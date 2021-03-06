// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.MethodBytecodeUtil;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.filters.LineNumbersMapping;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.DocumentUtil;
import com.intellij.util.Range;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;
import org.jetbrains.org.objectweb.asm.Label;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;

import java.util.*;
import java.util.stream.IntStream;

public class JavaSmartStepIntoHandler extends JvmSmartStepIntoHandler {
  private static final Logger LOG = Logger.getInstance(JavaSmartStepIntoHandler.class);

  @Override
  public boolean isAvailable(final SourcePosition position) {
    final PsiFile file = position.getFile();
    return file.getLanguage().isKindOf(JavaLanguage.INSTANCE);
  }

  @NotNull
  private Promise<List<SmartStepTarget>> findSmartStepTargetsAsync(SourcePosition position, DebuggerSession session, boolean smart) {
    AsyncPromise<List<SmartStepTarget>> res = new AsyncPromise<>();
    session.getProcess().getManagerThread().schedule(new DebuggerContextCommandImpl(session.getContextManager().getContext()) {
      @Override
      public void threadAction(@NotNull SuspendContextImpl suspendContext) {
        res.setResult(ReadAction.compute(() -> findStepTargets(position, suspendContext, getDebuggerContext(), smart)));
      }

      @Override
      public Priority getPriority() {
        return Priority.NORMAL;
      }
    });
    return res;
  }

  @NotNull
  @Override
  public Promise<List<SmartStepTarget>> findSmartStepTargetsAsync(SourcePosition position, DebuggerSession session) {
    return findSmartStepTargetsAsync(position, session, true);
  }

  @NotNull
  @Override
  public Promise<List<SmartStepTarget>> findStepIntoTargets(SourcePosition position, DebuggerSession session) {
    if (DebuggerSettings.getInstance().ALWAYS_SMART_STEP_INTO) {
      return findSmartStepTargetsAsync(position, session, false);
    }
    return Promises.rejectedPromise();
  }

  @NotNull
  @Override
  public List<SmartStepTarget> findSmartStepTargets(SourcePosition position) {
    throw new IllegalStateException("Should not be used");
  }

  protected List<SmartStepTarget> findStepTargets(final SourcePosition position,
                                                  @Nullable SuspendContextImpl suspendContext,
                                                  @NotNull DebuggerContextImpl debuggerContext,
                                                  boolean smart) {
    final int line = position.getLine();
    if (line < 0) {
      return Collections.emptyList(); // the document has been changed
    }

    final PsiFile file = position.getFile();
    final VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) {
      // the file is not physical
      return Collections.emptyList();
    }

    final Document doc = FileDocumentManager.getInstance().getDocument(vFile);
    if (doc == null) return Collections.emptyList();
    if (line >= doc.getLineCount()) {
      return Collections.emptyList(); // the document has been changed
    }
    TextRange curLineRange = DocumentUtil.getLineTextRange(doc, line);
    PsiElement element = position.getElementAt();
    PsiElement body = DebuggerUtilsEx.getBody(DebuggerUtilsEx.getContainingMethod(element));
    final TextRange lineRange = (body != null) ? curLineRange.intersection(body.getTextRange()) : curLineRange;

    if (lineRange == null || lineRange.isEmpty()) {
      return Collections.emptyList();
    }

    if (element != null && !(element instanceof PsiCompiledElement)) {
      do {
        final PsiElement parent = element.getParent();
        if (parent == null || (parent.getTextOffset() < lineRange.getStartOffset())) {
          break;
        }
        element = parent;
      }
      while (true);

      final List<SmartStepTarget> targets = new ArrayList<>();

      final Ref<TextRange> textRange = new Ref<>(lineRange);

      final PsiElementVisitor methodCollector = new JavaRecursiveElementVisitor() {
        final Deque<PsiMethod> myContextStack = new LinkedList<>();
        final Deque<String> myParamNameStack =  new LinkedList<>();
        private int myNextLambdaExpressionOrdinal = 0;
        private boolean myInsideLambda = false;

        @Nullable
        private String getCurrentParamName() {
          return myParamNameStack.peekFirst();
        }

        @Override
        public void visitAnonymousClass(PsiAnonymousClass aClass) {
          PsiExpressionList argumentList = aClass.getArgumentList();
          if (argumentList != null) {
            argumentList.accept(this);
          }
          for (PsiMethod psiMethod : aClass.getMethods()) {
            targets.add(0, new MethodSmartStepTarget(psiMethod, getCurrentParamName(), psiMethod.getBody(), true, null));
          }
        }

        @Override
        public void visitLambdaExpression(PsiLambdaExpression expression) {
          boolean inLambda = myInsideLambda;
          myInsideLambda = true;
          super.visitLambdaExpression(expression);
          myInsideLambda = inLambda;
          targets.add(0, new LambdaSmartStepTarget(expression,
                                                   getCurrentParamName(),
                                                   expression.getBody(),
                                                   myNextLambdaExpressionOrdinal++,
                                                   null,
                                                   !myInsideLambda));
        }

        @Override
        public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
          PsiElement element = expression.resolve();
          if (element instanceof PsiMethod) {
            PsiElement navMethod = element.getNavigationElement();
            if (navMethod instanceof PsiMethod) {
              targets.add(0, new MethodSmartStepTarget(((PsiMethod)navMethod), null, expression, true, null));
            }
          }
        }

        @Override
        public void visitField(PsiField field) {
          if (checkTextRange(field, false)) {
            super.visitField(field);
          }
        }

        @Override
        public void visitMethod(PsiMethod method) {
          if (checkTextRange(method, false)) {
            super.visitMethod(method);
          }
        }

        @Override
        public void visitStatement(PsiStatement statement) {
          if (checkTextRange(statement, true)) {
            super.visitStatement(statement);
          }
        }

        @Override
        public void visitIfStatement(PsiIfStatement statement) {
          visitConditional(statement.getCondition(), statement.getThenBranch(), statement.getElseBranch());
        }

        @Override
        public void visitConditionalExpression(PsiConditionalExpression expression) {
          visitConditional(expression.getCondition(), expression.getThenExpression(), expression.getElseExpression());
        }

        private void visitConditional(@Nullable PsiElement condition,
                                      @Nullable PsiElement thenBranch,
                                      @Nullable PsiElement elseBranch) {
          if (condition != null && checkTextRange(condition, true)) {
            condition.accept(this);
          }
          ThreeState conditionRes = evaluateCondition(condition);
          if (conditionRes != ThreeState.NO && thenBranch != null && checkTextRange(thenBranch, true)) {
            thenBranch.accept(this);
          }
          if (conditionRes != ThreeState.YES && elseBranch != null && checkTextRange(elseBranch, true)) {
            elseBranch.accept(this);
          }
        }

        private ThreeState evaluateCondition(@Nullable PsiElement condition) {
          if (condition != null && !DebuggerUtils.hasSideEffects(condition)) {
            try {
              ExpressionEvaluator evaluator = EvaluatorBuilderImpl.getInstance().build(condition, position);
              return ThreeState.fromBoolean(DebuggerUtilsEx.evaluateBoolean(evaluator, debuggerContext.createEvaluationContext()));
            }
            catch (EvaluateException e) {
              LOG.info(e);
            }
          }
          return ThreeState.UNSURE;
        }

        @Override
        public void visitExpression(PsiExpression expression) {
          checkTextRange(expression, true);
          super.visitExpression(expression);
        }

        boolean checkTextRange(@NotNull PsiElement expression, boolean expand) {
          TextRange range = expression.getTextRange();
          if (lineRange.intersects(range)) {
            if (expand) {
              textRange.set(textRange.get().union(range));
            }
            return true;
          }
          return false;
        }

        @Override
        public void visitExpressionList(PsiExpressionList expressionList) {
          visitArguments(expressionList, myContextStack.peekFirst());
        }

        void visitArguments(PsiExpressionList expressionList, PsiMethod psiMethod) {
          if (psiMethod != null) {
            final String methodName = psiMethod.getName();
            final PsiExpression[] expressions = expressionList.getExpressions();
            final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
            for (int idx = 0; idx < expressions.length; idx++) {
              final String paramName =
                (idx < parameters.length && !parameters[idx].isVarArgs()) ? parameters[idx].getName() : "arg" + (idx + 1);
              myParamNameStack.push(methodName + ": " + paramName + ".");
              final PsiExpression argExpression = expressions[idx];
              try {
                argExpression.accept(this);
              }
              finally {
                myParamNameStack.pop();
              }
            }
          }
          else {
            super.visitExpressionList(expressionList);
          }
        }

        @Override
        public void visitCallExpression(final PsiCallExpression expression) {
          int pos = -1;
          if (myContextStack.isEmpty()) { // always move the outmost item in the group to the top
            pos = targets.size();
          }
          final PsiMethod psiMethod = expression.resolveMethod();
          boolean isMethodCall = expression instanceof PsiMethodCallExpression;
          if (isMethodCall) {
            PsiExpression qualifier = ((PsiMethodCallExpression)expression).getMethodExpression().getQualifierExpression();
            if (qualifier != null) {
              qualifier.accept(this);
            }
            visitArguments(expression.getArgumentList(), psiMethod);
          }
          if (psiMethod != null) {
            myContextStack.push(psiMethod);
            MethodSmartStepTarget target = new MethodSmartStepTarget(
              psiMethod,
              null,
              isMethodCall ?
              ((PsiMethodCallExpression)expression).getMethodExpression().getReferenceNameElement()
                                                            : expression instanceof PsiNewExpression ? ((PsiNewExpression)expression)
                                                              .getClassOrAnonymousClassReference() : expression,
              myInsideLambda || (expression instanceof PsiNewExpression && ((PsiNewExpression)expression).getAnonymousClass() != null),
              null
            );
            target.setOrdinal((int)existingMethodCalls(targets, psiMethod).count());
            if (pos != -1) {
              targets.add(pos, target);
            }
            else {
              targets.add(target);
            }
          }
          try {
            if (isMethodCall) {
              checkTextRange(expression, true);
            }
            else {
              super.visitCallExpression(expression);
            }
          }
          finally {
            if (psiMethod != null) {
              myContextStack.pop();
            }
          }
        }

      };

      element.accept(methodCollector);
      for (PsiElement sibling = element.getNextSibling(); sibling != null; sibling = sibling.getNextSibling()) {
        if (!lineRange.intersects(sibling.getTextRange())) {
          break;
        }
        sibling.accept(methodCollector);
      }

      Range<Integer> sourceLines =
        new Range<>(doc.getLineNumber(textRange.get().getStartOffset()), doc.getLineNumber(textRange.get().getEndOffset()));
      targets.forEach(t -> t.setCallingExpressionLines(sourceLines));

      Set<Integer> lines = new HashSet<>();
      IntStream.rangeClosed(sourceLines.getFrom(), sourceLines.getTo()).forEach(lines::add);
      LineNumbersMapping mapping = vFile.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY);
      if (mapping != null) {
        lines = StreamEx.of(lines).map(l -> mapping.sourceToBytecode(l + 1) - 1).filter(l -> l >= 0).toSet();
      }


      if (!targets.isEmpty()) {
        StackFrameProxyImpl frameProxy = suspendContext != null ? suspendContext.getFrameProxy() : null;
        if (frameProxy != null) {
          VirtualMachineProxyImpl virtualMachine = frameProxy.getVirtualMachine();
          if (!virtualMachine.canGetConstantPool() || !virtualMachine.canGetBytecodes()) {
            return smart ? targets : Collections.emptyList();
          }
          // sanity check
          DebugProcessImpl debugProcess = suspendContext.getDebugProcess();
          try {
            List<MethodSmartStepTarget> methodTargets = immediateMethodCalls(targets).toList();
            visitLinesInstructions(frameProxy.location(), true, lines,
                                   (opcode, owner, name, desc, itf, ordinal) ->
                                     removeMatchingMethod(methodTargets, owner, name, desc, ordinal, debugProcess));
            if (!methodTargets.isEmpty()) {
              LOG.debug("Sanity check failed for: " + methodTargets);
              return Collections.emptyList();
            }
          }
          catch (Exception e) {
            LOG.info(e);
            return smart ? targets : Collections.emptyList();
          }

          try {
            ArrayList<SmartStepTarget> all = new ArrayList<>(targets);
            // remove already executed
            JumpsAndInsnVisitor visitor = new JumpsAndInsnVisitor(targets, debugProcess);
            visitLinesInstructions(frameProxy.location(), false, lines, visitor);

            if (!smart && !targets.isEmpty()) {
              ArrayList<SmartStepTarget> copy = new ArrayList<>(targets);
              // remove after jumps
              visitor.setJumpsToIgnore(visitor.getJumpCounter() + 1);
              visitLinesInstructions(frameProxy.location(), true, lines, visitor);

              // check if anything real left, fallback to the previous state
              if (!targets.isEmpty() && immediateMethodCalls(targets).findAny().isEmpty()) {
                targets.clear();
                targets.addAll(copy);
              }
            }

            // fix ordinals
            ArrayList<SmartStepTarget> removed = new ArrayList<>(all);
            removed.removeAll(targets);
            for (SmartStepTarget m : removed) {
              MethodSmartStepTarget target = (MethodSmartStepTarget)m;
              existingMethodCalls(all, target.getMethod())
                .forEach(t -> {
                  int ordinal = t.getOrdinal();
                  if (ordinal > target.getOrdinal()) {
                    t.setOrdinal(ordinal - 1);
                  }
                });
            }
          }
          catch (Exception e) {
            LOG.info(e);
            return Collections.emptyList();
          }
        }

        return targets;
      }
    }
    return Collections.emptyList();
  }

  private static void removeMatchingMethod(List<MethodSmartStepTarget> targets,
                                           String owner,
                                           String name,
                                           String desc,
                                           int ordinal,
                                           DebugProcessImpl process) {
    Iterator<MethodSmartStepTarget> iterator = targets.iterator();
    while (iterator.hasNext()) {
      MethodSmartStepTarget target = iterator.next();
      if (DebuggerUtilsEx.methodMatches(target.getMethod(), owner.replace("/", "."), name, desc, process) &&
          target.getOrdinal() == ordinal) {
        iterator.remove();
        break;
      }
    }
  }

  private interface MethodInsnVisitor {
    void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf, int ordinal);
    default void visitJumpInsn(int opcode, Label label) {}
    default void visitCode() {}
  }

  private static void visitLinesInstructions(Location location, boolean full, Set<Integer> lines, MethodInsnVisitor visitor) {
    Object2IntMap<String> myCounter = new Object2IntOpenHashMap<>();

    MethodBytecodeUtil.visit(location.method(), full ? Long.MAX_VALUE : location.codeIndex(), new MethodVisitor(Opcodes.API_VERSION) {
      boolean myLineMatch = false;

      @Override
      public void visitJumpInsn(int opcode, Label label) {
        if (myLineMatch) {
          visitor.visitJumpInsn(opcode, label);
        }
      }

      @Override
      public void visitCode() {
        visitor.visitCode();
      }

      @Override
      public void visitLineNumber(int line, Label start) {
        myLineMatch = lines.contains(line - 1);
      }

      @Override
      public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        if (myLineMatch) {
          String key = owner + "." + name + desc;
          int currentCount = myCounter.mergeInt(key, 1, Math::addExact) - 1;
          if (name.startsWith("access$")) { // bridge method
            ReferenceType cls =
              ContainerUtil.getFirstItem(location.virtualMachine().classesByName(Type.getObjectType(owner).getClassName()));
            if (cls != null) {
              Method method = DebuggerUtils.findMethod(cls, name, desc);
              if (method != null) {
                MethodBytecodeUtil.visit(method, new MethodVisitor(Opcodes.API_VERSION) {
                  @Override
                  public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                    if ("java/lang/AbstractMethodError".equals(owner)) {
                      return;
                    }
                    visitor.visitMethodInsn(opcode, owner, name, desc, itf, currentCount);
                  }
                }, false);
              }
            }
          }
          else {
            visitor.visitMethodInsn(opcode, owner, name, desc, itf, currentCount);
          }
        }
      }
    }, true);
  }

  private static StreamEx<MethodSmartStepTarget> immediateMethodCalls(List<SmartStepTarget> targets) {
    return StreamEx.of(targets)
      .select(MethodSmartStepTarget.class)
      .filter(target -> !target.needsBreakpointRequest());
  }

  private static StreamEx<MethodSmartStepTarget> existingMethodCalls(List<SmartStepTarget> targets, PsiMethod psiMethod) {
    return immediateMethodCalls(targets).filter(t -> t.getMethod().equals(psiMethod));
  }

  private static class JumpsAndInsnVisitor implements MethodInsnVisitor {
    private final List<SmartStepTarget> myTargets;
    private final DebugProcessImpl myDebugProcess;
    private int myJumpCounter;
    private int myJumpsToIgnore;

    JumpsAndInsnVisitor(List<SmartStepTarget> targets, DebugProcessImpl debugProcess) {
      myTargets = targets;
      myDebugProcess = debugProcess;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf, int ordinal) {
      if (myJumpCounter >= myJumpsToIgnore) {
        Iterator<SmartStepTarget> iterator = myTargets.iterator();
        while (iterator.hasNext()) {
          SmartStepTarget e = iterator.next();
          if (e instanceof MethodSmartStepTarget &&
              DebuggerUtilsEx.methodMatches(((MethodSmartStepTarget)e).getMethod(), owner.replace("/", "."), name, desc, myDebugProcess) &&
              ((MethodSmartStepTarget)e).getOrdinal() == ordinal) {
            iterator.remove();
            break;
          }
        }
      }
    }

    @Override
    public void visitCode() {
      myJumpCounter = 0;
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
      myJumpCounter++;
    }

    int getJumpCounter() {
      return myJumpCounter;
    }

    void setJumpsToIgnore(int jumpsToIgnore) {
      myJumpsToIgnore = jumpsToIgnore;
    }
  }
}

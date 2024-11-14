// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.jdi.MethodBytecodeUtil;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.statistics.DebuggerStatistics;
import com.intellij.debugger.statistics.Engine;
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
import com.intellij.psi.util.PsiTreeUtil;
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
    var res = new AsyncPromise<List<SmartStepTarget>>();
    DebuggerContextImpl context = session.getContextManager().getContext();
    Objects.requireNonNull(context.getManagerThread()).schedule(new DebuggerContextCommandImpl(context) {
      @Override
      public void threadAction(@NotNull SuspendContextImpl suspendContext) {
        Promises.compute(res, () ->
          ReadAction.nonBlocking(() -> findStepTargets(position, suspendContext, getDebuggerContext(), smart)).executeSynchronously());
      }

      @Override
      protected void commandCancelled() {
        res.setError("Cancelled");
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
    return reorderWithSteppingFilters(findStepTargetsInt(position, suspendContext, debuggerContext, smart));
  }

  private List<SmartStepTarget> findStepTargetsInt(final SourcePosition position,
                                                   @Nullable SuspendContextImpl suspendContext,
                                                   @NotNull DebuggerContextImpl debuggerContext,
                                                   boolean smart) {
    final int line = position.getLine();
    if (line < 0) {
      DebuggerStatistics.logSmartStepIntoTargetsDetection(debuggerContext.getProject(), Engine.JAVA, SmartStepIntoDetectionStatus.INVALID_POSITION);
      return Collections.emptyList(); // the document has been changed
    }

    final PsiFile file = position.getFile();
    final VirtualFile vFile = file.getVirtualFile();
    if (vFile == null) {
      DebuggerStatistics.logSmartStepIntoTargetsDetection(debuggerContext.getProject(), Engine.JAVA, SmartStepIntoDetectionStatus.INVALID_POSITION);
      // the file is not physical
      return Collections.emptyList();
    }

    final Document doc = FileDocumentManager.getInstance().getDocument(vFile);
    if (doc == null || line >= doc.getLineCount()) {
      DebuggerStatistics.logSmartStepIntoTargetsDetection(debuggerContext.getProject(), Engine.JAVA, SmartStepIntoDetectionStatus.INVALID_POSITION);
      return Collections.emptyList(); // the document has been changed
    }
    TextRange curLineRange = DocumentUtil.getLineTextRange(doc, line);
    PsiElement element = position.getElementAt();
    PsiElement body = DebuggerUtilsEx.getBody(DebuggerUtilsEx.getContainingMethod(element));
    final TextRange lineRange = (body != null) ? curLineRange.intersection(body.getTextRange()) : curLineRange;

    if (lineRange == null || lineRange.isEmpty() || element == null || element instanceof PsiCompiledElement) {
      DebuggerStatistics.logSmartStepIntoTargetsDetection(debuggerContext.getProject(), Engine.JAVA, SmartStepIntoDetectionStatus.INVALID_POSITION);
      return Collections.emptyList();
    }

    final PsiElement initial = element;
    element = getTopmostParentAfterOffset(element, lineRange.getStartOffset());

    final PsiElement statementParent = PsiTreeUtil.getParentOfType(initial, PsiStatement.class, false);
    if (statementParent != null
        && (body == null || body.getTextRange().contains(statementParent.getTextRange()))
        // take only wider statements
        && statementParent.getTextRange().contains(element.getTextRange())) {
      element = statementParent;
    }

    final List<SmartStepTarget> targets = new ArrayList<>();

    final Ref<TextRange> textRange = new Ref<>(lineRange);

    final PsiElementVisitor methodCollector = new JavaRecursiveElementVisitor() {
      final Deque<PsiMethod> myContextStack = new LinkedList<>();
      final Deque<String> myParamNameStack = new LinkedList<>();
      private int myNextLambdaExpressionOrdinal = 0;
      private boolean myInsideLambda = false;

      @Nullable
      private String getCurrentParamName() {
        return myParamNameStack.peekFirst();
      }

      @Override
      public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {
        if (!matchLine(aClass)) return;
        PsiExpressionList argumentList = aClass.getArgumentList();
        if (argumentList != null) {
          argumentList.accept(this);
        }
        for (PsiMethod psiMethod : aClass.getMethods()) {
          targets.add(0, new MethodSmartStepTarget(psiMethod, getCurrentParamName(), psiMethod.getBody(), true, null));
        }
      }

      @Override
      public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
        boolean inLambda = myInsideLambda;
        myInsideLambda = true;
        super.visitLambdaExpression(expression);
        myInsideLambda = inLambda;
        if (!matchLine(expression)) return;
        targets.add(0, new LambdaSmartStepTarget(expression,
                                                 getCurrentParamName(),
                                                 expression.getBody(),
                                                 myNextLambdaExpressionOrdinal++,
                                                 null,
                                                 !myInsideLambda));
      }

      @Override
      public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
        PsiElement element = expression.resolve();
        if (matchLine(expression) && element instanceof PsiMethod) {
          PsiElement navMethod = element.getNavigationElement();
          if (navMethod instanceof PsiMethod) {
            targets.add(0, new MethodSmartStepTarget(((PsiMethod)navMethod), null, expression, true, null));
          }
        }
      }

      @Override
      public void visitField(@NotNull PsiField field) {
        if (checkTextRange(field, false)) {
          super.visitField(field);
        }
      }

      @Override
      public void visitMethod(@NotNull PsiMethod method) {
        if (checkTextRange(method, false)) {
          super.visitMethod(method);
        }
      }

      @Override
      public void visitStatement(@NotNull PsiStatement statement) {
        if (checkTextRange(statement, true)) {
          super.visitStatement(statement);
        }
      }

      @Override
      public void visitIfStatement(@NotNull PsiIfStatement statement) {
        visitConditional(statement.getCondition(), statement.getThenBranch(), statement.getElseBranch());
      }

      @Override
      public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
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
      public void visitExpression(@NotNull PsiExpression expression) {
        checkTextRange(expression, true);
        super.visitExpression(expression);
      }

      boolean checkTextRange(@NotNull PsiElement expression, boolean expand) {
        TextRange range = expression.getTextRange();
        if (lineRange.intersects(range)) {
          if (expand && matchLine(expression)) {
            textRange.set(textRange.get().union(range));
          }
          return true;
        }
        return false;
      }

      boolean matchLine(@NotNull PsiElement elem) {
        return lineRange.getStartOffset() <= elem.getTextRange().getStartOffset();
      }

      @Override
      public void visitExpressionList(@NotNull PsiExpressionList expressionList) {
        visitArguments(expressionList, myContextStack.peekFirst());
      }

      void visitArguments(PsiExpressionList expressionList, PsiMethod psiMethod) {
        if (psiMethod != null) {
          final String methodName = psiMethod.getName();
          final PsiExpression[] expressions = expressionList.getExpressions();
          final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
          for (int idx = 0; idx < expressions.length; idx++) {
            final PsiExpression argExpression = expressions[idx];
            if (!matchLine(argExpression)) continue;
            final String paramName =
              (idx < parameters.length && !parameters[idx].isVarArgs()) ? parameters[idx].getName() : "arg" + (idx + 1);
            myParamNameStack.push(methodName + ": " + paramName + ".");
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
      public void visitCallExpression(final @NotNull PsiCallExpression expression) {
        int pos = -1;
        if (myContextStack.isEmpty()) { // always move the outmost item in the group to the top
          pos = targets.size();
        }
        final PsiMethod psiMethod = expression.resolveMethod();
        if (expression instanceof PsiMethodCallExpression callExpr) {
          PsiExpression qualifier = callExpr.getMethodExpression().getQualifierExpression();
          if (qualifier != null) {
            qualifier.accept(this);
          }
          visitArguments(expression.getArgumentList(), psiMethod);
        }
        if (psiMethod != null) {
          myContextStack.push(psiMethod);
        }
        try {
          PsiElement callExpression = expression instanceof PsiMethodCallExpression callExpr
                                      ? callExpr.getMethodExpression().getReferenceNameElement()
                                      : expression instanceof PsiNewExpression newExpr
                                        ? newExpr.getClassOrAnonymousClassReference()
                                        : expression;
          if (psiMethod != null && (callExpression == null || matchLine(callExpression))) {
            MethodSmartStepTarget target = new MethodSmartStepTarget(
              psiMethod,
              null,
              callExpression,
              myInsideLambda || (expression instanceof PsiNewExpression newExpr && newExpr.getAnonymousClass() != null),
              null
            );
            target.setOrdinal(Math.toIntExact(existingMethodCalls(targets, psiMethod).count()));
            if (pos != -1) {
              targets.add(pos, target);
            }
            else {
              targets.add(target);
            }
          }
          if (expression instanceof PsiMethodCallExpression) {
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
    if (targets.isEmpty()) {
      DebuggerStatistics.logSmartStepIntoTargetsDetection(debuggerContext.getProject(), Engine.JAVA, SmartStepIntoDetectionStatus.NO_TARGETS);
      return Collections.emptyList();
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

    StackFrameProxyImpl frameProxy = suspendContext != null ? suspendContext.getFrameProxy() : null;
    if (frameProxy == null) {
      DebuggerStatistics.logSmartStepIntoTargetsDetection(debuggerContext.getProject(), Engine.JAVA, SmartStepIntoDetectionStatus.SUCCESS);
      return targets;
    }

    VirtualMachineProxyImpl virtualMachine = frameProxy.getVirtualMachine();
    if (!virtualMachine.canGetConstantPool() || !virtualMachine.canGetBytecodes()) {
      if (smart) {
        DebuggerStatistics.logSmartStepIntoTargetsDetection(debuggerContext.getProject(), Engine.JAVA, SmartStepIntoDetectionStatus.SUCCESS);
        return targets;
      } else {
        DebuggerStatistics.logSmartStepIntoTargetsDetection(debuggerContext.getProject(), Engine.JAVA, SmartStepIntoDetectionStatus.BYTECODE_NOT_AVAILABLE);
        return Collections.emptyList();
      }
    }

    try {
      DebugProcessImpl debugProcess = suspendContext.getDebugProcess();
      Location location = frameProxy.location();
      long currentBytecodeOffset = location.codeIndex();

      ArrayList<SmartStepTarget> all = new ArrayList<>(targets);

      final List<SmartStepTarget> targetsWithCollisions = new ArrayList<>();
      // collect bytecode offsets of the calls
      final Set<Integer> finalLines = lines;
      class BytecodeVisitor extends MethodVisitor implements MethodBytecodeUtil.InstructionOffsetReader {
        private boolean myLineMatch = false;
        private int myOffset = -1;
        private int endOfBasicBlock = Integer.MAX_VALUE;
        private final Object2IntMap<String> myCounter = new Object2IntOpenHashMap<>();

        final Set<SmartStepTarget> foundTargets = new HashSet<>();
        final Set<SmartStepTarget> alreadyExecutedTargets = new HashSet<>();
        final Set<SmartStepTarget> anotherBasicBlockTargets = new HashSet<>();

        BytecodeVisitor() {
          super(Opcodes.API_VERSION);
        }

        @Override
        public void readBytecodeInstructionOffset(int bytecodeOffset) {
          myOffset = bytecodeOffset;
        }

        @Override
        public void visitLineNumber(int line, Label start) {
          myLineMatch = finalLines.contains(line - 1);
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
          if (myLineMatch) {
            assert myOffset != -1;
            int oldValue = endOfBasicBlock;
            if (currentBytecodeOffset <= myOffset && myOffset < oldValue) {
              assert oldValue == Integer.MAX_VALUE; // it should be set only once, because bytecode is iterated linearly
              endOfBasicBlock = myOffset;
            }
          }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
          if (myLineMatch) {
            assert myOffset != -1;
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
                      visitMethodInstInt(owner, name, desc, currentCount, myOffset);
                    }
                  }, false);
                }
              }
            }
            else {
              visitMethodInstInt(owner, name, desc, currentCount, myOffset);
            }
          }
        }

        private void visitMethodInstInt(String owner, String name, String desc, int ordinal, int bcOffs) {
          for (SmartStepTarget t : targets) {
            if (t instanceof MethodSmartStepTarget mt && mt.getOrdinal() == ordinal) {
              PsiMethod method = mt.getMethod();
              if (DebuggerUtilsEx.methodMatches(method, owner.replace("/", "."), name, desc, debugProcess)) {
                if (foundTargets.contains(mt)) {
                  targetsWithCollisions.add(mt);
                }
                else {
                  foundTargets.add(mt);
                  if (bcOffs < currentBytecodeOffset) {
                    alreadyExecutedTargets.add(mt);
                  }
                  if (bcOffs > endOfBasicBlock) {
                    anotherBasicBlockTargets.add(mt);
                  }
                }
              }
            }
          }
        }
      }
      BytecodeVisitor bytecodeVisitor = new BytecodeVisitor();
      MethodBytecodeUtil.visit(location.method(), bytecodeVisitor, true);

      // sanity check
      List<SmartStepTarget> notFoundTargets = new ArrayList<>();
      for (SmartStepTarget t : targets) {
        if (isImmediateMethodCall(t) && !bytecodeVisitor.foundTargets.contains(t)) {
          notFoundTargets.add(t);
        }
      }

      StringBuilder errorMessage = new StringBuilder();
      if (!targetsWithCollisions.isEmpty()) {
        errorMessage.append("Target occurred multiple times in bytecode: ")
          .append(JvmSmartStepIntoErrorReporter.joinTargetInfo(targetsWithCollisions));
      }
      if (!notFoundTargets.isEmpty()) {
        if (!errorMessage.isEmpty()) errorMessage.append('\n');
        errorMessage.append("Target not found in bytecode: ")
          .append(JvmSmartStepIntoErrorReporter.joinTargetInfo(notFoundTargets));
      }

      if (!errorMessage.isEmpty()) {
        JvmSmartStepIntoErrorReporter.report(element, debuggerContext.getDebuggerSession(), position, errorMessage.toString());
        DebuggerStatistics.logSmartStepIntoTargetsDetection(element.getProject(), Engine.JAVA, SmartStepIntoDetectionStatus.TARGETS_MISMATCH);
        return Collections.emptyList();
      }

      // remove already executed
      targets.removeAll(bytecodeVisitor.alreadyExecutedTargets);

      if (!smart && !targets.isEmpty() && !bytecodeVisitor.anotherBasicBlockTargets.isEmpty()) {
        // remove after jumps
        int oldSize = targets.size();
        targets.removeAll(bytecodeVisitor.anotherBasicBlockTargets);
        assert oldSize == targets.size() + bytecodeVisitor.anotherBasicBlockTargets.size(); // this allows us easy fallback below

        // check if anything real left, otherwise fallback to the previous state
        if (!targets.isEmpty() && immediateMethodCalls(targets).findAny().isEmpty()) {
          targets.addAll(bytecodeVisitor.anotherBasicBlockTargets);
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
      DebuggerStatistics.logSmartStepIntoTargetsDetection(element.getProject(), Engine.JAVA, SmartStepIntoDetectionStatus.SUCCESS);
      return targets;
    }
    catch (Exception e) {
      DebuggerUtilsImpl.logError(e);
      DebuggerStatistics.logSmartStepIntoTargetsDetection(element.getProject(), Engine.JAVA, SmartStepIntoDetectionStatus.INTERNAL_ERROR);
      return Collections.emptyList();
    }
  }

  /**
   * Find the topmost parent element whose range starts after the target offset.
   */
  private static PsiElement getTopmostParentAfterOffset(PsiElement element, int offset) {
    if (element == null) return null;
    while (true) {
      final PsiElement parent = element.getParent();
      if (parent == null || (parent.getTextRange().getStartOffset() < offset)) {
        return element;
      }
      element = parent;
    }
  }

  private static boolean isImmediateMethodCall(SmartStepTarget target) {
    return !target.needsBreakpointRequest();
  }

  private static StreamEx<MethodSmartStepTarget> immediateMethodCalls(List<SmartStepTarget> targets) {
    return StreamEx.of(targets)
      .select(MethodSmartStepTarget.class)
      .filter(JavaSmartStepIntoHandler::isImmediateMethodCall);
  }

  private static StreamEx<MethodSmartStepTarget> existingMethodCalls(List<SmartStepTarget> targets, PsiMethod psiMethod) {
    return immediateMethodCalls(targets).filter(t -> t.getMethod().equals(psiMethod));
  }
}

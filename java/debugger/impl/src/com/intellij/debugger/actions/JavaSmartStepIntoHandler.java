// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.ContextUtil;
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
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.Range;
import com.intellij.util.ThreeState;
import com.sun.jdi.Location;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

public class JavaSmartStepIntoHandler extends JvmSmartStepIntoHandler {
  private static final Logger LOG = Logger.getInstance(JavaSmartStepIntoHandler.class);

  @Override
  public boolean isAvailable(final SourcePosition position) {
    final PsiFile file = position.getFile();
    return file.getLanguage().isKindOf(JavaLanguage.INSTANCE);
  }

  private @NotNull Promise<List<SmartStepTarget>> findSmartStepTargetsAsync(SourcePosition position, DebuggerSession session, boolean smart) {
    var res = new AsyncPromise<List<SmartStepTarget>>();
    DebuggerContextImpl context = session.getContextManager().getContext();
    Objects.requireNonNull(context.getManagerThread()).schedule(new DebuggerContextCommandImpl(context) {
      @Override
      public void threadAction(@NotNull SuspendContextImpl suspendContext) {
        if (!Objects.equals(ContextUtil.getSourcePosition(suspendContext), position)) {
          // The source position has changed - just cancel the current command
          res.cancel();
          return;
        }

        Promises.compute(res, () ->
          ReadAction.nonBlocking(() -> findStepTargets(position, suspendContext, getDebuggerContext(), smart)).executeSynchronously());
      }

      @Override
      protected void commandCancelled() {
        res.cancel();
      }

      @Override
      public Priority getPriority() {
        return Priority.NORMAL;
      }
    });
    return res;
  }

  @Override
  public @NotNull Promise<List<SmartStepTarget>> findSmartStepTargetsAsync(SourcePosition position, DebuggerSession session) {
    return findSmartStepTargetsAsync(position, session, true);
  }

  @Override
  public @NotNull Promise<List<SmartStepTarget>> findStepIntoTargets(SourcePosition position, DebuggerSession session) {
    if (DebuggerSettings.getInstance().ALWAYS_SMART_STEP_INTO) {
      return findSmartStepTargetsAsync(position, session, false);
    }
    return Promises.rejectedPromise();
  }

  @Override
  public @NotNull List<SmartStepTarget> findSmartStepTargets(SourcePosition position) {
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

    final PsiElement statementParent = PsiTreeUtil.getNonStrictParentOfType(initial, PsiStatement.class, PsiField.class);
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

      private @Nullable String getCurrentParamName() {
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
          if (isSteppableMethod(psiMethod)) {
            targets.addFirst(new MethodSmartStepTarget(psiMethod, getCurrentParamName(), psiMethod.getBody(), true, null));
          }
        }
      }

      @Override
      public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
        super.visitLambdaExpression(expression);
        if (!matchLine(expression)) return;
        targets.add(0, new LambdaSmartStepTarget(expression,
                                                 getCurrentParamName(),
                                                 expression.getBody(),
                                                 myNextLambdaExpressionOrdinal++,
                                                 null,
                                                 !isInsideLambda(expression)));
      }

      @Override
      public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
        PsiElement element = expression.resolve();
        if (matchLine(expression) && element instanceof PsiMethod) {
          PsiElement navMethod = element.getNavigationElement();
          if (navMethod instanceof PsiMethod method && isSteppableMethod(method)) {
            targets.addFirst(new MethodSmartStepTarget(method, null, expression, true, null));
          }
        }
      }

      @Override
      public void visitField(@NotNull PsiField field) {
        if (checkTextRange(field, true)) {
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
          if (expand) {
            // only expand to the bottom of the file
            TextRange current = textRange.get();
            int delta = range.getEndOffset() - current.getEndOffset();
            if (delta > 0) {
              textRange.set(current.grown(delta));
            }
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
          if (psiMethod != null && isSteppableMethod(psiMethod) && (callExpression == null || matchLine(callExpression))) {
            MethodSmartStepTarget target = new MethodSmartStepTarget(
              psiMethod,
              null,
              callExpression,
              isInsideLambda(expression) ||
              (expression instanceof PsiNewExpression newExpr && newExpr.getAnonymousClass() != null),
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

      ArrayList<SmartStepTarget> all = new ArrayList<>(targets);

      JavaSmartStepIntoBytecodeMatcher.Result bytecodeMatch =
        new JavaSmartStepIntoBytecodeMatcher(location, debugProcess, lines, targets, !smart).match();
      List<SmartStepTarget> targetsWithCollisions = bytecodeMatch.getCollidingTargets();
      List<SmartStepTarget> notFoundTargets = bytecodeMatch.getNotFoundTargets();

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
      targets.removeAll(bytecodeMatch.getAlreadyExecutedTargets());

      Set<SmartStepTarget> conditionallyExecutedTargets = bytecodeMatch.getConditionallyExecutedTargets();
      if (!smart && !targets.isEmpty() && !conditionallyExecutedTargets.isEmpty()) {
        int oldSize = targets.size();
        targets.removeAll(conditionallyExecutedTargets);
        assert oldSize == targets.size() + conditionallyExecutedTargets.size(); // this allows us easy fallback below

        // check if anything real left, otherwise fallback to the previous state
        if (!targets.isEmpty() && immediateMethodCalls(targets).findAny().isEmpty()) {
          targets.addAll(conditionallyExecutedTargets);
        }
      }

      // fix ordinals
      ArrayList<SmartStepTarget> removed = new ArrayList<>(all);
      removed.removeAll(targets);
      for (SmartStepTarget m : removed) {
        if (!(m instanceof MethodSmartStepTarget target)) continue;
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

  private static boolean isInsideLambda(@NotNull PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class) != null;
  }

  private static boolean isSteppableMethod(@NotNull PsiMethod method) {
    return !method.hasModifierProperty(PsiModifier.NATIVE) || PsiUtil.canBeOverridden(method);
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
    return immediateMethodCalls(targets)
      .filter(t -> psiMethod.getManager().areElementsEquivalent(psiMethod, t.getMethod()));
  }
}

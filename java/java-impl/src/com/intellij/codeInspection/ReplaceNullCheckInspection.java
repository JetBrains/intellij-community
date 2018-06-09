// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.util.ObjectUtils.tryCast;

public class ReplaceNullCheckInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final EquivalenceChecker ourEquivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
  private static final int MINIMAL_WARN_DELTA_SIZE = 30;

  private static final CallMatcher STREAM_EMPTY = CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "empty")
    .parameterCount(0);
  private static final CallMatcher STREAM_OF = CallMatcher.anyOf(
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "ofNullable").parameterCount(1),
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "of").parameterTypes("T")
  );

  @SuppressWarnings("PublicField")
  public boolean noWarningReplacementBigger = true;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel
      .addCheckbox(InspectionsBundle.message("inspection.require.non.null.no.warning.replacement.bigger"), "noWarningReplacementBigger");
    return panel;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    PsiFile file = holder.getFile();
    if (!PsiUtil.isLanguageLevel9OrHigher(file)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitIfStatement(PsiIfStatement ifStatement) {
        NotNullContext context = NotNullContext.from(ifStatement);
        if(context == null) return;
        String method = getMethodWithClass(context.myExpressionToReplace, context.myIsStream);

        PsiStatement nextToDelete = context.myNextToDelete;
        int maybeImplicitElseLength = nextToDelete != null ? nextToDelete.getTextLength() : 0;
        boolean isInfoLevel = noWarningReplacementBigger && ifStatement.getTextLength() + maybeImplicitElseLength - context.getLenAfterReplace() < MINIMAL_WARN_DELTA_SIZE;
        if (!isOnTheFly && isInfoLevel) return;
        ProblemHighlightType highlight = getHighlight(context, isInfoLevel);
        holder.registerProblem(ifStatement.getFirstChild(), InspectionsBundle.message("inspection.require.non.null.message", method), highlight,
                               new ReplaceWithRequireNonNullFix(method, false));
      }

      @NotNull
      private ProblemHighlightType getHighlight(NotNullContext context, boolean isInfoLevel) {
        if(context.myIsStream) {
          return ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
        }
        if (isInfoLevel) {
          return ProblemHighlightType.INFORMATION;
        }
        return ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
      }

      @Override
      public void visitConditionalExpression(PsiConditionalExpression ternary) {
        TernaryNotNullContext context = TernaryNotNullContext.from(ternary);
        if(context == null) return;
        String method = getMethodWithClass(context.myNullExpr, false);
        String name = context.myReferenceExpression.getText();
        boolean replacementShorter =
          name != null
          && context.myNullExpr.getTextLength() + method.length() + name.length() < context.myTernary.getTextLength() + MINIMAL_WARN_DELTA_SIZE;
        boolean isInfoLevel = noWarningReplacementBigger && replacementShorter;
        ProblemHighlightType highlightType = isInfoLevel ? ProblemHighlightType.INFORMATION : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
        holder.registerProblem(ternary, InspectionsBundle.message("inspection.require.non.null.message", method),
                               highlightType, new ReplaceWithRequireNonNullFix(method, true));
      }
    };
  }

  private static class ReplaceWithRequireNonNullFix implements LocalQuickFix {
    private final @NotNull String myMethod;
    private final boolean myIsTernary;

    private ReplaceWithRequireNonNullFix(@NotNull String method, boolean ternary) {myMethod = method;
      myIsTernary = ternary;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("inspection.require.non.null.message", myMethod);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.require.non.null");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = myIsTernary ? descriptor.getStartElement() : descriptor.getStartElement().getParent();
      final PsiElement result;
      if(element instanceof PsiIfStatement) {
        NotNullContext context = NotNullContext.from((PsiIfStatement)element);
        if (context == null) return;
        CommentTracker tracker = new CommentTracker();
        PsiExpression expression = context.myExpressionToReplace;
        if(!context.myIsStream) {
          PsiExpression requireCall = createRequireExpression(tracker, expression, project, context.myReference, context.myDiff);
          context.myDiff.replace(requireCall);
        } else {
          PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
          String streamOfNullableText = CommonClassNames.JAVA_UTIL_STREAM_STREAM + ".ofNullable(" + context.myReference.getText() + ")";
          PsiExpression streamOfNullable = factory.createExpressionFromText(streamOfNullableText, expression);
          expression.replace(streamOfNullable);
        }
        result = tracker.replaceAndRestoreComments(context.myIfStatement, context.myNullBranchStmt);
        if (context.myNextToDelete != null) {
          context.myNextToDelete.delete();
        }
      } else if(element instanceof PsiConditionalExpression) {
        TernaryNotNullContext context = TernaryNotNullContext.from((PsiConditionalExpression)element);
        if(context == null) return;
        CommentTracker tracker = new CommentTracker();
        PsiExpression requireCall =
          createRequireExpression(tracker, context.myNullExpr, project, context.myReferenceExpression, context.myNullExpr);
        result = tracker.replace(context.myTernary, requireCall);
      } else return;
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
      CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(result));
    }
  }

  @NotNull
  private static PsiExpression createRequireExpression(@NotNull CommentTracker tracker,
                                                       @NotNull PsiExpression expression,
                                                       @NotNull Project project,
                                                       @NotNull PsiReferenceExpression nullableReference,
                                                       @NotNull PsiElement context) {
    boolean isSimple = ExpressionUtils.isSafelyRecomputableExpression(expression);
    String expr = tracker.text(expression);
    if (!isSimple) {
      expr = "()->" + expr;
    }
    String varName = nullableReference.getText();
    String requireCallText = CommonClassNames.JAVA_UTIL_OBJECTS + "." + getMethod(expression) + "(" + varName + "," + expr + ")";
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    return factory.createExpressionFromText(requireCallText, context);
  }

  private static class NotNullContext {
    private final @NotNull PsiExpression myExpressionToReplace;
    private final @NotNull PsiExpression myDiff;
    private final @NotNull PsiStatement myNullBranchStmt;
    private final @NotNull PsiReferenceExpression myReference;
    private final @NotNull PsiIfStatement myIfStatement;
    private final @Nullable PsiStatement myNextToDelete;
    private final boolean myIsStream;

    private NotNullContext(@NotNull PsiExpression expressionToReplace,
                           @NotNull PsiExpression diff,
                           @NotNull PsiStatement nullBranchStmt,
                           @NotNull PsiReferenceExpression reference,
                           @NotNull PsiIfStatement statement,
                           @Nullable PsiStatement nextToDelete,
                           boolean isStream) {
      myExpressionToReplace = expressionToReplace;
      myDiff = diff;
      myNullBranchStmt = nullBranchStmt;
      myReference = reference;
      myIfStatement = statement;
      myNextToDelete = nextToDelete;
      myIsStream = isStream;
    }

    int getLenAfterReplace() {
      int lengthAfterReplace = myExpressionToReplace.getTextLength() + getMethodWithClass(myExpressionToReplace, myIsStream).length();
      if(!myIsStream) {
        lengthAfterReplace += myNullBranchStmt.getTextLength() + 6;
      }
      return lengthAfterReplace;
    }

    @Nullable
    static NotNullContext from(@NotNull PsiIfStatement ifStatement) {
      PsiExpression condition = ifStatement.getCondition();
      if(condition == null) return null;
      PsiBinaryExpression binOp = tryCast(condition, PsiBinaryExpression.class);
      if(binOp == null) return null;
      PsiExpression value = ExpressionUtils.getValueComparedWithNull(binOp);
      PsiReferenceExpression referenceExpression = tryCast(value, PsiReferenceExpression.class);
      if(referenceExpression == null) return null;
      PsiVariable variable = tryCast(referenceExpression.resolve(), PsiVariable.class);
      if(variable == null) return null;
      if(ClassUtils.isPrimitive(variable.getType())) return null;

      boolean inverted = binOp.getOperationTokenType() == JavaTokenType.NE;
      PsiStatement elseBranch = ControlFlowUtils.stripBraces(ifStatement.getElseBranch());
      PsiStatement thenBranch = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
      if(elseBranch != null) {
        PsiStatement nullBranch = inverted? thenBranch : elseBranch;
        PsiStatement nonNullBranch = inverted? elseBranch : thenBranch;
        return extractContext(ifStatement, variable, referenceExpression, nullBranch, nonNullBranch, null);
      } else {
        PsiReturnStatement nextReturn = tryCast(PsiTreeUtil.skipWhitespacesAndCommentsForward(ifStatement), PsiReturnStatement.class);
        if (nextReturn == null) return null;
        if (thenBranch instanceof PsiReturnStatement) {
          PsiStatement nullBranch = inverted? thenBranch : nextReturn;
          PsiStatement nonNullBranch = inverted? nextReturn : thenBranch;
          return extractContext(ifStatement, variable, referenceExpression, nullBranch, nonNullBranch, nextReturn);
        }
      }
      return null;
    }

    @Contract("_, _, _, null, _, _ -> null")
    private static NotNullContext extractContext(@NotNull PsiIfStatement ifStatement,
                                                 @NotNull PsiVariable variable,
                                                 @NotNull PsiReferenceExpression reference,
                                                 @Nullable PsiStatement nullBranch,
                                                 @Nullable PsiStatement nonNullBranch,
                                                 @Nullable PsiReturnStatement toDelete) {
      if(nullBranch == null) return null;
      EquivalenceChecker.Match match = ourEquivalence.statementsMatch(nullBranch, nonNullBranch);
      PsiExpression nullDiff = tryCast(match.getLeftDiff(), PsiExpression.class);
      PsiExpression nonNullDiff = tryCast(match.getRightDiff(), PsiExpression.class);
      if(!ExpressionUtils.isReferenceTo(nullDiff, variable)) {
        TopmostQualifierDiff qualifierDiff = TopmostQualifierDiff.from(nullDiff, nonNullDiff);
        if(qualifierDiff == null) {
          PsiMethodCallExpression nullCall = tryCast(nullDiff, PsiMethodCallExpression.class);
          PsiMethodCallExpression nonNullCall = tryCast(nonNullDiff, PsiMethodCallExpression.class);
          if(nullCall == null || nonNullCall == null) return null;
          if(!STREAM_EMPTY.test(nonNullCall) || !STREAM_OF.test(nullCall)) return null;
          PsiExpression maybeRef = nullCall.getArgumentList().getExpressions()[0];
          if (!ExpressionUtils.isReferenceTo(maybeRef, variable)) return null;
          return new NotNullContext(nullCall, maybeRef, nullBranch, reference, ifStatement, null, true);
        }
        nullDiff = qualifierDiff.getLeft();
        nonNullDiff = qualifierDiff.getRight();
        if(!ExpressionUtils.isReferenceTo(nullDiff, variable)) return null;
      }
      if(NullabilityUtil.getExpressionNullability(nonNullDiff, true) != Nullability.NOT_NULL) return null;
      if(!LambdaGenerationUtil.canBeUncheckedLambda(nonNullDiff)) return null;
      return new NotNullContext(nonNullDiff, nullDiff, nullBranch, reference, ifStatement, toDelete, false);
    }
  }

  @NotNull
  private static String getMethod(PsiExpression expression) {
    return ExpressionUtils.isSafelyRecomputableExpression(expression) ? "requireNonNullElse" : "requireNonNullElseGet";
  }

  @NotNull
  private static String getMethodWithClass(PsiExpression expression, boolean isStream) {
    return isStream ? "Stream.ofNullable" : "Objects." + getMethod(expression);
  }


  private static class TernaryNotNullContext {
    private final @NotNull PsiConditionalExpression myTernary;
    private final @NotNull PsiExpression myNullExpr;
    private final @NotNull PsiReferenceExpression myReferenceExpression;

    private TernaryNotNullContext(@NotNull PsiConditionalExpression ternary,
                                  @NotNull PsiExpression nullExpr,
                                  @NotNull PsiReferenceExpression expression) {
      myTernary = ternary;
      myNullExpr = nullExpr;
      myReferenceExpression = expression;
    }

    @Nullable
    static TernaryNotNullContext from(@NotNull PsiConditionalExpression ternary) {
      PsiBinaryExpression binOp = tryCast(ternary.getCondition(), PsiBinaryExpression.class);
      if(binOp == null) return null;
      PsiExpression value = ExpressionUtils.getValueComparedWithNull(binOp);
      PsiReferenceExpression referenceExpression = tryCast(value, PsiReferenceExpression.class);
      if(referenceExpression == null) return null;
      PsiVariable variable = tryCast(referenceExpression.resolve(), PsiVariable.class);
      if(variable == null) return null;
      boolean negated = binOp.getOperationTokenType() == JavaTokenType.NE;
      PsiExpression nullBranch = negated ? ternary.getElseExpression() : ternary.getThenExpression();
      if(ClassUtils.isPrimitive(variable.getType())) return null;
      PsiExpression nonNullBranch = negated ? ternary.getThenExpression() : ternary.getElseExpression();
      if(!ExpressionUtils.isReferenceTo(nonNullBranch, variable)) return null;
      if(NullabilityUtil.getExpressionNullability(nullBranch, true) != Nullability.NOT_NULL) return null;
      if(!LambdaGenerationUtil.canBeUncheckedLambda(nullBranch)) return null;
      return new TernaryNotNullContext(ternary, nullBranch, referenceExpression);
    }
  }

  /**
   * Represents difference between o1.m1().m2() and o2.m1().m2()
   * Relies that call chain and arguments are exactly the same
   */
  private static class TopmostQualifierDiff {
    private final @Nullable PsiExpression myLeft;
    private final @Nullable PsiExpression myRight;

    private TopmostQualifierDiff(@Nullable PsiExpression left, @Nullable PsiExpression right) {
      myLeft = left;
      myRight = right;
    }

    @Nullable
    public PsiExpression getRight() {
      return myRight;
    }

    @Nullable
    public PsiExpression getLeft() {
      return myLeft;
    }

    @Nullable
    static TopmostQualifierDiff from(@Nullable PsiExpression left, @Nullable PsiExpression right) {
      PsiMethodCallExpression leftCall = tryCast(left, PsiMethodCallExpression.class);
      PsiMethodCallExpression rightCall = tryCast(right, PsiMethodCallExpression.class);
      if(leftCall == null || rightCall == null) return null;
      while(true) {
        PsiReferenceExpression leftMethodExpression = leftCall.getMethodExpression();
        PsiReferenceExpression rightMethodExpression = rightCall.getMethodExpression();
        if(tryCast(leftMethodExpression.resolve(), PsiMethod.class) != tryCast(rightMethodExpression.resolve(), PsiMethod.class)) return null;
        PsiExpression[] leftExpressions = leftCall.getArgumentList().getExpressions();
        PsiExpression[] rightExpressions = rightCall.getArgumentList().getExpressions();
        int length = leftExpressions.length;
        if(length != rightExpressions.length) return null;
        for (int i = 0; i < length; i++) {
          if(!ourEquivalence.expressionsAreEquivalent(leftExpressions[i], rightExpressions[i])) return null;
        }
        PsiExpression leftQualifier = leftMethodExpression.getQualifierExpression();
        leftCall = tryCast(leftQualifier, PsiMethodCallExpression.class);
        PsiExpression rightQualifier = rightMethodExpression.getQualifierExpression();
        rightCall = tryCast(rightQualifier, PsiMethodCallExpression.class);
        if(leftCall == null || rightCall == null) {
          return new TopmostQualifierDiff(leftQualifier, rightQualifier);
        }
      }
    }
  }
}

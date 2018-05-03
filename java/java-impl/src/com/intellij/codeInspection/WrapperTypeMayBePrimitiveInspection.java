// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.NullnessUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.util.ObjectUtils.tryCast;

public class WrapperTypeMayBePrimitiveInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher TO_STRING = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "toString");
  private static final CallMatcher HASH_CODE = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "hashCode");
  private static final CallMatcher VALUE_OF = CallMatcher.anyOf(
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_INTEGER, "valueOf").parameterTypes(CommonClassNames.JAVA_LANG_STRING),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_LONG, "valueOf").parameterTypes(CommonClassNames.JAVA_LANG_STRING),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_BOOLEAN, "valueOf").parameterTypes(CommonClassNames.JAVA_LANG_STRING),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_FLOAT, "valueOf").parameterTypes(CommonClassNames.JAVA_LANG_STRING),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_DOUBLE, "valueOf").parameterTypes(CommonClassNames.JAVA_LANG_STRING),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_SHORT, "valueOf").parameterTypes(CommonClassNames.JAVA_LANG_STRING),
    CallMatcher.staticCall(CommonClassNames.JAVA_LANG_BYTE, "valueOf").parameterTypes(CommonClassNames.JAVA_LANG_STRING)
  );

  private static final Map<String, String> ourReplacementMap = new HashMap<>();

  static {
    ourReplacementMap.put(CommonClassNames.JAVA_LANG_INTEGER, "parseInt");
    ourReplacementMap.put(CommonClassNames.JAVA_LANG_LONG, "parseLong");
    ourReplacementMap.put(CommonClassNames.JAVA_LANG_FLOAT, "parseFloat");
    ourReplacementMap.put(CommonClassNames.JAVA_LANG_BOOLEAN, "parseBoolean");
    ourReplacementMap.put(CommonClassNames.JAVA_LANG_DOUBLE, "parseDouble");
    ourReplacementMap.put(CommonClassNames.JAVA_LANG_SHORT, "parseShort");
    ourReplacementMap.put(CommonClassNames.JAVA_LANG_BYTE, "parseByte");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitLocalVariable(PsiLocalVariable variable) {
        if (!isBoxedType(variable.getType())) return;
        PsiExpression initializer = variable.getInitializer();
        UnboxingStatistics statistics = new UnboxingStatistics();
        if (initializer != null && !statistics.checkExpression(initializer)) return;

        if (ExpressionUtils.isNullLiteral(variable.getInitializer())) return;
        PsiElement block = PsiUtil.getVariableCodeBlock(variable, null);
        if (block == null) return;
        WrapperTypeCanBePrimitiveDetectingVisitor visitor = new WrapperTypeCanBePrimitiveDetectingVisitor(variable, statistics);
        block.accept(visitor);
        if (visitor.myBoxingRequired ||
            !visitor.myHasReferences ||
            !visitor.myStatistics.primitiveReplacementReducesUnnecessaryOperationCount()) {
          return;
        }
        holder.registerProblem(variable.getTypeElement(), InspectionsBundle.message("inspection.wrapper.type.may.be.primitive.name"),
                               new ConvertWrapperTypeToPrimitive());
      }
    };
  }

  private static boolean isValueOfCall(PsiExpression expression) {
    return expression instanceof PsiMethodCallExpression && VALUE_OF.test((PsiMethodCallExpression)expression);
  }

  private static class UnboxingStatistics {
    private int myBoxedUnnecessaryOperationCount = 0;
    private int myUnboxedUnnecessaryOperationCount = 0;

    /**
     * Check, whether expression passed as argument is suitable to be right part of assignment or initializer when variable will be primitive
     * Also collect statistics if boxing needed or unboxing needed
     *
     * @return false if boxing is required anyway
     */
    boolean checkExpression(@NotNull PsiExpression expression) {
      if (expression.getType() instanceof PsiPrimitiveType && !PsiType.NULL.equals(expression.getType())) {
        myBoxedUnnecessaryOperationCount++;
      }
      else if (!isValueOfCall(expression)) {
        if (NullnessUtil.getExpressionNullness(expression) != Nullness.NOT_NULL) { // not safe using with primitive
          return false;
        }
        myUnboxedUnnecessaryOperationCount++;
      }
      return true;
    }

    boolean primitiveReplacementReducesUnnecessaryOperationCount() {
      return myUnboxedUnnecessaryOperationCount < myBoxedUnnecessaryOperationCount;
    }
  }

  private static class WrapperTypeCanBePrimitiveDetectingVisitor extends JavaRecursiveElementWalkingVisitor {
    private final PsiLocalVariable myVariable;
    boolean myBoxingRequired = false;
    boolean myHasReferences = false;
    private final @NotNull UnboxingStatistics myStatistics;

    public WrapperTypeCanBePrimitiveDetectingVisitor(PsiLocalVariable variable,
                                                     @NotNull UnboxingStatistics statistics) {
      myVariable = variable;
      myStatistics = statistics;
    }

    private static final int IN_LOOP_ASSIGNMENT_OPERATION_MULTIPLIER = 10;

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (!ExpressionUtils.isReferenceTo(expression, myVariable)) return;
      myHasReferences = true;
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression).getParent();
      PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier(expression);
      if (call != null) {
        if (TO_STRING.test(call) || HASH_CODE.test(call)) return;
        boxingRequired();
      }
      else if (parent instanceof PsiExpressionList) {
        PsiElement grandParent = parent.getParent();
        if (!(grandParent instanceof PsiCallExpression)) return;
        PsiExpression[] arguments = ((PsiExpressionList)parent).getExpressions();
        int argumentsIndex = ArrayUtil.indexOf(arguments, expression);
        if (argumentsIndex == -1) return;
        PsiCallExpression callExpression = (PsiCallExpression)grandParent;
        PsiMethod method = callExpression.resolveMethod();
        if (method == null) return;
        PsiParameter[] parameters = method.getParameterList().getParameters();
        int parameterIndex = parameters.length < argumentsIndex + 1 ? parameters.length - 1 : argumentsIndex;
        PsiParameter parameter = parameters[parameterIndex];
        PsiType type = parameter.getType();
        if (type instanceof PsiPrimitiveType) {
          myStatistics.myBoxedUnnecessaryOperationCount++;
        }
        else {
          myStatistics.myUnboxedUnnecessaryOperationCount++;
        }
      }
      else if (parent instanceof PsiAssignmentExpression) {
        PsiExpression rExpression = ((PsiAssignmentExpression)parent).getRExpression();
        if (rExpression == null) return;
        if (!myStatistics.checkExpression(rExpression)) {
          boxingRequired();
        }
      }
      else if (parent instanceof PsiSynchronizedStatement) {
        boxingRequired();
      }
      else if (parent instanceof PsiBinaryExpression) {
        checkBinaryExpression((PsiBinaryExpression)parent);
      }
      else if (parent instanceof PsiReturnStatement) {
        PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class, false, PsiLambdaExpression.class);
        if (method != null) {
          PsiType returnType = method.getReturnType();
          if (returnType != null) {
            if (returnType instanceof PsiPrimitiveType) {
              myStatistics.myBoxedUnnecessaryOperationCount++;
            } else {
              myStatistics.myUnboxedUnnecessaryOperationCount++;
            }
          }
        }
      }
    }

    private void checkBinaryExpression(PsiBinaryExpression binaryExpression) {
      IElementType operationTokenType = binaryExpression.getOperationTokenType();
      PsiExpression other = ExpressionUtils.getOtherOperand(binaryExpression, myVariable);
      PsiType type = other.getType();
      if (operationTokenType == JavaTokenType.EQEQ || operationTokenType == JavaTokenType.NE) {
        if (type instanceof PsiPrimitiveType && !PsiType.NULL.equals(type)) {
          myStatistics.myBoxedUnnecessaryOperationCount++;
        } else {
          boxingRequired();
          return;
        }
      }

      int boxedUnnecessaryOpImpact = 0;
      int unboxedUnnecessaryOpImpact = 0;
      if (type instanceof PsiPrimitiveType) {
        if (PsiType.NULL.equals(type)) {
          boxingRequired();
          return;
        }
        else {
          if (ExpressionUtils.isCompoundAssignmentOperation(binaryExpression)) {
            boxedUnnecessaryOpImpact += 2;
          }
        }
      }
      else if (NullnessUtil.getExpressionNullness(other) == Nullness.NOT_NULL) {
        boxedUnnecessaryOpImpact += 3;
        unboxedUnnecessaryOpImpact += 3;
      }
      else {
        boxingRequired();
        return;
      }
      PsiLoopStatement binopLoop =
        PsiTreeUtil.getParentOfType(binaryExpression, PsiLoopStatement.class, false, PsiClass.class, PsiLambdaExpression.class);
      PsiLoopStatement variableLoop =
        PsiTreeUtil.getParentOfType(myVariable, PsiLoopStatement.class, false, PsiClass.class, PsiLambdaExpression.class);
      if (binopLoop != null && binopLoop == variableLoop) {
        boxedUnnecessaryOpImpact *= IN_LOOP_ASSIGNMENT_OPERATION_MULTIPLIER;
        unboxedUnnecessaryOpImpact *= IN_LOOP_ASSIGNMENT_OPERATION_MULTIPLIER;
      }
      myStatistics.myBoxedUnnecessaryOperationCount += boxedUnnecessaryOpImpact;
      myStatistics.myUnboxedUnnecessaryOperationCount += unboxedUnnecessaryOpImpact;
    }

    // Strong boxing requirement
    private void boxingRequired() {
      myBoxingRequired = true;
      stopWalking();
    }
  }

  private static boolean isBoxedType(@NotNull PsiType type) {
    return type.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN) ||
           type.equalsToText(CommonClassNames.JAVA_LANG_INTEGER) ||
           type.equalsToText(CommonClassNames.JAVA_LANG_LONG) ||
           type.equalsToText(CommonClassNames.JAVA_LANG_DOUBLE) ||
           type.equalsToText(CommonClassNames.JAVA_LANG_FLOAT) ||
           type.equalsToText(CommonClassNames.JAVA_LANG_SHORT) ||
           type.equalsToText(CommonClassNames.JAVA_LANG_BYTE) ||
           type.equalsToText(CommonClassNames.JAVA_LANG_CHARACTER);
  }

  private static class ConvertWrapperTypeToPrimitive implements LocalQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.wrapper.type.may.be.primitive.fix.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      PsiTypeElement typeElement = tryCast(element, PsiTypeElement.class);
      if (typeElement == null) return;
      PsiLocalVariable variable = tryCast(typeElement.getParent(), PsiLocalVariable.class);
      if (variable == null) return;
      PsiExpression initializer = variable.getInitializer();
      if (initializer != null) {
        tryReplaceStaticCall(initializer);
      }
      PsiType type = variable.getType();
      String boxedType = type.getCanonicalText();
      String unboxedType = PsiTypesUtil.unboxIfPossible(boxedType);
      if (unboxedType.equals(boxedType)) return;
      PsiElement codeBlock = PsiUtil.getVariableCodeBlock(variable, null);
      if (codeBlock == null) return;
      codeBlock.accept(new UnboxingVisitor(variable));
      new CommentTracker().replaceAndRestoreComments(typeElement, unboxedType);
    }

    private static class UnboxingVisitor extends JavaRecursiveElementVisitor {
      private final PsiLocalVariable myVariable;

      public UnboxingVisitor(PsiLocalVariable variable) {myVariable = variable;}

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (!ExpressionUtils.isReferenceTo(expression, myVariable)) return;
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression).getParent();
        PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier(expression);
        if (call != null) {
          replaceInstanceCall(call);
        }
        else if (parent instanceof PsiAssignmentExpression) {
          PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
          if (!ExpressionUtils.isReferenceTo(assignment.getLExpression(), myVariable)) return;
          tryReplaceStaticCall(assignment.getRExpression());
        }
      }
    }

    private static void tryReplaceStaticCall(PsiExpression expression) {
      PsiMethodCallExpression callExpression = tryCast(expression, PsiMethodCallExpression.class);
      if (!VALUE_OF.test(callExpression)) return;
      PsiMethod method = callExpression.resolveMethod();
      if (method == null) return;
      PsiClass containingClass = method.getContainingClass();
      PsiExpression[] arguments = callExpression.getArgumentList().getExpressions();
      if (arguments.length != 1) return;
      PsiExpression argument = arguments[0];
      if (containingClass == null) return;
      String containingClassName = containingClass.getQualifiedName();
      String replacementMethodCall = ourReplacementMap.get(containingClassName);
      if (replacementMethodCall == null) return;
      CommentTracker tracker = new CommentTracker();
      String argumentText = tracker.text(argument);
      String replacementText = containingClassName + "." + replacementMethodCall + "(" + argumentText + ")";
      tracker.replaceAndRestoreComments(callExpression, replacementText);
    }

    private static void replaceInstanceCall(PsiMethodCallExpression call) {
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return;
      PsiType qualifierType = qualifier.getType();
      if (qualifierType == null) return;
      String qualifierTypeText = qualifierType.getCanonicalText();
      CommentTracker tracker = new CommentTracker();
      String qualifierText = tracker.text(qualifier);
      String methodNameText;
      if (HASH_CODE.test(call)) {
        methodNameText = "hashCode";
      }
      else if (TO_STRING.test(call)) {
        methodNameText = "toString";
      }
      else {
        return;
      }
      String callReplacementText = qualifierTypeText + "." + methodNameText + "(" + qualifierText + ")";
      tracker.replaceAndRestoreComments(call, callReplacementText);
    }
  }
}

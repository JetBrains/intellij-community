// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.java.JavaBundle;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.JavaPsiBoxingUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.tryCast;

public class WrapperTypeMayBePrimitiveInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher TO_STRING = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "toString");
  private static final CallMatcher HASH_CODE = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "hashCode");
  private static final CallMatcher VALUE_OF = getValueOfMatcher();

  private static final Set<String> ourAllowedInstanceCalls = new HashSet<>();

  static {
    ourAllowedInstanceCalls.add("isInfinite");
    ourAllowedInstanceCalls.add("isNaN");
    ourAllowedInstanceCalls.add("byteValue");
    ourAllowedInstanceCalls.add("shortValue");
    ourAllowedInstanceCalls.add("intValue");
    ourAllowedInstanceCalls.add("longValue");
    ourAllowedInstanceCalls.add("floatValue");
    ourAllowedInstanceCalls.add("doubleValue");
  }

  private static CallMatcher getValueOfMatcher() {
    CallMatcher[] matchers = JvmPrimitiveTypeKind.getBoxedFqns()
                                                 .stream()
                                                 .filter(fqn -> !fqn.equals(CommonClassNames.JAVA_LANG_CHARACTER))
                                                 .map(fqn -> CallMatcher.staticCall(fqn, "valueOf")
                                                                        .parameterTypes(CommonClassNames.JAVA_LANG_STRING))
                                                 .toArray(CallMatcher[]::new);
    return CallMatcher.anyOf(matchers);
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethod(PsiMethod method) {
        PsiCodeBlock body = method.getBody();
        if (body == null) return;
        WrapperTypeMayBePrimitiveDetectingVisitor visitor = new WrapperTypeMayBePrimitiveDetectingVisitor();
        body.accept(visitor);
        for (PsiVariable variable : visitor.getVariablesToUnbox()) {
          PsiElement elementToHighlight = variable.getTypeElement() != null ? variable.getTypeElement() : variable;
          holder.registerProblem(elementToHighlight, JavaBundle.message("inspection.wrapper.type.may.be.primitive.name"), new ConvertWrapperTypeToPrimitive());
        }
      }
    };
  }

  private static final class BoxingInfo {
    private final @NotNull PsiVariable myVariable;
    boolean myHasReferences = false;

    // Change in count of operations after removing boxing (inexact, due to loops)
    // If less than 0 - worth to remove
    private int myAfterRemovalOperationCountDiff = 0;

    private BoxingInfo(@NotNull PsiVariable variable) {myVariable = variable;}

    /**
     * Check, whether expression passed as argument is suitable to be right part of assignment or initializer when variable will be primitive
     * Also collect statistics if boxing needed or unboxing needed
     *
     * @return false if boxing is required anyway
     */
    boolean checkExpression(@NotNull PsiExpression expression) {
      if (expression.getType() instanceof PsiPrimitiveType && !PsiType.NULL.equals(expression.getType())) {
        myAfterRemovalOperationCountDiff -= 1;
      }
      else if (isValueOfCall(expression)) {
        myAfterRemovalOperationCountDiff -= 1;
      }
      else {
        if (NullabilityUtil.getExpressionNullability(expression, true) != Nullability.NOT_NULL) { // not safe using with primitive
          return false;
        }
        myAfterRemovalOperationCountDiff += 1;
      }
      return true;
    }
    boolean primitiveReplacementReducesUnnecessaryOperationCount() {
      return myAfterRemovalOperationCountDiff < 0;
    }
  }

  private static boolean isValueOfCall(PsiExpression expression) {
    return expression instanceof PsiMethodCallExpression && VALUE_OF.test((PsiMethodCallExpression)expression);
  }

  private static class WrapperTypeMayBePrimitiveDetectingVisitor extends JavaRecursiveElementWalkingVisitor {
    private static final int IN_LOOP_OPERATION_MULTIPLIER = 10;

    // name to list of boxes
    private final Map<String, List<BoxingInfo>> myBoxingMap = new HashMap<>();

    @Override
    public void visitClass(PsiClass aClass) {
      // To avoid revisiting elements from child method
    }

    @Override
    public void visitLocalVariable(PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      if (variable instanceof PsiField) return;
      if (!TypeConversionUtil.isPrimitiveWrapper(variable.getType())) return;
      PsiExpression initializer = variable.getInitializer();
      BoxingInfo boxingInfo = new BoxingInfo(variable);
      if (initializer != null && !boxingInfo.checkExpression(initializer)) return;
      String name = variable.getName();
      ArrayList<BoxingInfo> infos = new ArrayList<>();
      infos.add(boxingInfo);
      myBoxingMap.put(name, infos);
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      String name = expression.getReferenceName();
      if (name == null) return;
      List<BoxingInfo> infos = myBoxingMap.get(name);
      if (infos == null) return;
      Iterator<BoxingInfo> iterator = infos.iterator();
      while (iterator.hasNext()) {
        BoxingInfo boxingInfo = iterator.next();
        if (!ExpressionUtils.isReferenceTo(expression, boxingInfo.myVariable)) continue;
        boxingInfo.myHasReferences = true;
        final Integer boxingRemovalImpact = afterBoxingRemovalReferenceBoostedImpact(expression, boxingInfo);
        if (boxingRemovalImpact == null)  {
          iterator.remove();
        } else {
          boxingInfo.myAfterRemovalOperationCountDiff += boxingRemovalImpact;
        }
        break;
      }
      if (infos.isEmpty()) {
        myBoxingMap.remove(name);
      }
    }

    public List<PsiVariable> getVariablesToUnbox() {
      List<PsiVariable> variables = new ArrayList<>();
      for (List<BoxingInfo> infos : myBoxingMap.values()) {
        for (BoxingInfo boxingInfo : infos) {
          if (boxingInfo.myHasReferences && boxingInfo.primitiveReplacementReducesUnnecessaryOperationCount()) {
            variables.add(boxingInfo.myVariable);
          }
        }
      }
      return variables;
    }

    private static boolean isAllowedInstanceCall(@NotNull PsiMethodCallExpression call) {
      PsiMethod method = call.resolveMethod();
      if (method == null) return false;
      if (method.hasModifier(JvmModifier.STATIC)) return false;
      return ourAllowedInstanceCalls.contains(call.getMethodExpression().getReferenceName());
    }

    @Nullable("When use not allows unboxing")
    private static Integer afterBoxingRemovalReferenceBoostedImpact(@NotNull PsiReferenceExpression expression,
                                                             @NotNull BoxingInfo boxingInfo) {
      Integer impact = afterBoxingRemovalReferenceImpact(expression, boxingInfo);
      if (impact == null) return null;
      final LocalSearchScope scope = tryCast(boxingInfo.myVariable.getUseScope(), LocalSearchScope.class);
      if (scope == null) return impact;
      final PsiElement[] scopeElements = scope.getScope();
      PsiLoopStatement loop =
        PsiTreeUtil.getParentOfType(expression, PsiLoopStatement.class, false, PsiClass.class, PsiLambdaExpression.class, PsiMethod.class);
      if (loop != null
          && StreamEx.of(scopeElements).anyMatch(scopeElement -> PsiTreeUtil.isAncestor(scopeElement, loop, false))
                           && !PsiTreeUtil.isAncestor(loop, boxingInfo.myVariable, true)) {
        impact *= IN_LOOP_OPERATION_MULTIPLIER;
      }
      return impact;
    }

    @Nullable("When use not allows unboxing")
    private static Integer afterBoxingRemovalReferenceImpact(@NotNull PsiReferenceExpression expression,
                                                             @NotNull BoxingInfo boxingInfo) {
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression).getParent();
      PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier(expression);
      if (call != null) {
        if(!TO_STRING.test(call) && !HASH_CODE.test(call) && !isAllowedInstanceCall(call)) return null;
      }
      if (parent instanceof PsiExpressionList) {
        return expressionListImpactAfterBoxingRemoval((PsiExpressionList)parent, expression);
      }
      else if (parent instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
        final PsiType lExprType = assignment.getLExpression().getType();
        if (lExprType != null && TypeUtils.isJavaLangString(lExprType)) return 0;
        PsiExpression rExpression = assignment.getRExpression();
        if (rExpression == null) return 0;
        if (!boxingInfo.checkExpression(rExpression)) return null;
        return TypeConversionUtil.convertEQtoOperation(assignment.getOperationTokenType()) != null ? -2 : -1;
      }
      else if (parent instanceof PsiSynchronizedStatement) {
        return null;
      }
      else if (parent instanceof PsiPolyadicExpression) {
        return polyadicExpressionImpactAfterBoxingRemoval((PsiPolyadicExpression)parent, expression);
      }
      else if (parent instanceof PsiReturnStatement) {
        PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class, false, PsiLambdaExpression.class);
        if (method != null) {
          PsiType returnType = method.getReturnType();
          if (returnType != null) {
            return returnType instanceof PsiPrimitiveType ? -1 : 1;
          }
        }
      }
      return 0;
    }

    @Nullable("When use not allows unboxing")
    private static Integer expressionListImpactAfterBoxingRemoval(@NotNull PsiExpressionList expressionList,
                                                                  @NotNull PsiReferenceExpression reference) {
      PsiElement grandParent = expressionList.getParent();
      if (!(grandParent instanceof PsiCallExpression)) return null;
      PsiExpression[] arguments = expressionList.getExpressions();
      int argumentsIndex = ArrayUtil.indexOf(arguments, reference);
      if (argumentsIndex == -1) return null;
      PsiCallExpression callExpression = (PsiCallExpression)grandParent;
      PsiMethod method = callExpression.resolveMethod();
      if (method == null) return null;
      PsiParameter[] parameters = method.getParameterList().getParameters();
      int parameterIndex = parameters.length < argumentsIndex + 1 ? parameters.length - 1 : argumentsIndex;
      if (parameterIndex < 0) return null;
      PsiParameter parameter = parameters[parameterIndex];
      PsiType type = parameter.getType();
      return type instanceof PsiPrimitiveType ? -1 : 1;
    }

    /**
     * @param haveBoxAfter if call has wrapper type in one of operands after removal boxing
     */
    private static int getBinOpImpact(boolean haveBoxAfter, IElementType operationTokenType) {
      if (!haveBoxAfter) return -1;
      if (operationTokenType == JavaTokenType.EQEQ || operationTokenType == JavaTokenType.NE) return -1;
      final boolean isRelational = operationTokenType == JavaTokenType.GT || operationTokenType == JavaTokenType.GE ||
                        operationTokenType == JavaTokenType.LT || operationTokenType == JavaTokenType.LE;
      return isRelational ? 0 : 1;
    }

    @Nullable("When use not allows unboxing")
    private static Integer polyadicExpressionImpactAfterBoxingRemoval(@NotNull PsiPolyadicExpression polyadic,
                                                                      PsiReferenceExpression reference) {
      final PsiExpression[] operands = polyadic.getOperands();
      final IElementType tokenType = polyadic.getOperationTokenType();

      int afterRemovalOpCountDiff = 0;
      int referenceIndex = -1;
      boolean leftPartBoxed = false;
      boolean hasLeftPart = false;
      boolean isPlus = polyadic.getOperationTokenType() == JavaTokenType.PLUS;
      for (int i = 0; i < operands.length; i++) {
        PsiExpression operand = PsiUtil.skipParenthesizedExprDown(operands[i]);
        if (operand == null) return null;
        // Assume all polyadic expressions are left associative, so handle left side
        if (operand == reference) {
          referenceIndex = i;
          break;
        }
        hasLeftPart = true;
        final PsiType operandType = operand.getType();
        if (isPlus && TypeUtils.isJavaLangString(operandType)) {
          return afterRemovalOpCountDiff; // everything else will be string
        }
        if (!(operandType instanceof PsiPrimitiveType)) {
          if (NullabilityUtil.getExpressionNullability(operand, true) != Nullability.NOT_NULL) return null;
          leftPartBoxed = true;
        }
      }
      if (hasLeftPart) {
        afterRemovalOpCountDiff += getBinOpImpact(leftPartBoxed, tokenType);
      }

      int nextOperandIndex = referenceIndex + 1;
      if (nextOperandIndex < operands.length) {
        final PsiExpression next = operands[nextOperandIndex];
        if (ExpressionUtils.isNullLiteral(next)) return null;
        afterRemovalOpCountDiff += getBinOpImpact(!(next.getType() instanceof PsiPrimitiveType), tokenType);
      }
      return afterRemovalOpCountDiff;
    }
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
      return JavaBundle.message("inspection.wrapper.type.may.be.primitive.fix.name");
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

      UnboxingVisitor(PsiLocalVariable variable) {myVariable = variable;}

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
      String replacementMethodCall = JavaPsiBoxingUtils.getParseMethod(containingClassName);
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
      String replacement = findStaticReplacement(call, qualifierText, qualifierTypeText);
      if (replacement == null) return;
      tracker.replaceAndRestoreComments(call, replacement);
    }

    private static String findStaticReplacement(PsiMethodCallExpression call, String qualifierText, String qualifierTypeText) {
      String methodNameText;
      String callName = call.getMethodExpression().getReferenceName();
      if (HASH_CODE.test(call)) {
        methodNameText = "hashCode";
      }
      else if (TO_STRING.test(call)) {
        methodNameText = "toString";
      }
      else if ("isInfinite".equals(callName)) {
        methodNameText = "isInfinite";
      }
      else if ("isNaN".equals(callName)) {
        methodNameText = "isNaN";
      } else {
        methodNameText = null;
      }
      if (methodNameText != null) {
        return qualifierTypeText + "." + methodNameText + "(" + qualifierText + ")";
      }
      String type;
      if ("intValue".equals(callName)) {
        type = "int";
      } else if ("byteValue".equals(callName)) {
        type = "byte";
      } else if ("floatValue".equals(callName)) {
        type = "float";
      } else if ("doubleValue".equals(callName)) {
        type = "double";
      } else if ("shortValue".equals(callName)) {
        type = "short";
      } else {
        return null;
      }
      return "(" + type + ")" + qualifierText;
    }
  }
}

/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.lang.java.parser.JavaBinaryOperations;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;

public final class ExpectedTypeUtils {

  private ExpectedTypeUtils() {}

  public static @Nullable PsiType findExpectedType(@NotNull PsiExpression expression, boolean calculateTypeForComplexReferences) {
    return findExpectedType(expression, calculateTypeForComplexReferences, false);
  }

  public static PsiType findExpectedType(PsiExpression expression, boolean calculateTypeForComplexReferences, boolean reportCasts) {
    PsiElement context = expression.getParent();
    PsiExpression wrappedExpression = expression;
    while (context instanceof PsiParenthesizedExpression) {
      wrappedExpression = (PsiExpression)context;
      context = context.getParent();
    }
    if (context == null) {
      return null;
    }
    final ExpectedTypeVisitor visitor = new ExpectedTypeVisitor(wrappedExpression, calculateTypeForComplexReferences, reportCasts);
    context.accept(visitor);
    return visitor.getExpectedType();
  }

  private static class ExpectedTypeVisitor extends JavaElementVisitor {

    private static final TokenSet arithmeticOps =
      TokenSet.create(JavaTokenType.PLUS, JavaTokenType.MINUS, JavaTokenType.ASTERISK, JavaTokenType.DIV, JavaTokenType.PERC);

    private static final TokenSet booleanOps =
      TokenSet.create(JavaTokenType.ANDAND, JavaTokenType.AND, JavaTokenType.XOR, JavaTokenType.OROR, JavaTokenType.OR);

    private static final TokenSet operatorAssignmentOps =
      TokenSet.create(JavaTokenType.PLUSEQ, JavaTokenType.MINUSEQ, JavaTokenType.ASTERISKEQ, JavaTokenType.DIVEQ, JavaTokenType.ANDEQ,
                      JavaTokenType.OREQ, JavaTokenType.XOREQ, JavaTokenType.PERCEQ, JavaTokenType.LTLTEQ, JavaTokenType.GTGTEQ,
                      JavaTokenType.GTGTGTEQ);

    private final @NotNull PsiExpression wrappedExpression;
    private final boolean calculateTypeForComplexReferences;
    private final boolean reportCasts;
    private PsiType expectedType = null;

    ExpectedTypeVisitor(@NotNull PsiExpression wrappedExpression, boolean calculateTypeForComplexReferences, boolean reportCasts) {
      this.wrappedExpression = wrappedExpression;
      this.calculateTypeForComplexReferences = calculateTypeForComplexReferences;
      this.reportCasts = reportCasts;
    }

    public PsiType getExpectedType() {
      return expectedType;
    }

    @Override
    public void visitNameValuePair(@NotNull PsiNameValuePair pair) {
      if (!wrappedExpression.equals(pair.getValue())) {
        return;
      }
      final PsiReference reference = pair.getReference();
      if (reference == null) {
        return;
      }
      final PsiElement target = reference.resolve();
      if (target instanceof PsiAnnotationMethod) {
        expectedType = ((PsiMethod)target).getReturnType();
      }
    }

    @Override
    public void visitField(@NotNull PsiField field) {
      final PsiExpression initializer = field.getInitializer();
      if (wrappedExpression.equals(initializer)) {
        expectedType = field.getType();
      }
    }

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement != null && !typeElement.isInferredType()) {
        expectedType = variable.getType();
      }
    }

    @Override
    public void visitAssertStatement(@NotNull PsiAssertStatement statement) {
      final PsiExpression condition = statement.getAssertCondition();
      if (wrappedExpression == condition) {
        expectedType = PsiTypes.booleanType();
      }
      else {
        expectedType = TypeUtils.getStringType(statement);
      }
    }

    @Override
    public void visitArrayInitializerExpression(@NotNull PsiArrayInitializerExpression initializer) {
      final PsiType type = initializer.getType();
      if (!(type instanceof PsiArrayType arrayType)) {
        expectedType = null;
        return;
      }
      expectedType = arrayType.getComponentType();
    }

    @Override
    public void visitArrayAccessExpression(@NotNull PsiArrayAccessExpression accessExpression) {
      final PsiExpression indexExpression = accessExpression.getIndexExpression();
      if (wrappedExpression.equals(indexExpression)) {
        expectedType = PsiTypes.intType();
      }
    }

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression polyadicExpression) {
      final PsiExpression[] operands = polyadicExpression.getOperands();
      if (operands.length < 2) {
        expectedType = null;
        return;
      }
      for (PsiExpression operand : operands) {
        if (operand == null || operand.getType() == null) {
          expectedType = null;
          return;
        }
      }
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      final PsiType type = polyadicExpression.getType();
      final PsiType wrappedExpressionType = wrappedExpression.getType();
      if (TypeUtils.isJavaLangString(type) || isArithmeticOperation(tokenType) || isBooleanOperation(tokenType)) {
        expectedType = type;
      }
      else if (isShiftOperation(tokenType)) {
        expectedType = TypeUtils.unaryNumericPromotion(wrappedExpressionType);
      }
      else if (ComparisonUtils.isEqualityComparison(polyadicExpression)) {
        final PsiExpression operand1 = operands[0];
        final PsiExpression operand2 = operands[1];
        if (operand1 == wrappedExpression || operand2 == wrappedExpression) {
          final PsiType type1 = operand1.getType();
          final PsiType type2 = operand2.getType();
          if (type1 instanceof PsiPrimitiveType) {
            expectedType = expectedPrimitiveType((PsiPrimitiveType)type1, type2);
          }
          else if (type2 instanceof PsiPrimitiveType) {
            expectedType = expectedPrimitiveType((PsiPrimitiveType)type2, type1);
          }
          else {
            expectedType = TypeUtils.getObjectType(wrappedExpression);
          }
        }
        else {
          // third or more operand must always be boolean or does not compile
          expectedType = TypeConversionUtil.isBooleanType(wrappedExpressionType) ? PsiTypes.booleanType() : null;
        }
      }
      else if (ComparisonUtils.isComparisonOperation(tokenType)) {
        if (operands.length != 2) {
          expectedType = null;
          return;
        }
        else if (!TypeConversionUtil.isPrimitiveAndNotNull(wrappedExpressionType)) {
          if (PsiPrimitiveType.getUnboxedType(wrappedExpressionType) == null) {
            return;
          }
        }
        expectedType = TypeConversionUtil.binaryNumericPromotion(operands[0].getType(), operands[1].getType());
      }
      else {
        expectedType = null;
      }
    }

    private PsiType expectedPrimitiveType(PsiPrimitiveType type1, PsiType type2) {
      if (TypeConversionUtil.isNumericType(type1)) {
        // JLS 15.21.1. Numerical Equality Operators == and !=
        return TypeConversionUtil.isNumericType(type2) ? TypeConversionUtil.binaryNumericPromotion(type1, type2) : null;
      }
      else if (PsiTypes.booleanType().equals(type1)) {
        // JLS 15.21.2. Boolean Equality Operators == and !=
        return TypeConversionUtil.isBooleanType(type2) ? PsiTypes.booleanType() : null;
      }
      else if (PsiTypes.nullType().equals(type1)) {
        return TypeUtils.getObjectType(wrappedExpression);
      }
      // void
      return null;
    }

    @Override
    public void visitUnaryExpression(@NotNull PsiUnaryExpression expression) {
      final PsiType type = expression.getType();
      if (type instanceof PsiPrimitiveType) {
        expectedType = type;
      }
      else {
        expectedType = PsiPrimitiveType.getUnboxedType(type);
      }
    }

    @Override
    public void visitSwitchExpression(@NotNull PsiSwitchExpression expression) {
      visitSwitchBlock(expression);
    }

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      visitSwitchBlock(statement);
    }

    private void visitSwitchBlock(PsiSwitchBlock switchBlock) {
      final PsiExpression expression = switchBlock.getExpression();
      if (expression == null) {
        return;
      }
      final PsiType type = expression.getType();
      final PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(type);
      if (unboxedType != null) {
        expectedType = unboxedType;
      }
      else {
        expectedType = type;
      }
    }

    @Override
    public void visitExpressionStatement(@NotNull PsiExpressionStatement statement) {
      final PsiElement parent = statement.getParent();
      if (!(parent instanceof PsiSwitchLabeledRuleStatement switchLabeledRuleStatement)) {
        return;
      }
      final PsiSwitchBlock block = switchLabeledRuleStatement.getEnclosingSwitchBlock();
      if (!(block instanceof PsiSwitchExpression switchExpression)) {
        return;
      }
      expectedType = switchExpression.getType();
    }

    @Override
    public void visitYieldStatement(@NotNull PsiYieldStatement statement) {
      PsiSwitchExpression expression = statement.findEnclosingExpression();
      if (expression != null) {
        expectedType = expression.getType();
      }
    }

    @Override
    public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
      if (reportCasts) {
        expectedType = expression.getType();
      }
    }

    @Override
    public void visitSwitchLabeledRuleStatement(@NotNull PsiSwitchLabeledRuleStatement statement) {
      expectedType = PsiTypes.booleanType(); // guard
    }

    @Override
    public void visitSwitchLabelStatement(@NotNull PsiSwitchLabelStatement statement) {
      expectedType = PsiTypes.booleanType(); // guard
    }

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement whileStatement) {
      expectedType = PsiTypes.booleanType();
    }

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      expectedType = PsiTypes.booleanType();
    }

    @Override
    public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
      final PsiExpression iteratedValue = statement.getIteratedValue();
      if (iteratedValue == null) {
        expectedType = null;
        return;
      }
      final PsiType iteratedValueType = iteratedValue.getType();
      if (!(iteratedValueType instanceof PsiClassType classType)) {
        expectedType = null;
        return;
      }
      final PsiType[] parameters = classType.getParameters();
      final PsiClass iterableClass = ClassUtils.findClass(CommonClassNames.JAVA_LANG_ITERABLE, statement);
      if (iterableClass == null) {
        expectedType = null;
      }
      else {
        expectedType = JavaPsiFacade.getElementFactory(statement.getProject()).createType(iterableClass, parameters);
      }
    }

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement statement) {
      expectedType = PsiTypes.booleanType();
    }

    @Override
    public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
      expectedType = PsiTypes.booleanType();
    }

    @Override
    public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
      expectedType = TypeUtils.getObjectType(statement);
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assignment) {
      final PsiExpression rExpression = assignment.getRExpression();
      final IElementType tokenType = assignment.getOperationTokenType();
      final PsiExpression lExpression = assignment.getLExpression();
      final PsiType lType = lExpression.getType();
      if (wrappedExpression.equals(rExpression)) {
        if (lType == null) {
          expectedType = null;
        }
        else if (TypeUtils.isJavaLangString(lType)) {
          if (JavaTokenType.PLUSEQ.equals(tokenType)) {
            // e.g. String += any type
            expectedType = rExpression.getType();
          }
          else {
            expectedType = lType;
          }
        }
        else if (isOperatorAssignmentOperation(tokenType)) {
          if (lType instanceof PsiPrimitiveType) {
            expectedType = lType;
          }
          else {
            expectedType = PsiPrimitiveType.getUnboxedType(lType);
          }
        }
        else {
          expectedType = lType;
        }
      }
      else {
        if (isOperatorAssignmentOperation(tokenType) && !(lType instanceof PsiPrimitiveType)) {
          expectedType = PsiPrimitiveType.getUnboxedType(lType);
        }
        else {
          expectedType = lType;
        }
      }
    }

    @Override
    public void visitConditionalExpression(@NotNull PsiConditionalExpression conditional) {
      final PsiExpression condition = conditional.getCondition();
      if (condition.equals(wrappedExpression)) {
        expectedType = PsiTypes.booleanType();
      }
      else {
        expectedType = conditional.getType();
      }
    }

    @Override
    public void visitReturnStatement(@NotNull PsiReturnStatement returnStatement) {
      expectedType = PsiTypesUtil.getMethodReturnType(returnStatement);
    }

    @Override
    public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
      expectedType = LambdaUtil.getFunctionalInterfaceReturnType(expression);
    }

    @Override
    public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
      expectedType = TypeUtils.getObjectType(expression);
    }

    @Override
    public void visitDeclarationStatement(@NotNull PsiDeclarationStatement declaration) {
      final PsiElement[] declaredElements = declaration.getDeclaredElements();
      for (PsiElement declaredElement : declaredElements) {
        if (declaredElement instanceof PsiVariable variable) {
          final PsiExpression initializer = variable.getInitializer();
          if (wrappedExpression.equals(initializer)) {
            expectedType = variable.getType();
            return;
          }
        }
      }
    }

    @Override
    public void visitExpressionList(@NotNull PsiExpressionList expressionList) {
      final JavaResolveResult result = findCalledMethod(expressionList);
      final PsiMethod method = (PsiMethod)result.getElement();
      if (method == null) {
        expectedType = null;
      }
      else {
        final int parameterPosition = getParameterPosition(expressionList, wrappedExpression);
        expectedType = getTypeOfParameter(result, parameterPosition);
      }
    }

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      final PsiExpression[] arrayDimensions = expression.getArrayDimensions();
      for (PsiExpression arrayDimension : arrayDimensions) {
        if (wrappedExpression.equals(arrayDimension)) {
          expectedType = PsiTypes.intType();
          break;
        }
      }
    }

    private static @NotNull JavaResolveResult findCalledMethod(PsiExpressionList expressionList) {
      final PsiElement parent = expressionList.getParent();
      if (parent instanceof PsiCall call) {
        return call.resolveMethodGenerics();
      }
      else if (parent instanceof PsiAnonymousClass) {
        final PsiElement grandParent = parent.getParent();
        if (grandParent instanceof PsiCallExpression callExpression) {
          return callExpression.resolveMethodGenerics();
        }
      }
      return JavaResolveResult.EMPTY;
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression referenceExpression) {
      if (calculateTypeForComplexReferences) {
        final Project project = referenceExpression.getProject();
        final JavaResolveResult resolveResult = referenceExpression.advancedResolve(false);
        final PsiElement element = resolveResult.getElement();
        PsiSubstitutor substitutor = resolveResult.getSubstitutor();
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        if (element instanceof PsiField field) {
          if (!isAccessibleFrom(field, referenceExpression)) {
            return;
          }
          final PsiClass aClass = field.getContainingClass();
          if (aClass == null) {
            return;
          }
          final PsiElementFactory factory = psiFacade.getElementFactory();
          expectedType = factory.createType(aClass, substitutor);
        }
        else if (element instanceof PsiMethod method) {
          final PsiElement parent = referenceExpression.getParent();
          final PsiType returnType;
          if (parent instanceof PsiMethodCallExpression methodCallExpression) {
            final PsiType type = methodCallExpression.getType();
            if (!PsiTypes.voidType().equals(type)) {
              returnType = findExpectedType(methodCallExpression, true);
            }
            else {
              returnType = null;
            }
          }
          else {
            returnType = null;
          }
          final PsiClass methodContainingClass = method.getContainingClass();
          if (methodContainingClass == null) {
            return;
          }
          final PsiMethod superMethod = findDeepestVisibleSuperMethod(method, returnType, referenceExpression);
          final PsiClass aClass;
          if (superMethod != null) {
            aClass = superMethod.getContainingClass();
            if (aClass == null) {
              return;
            }
            substitutor = TypeConversionUtil.getSuperClassSubstitutor(aClass, methodContainingClass, substitutor);
          }
          else {
            aClass = methodContainingClass;
          }
          final PsiElementFactory factory = psiFacade.getElementFactory();
          expectedType = factory.createType(aClass, substitutor);
        }
        else {
          expectedType = null;
        }
      }
    }

    private static @Nullable PsiMethod findDeepestVisibleSuperMethod(PsiMethod method, PsiType returnType, PsiElement element) {
      if (method.isConstructor()) {
        return null;
      }
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        return null;
      }
      if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return null;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return null;
      }
      final PsiReferenceList throwsList = method.getThrowsList();
      final HashSet<PsiClassType> thrownTypes = ContainerUtil.newHashSet(throwsList.getReferencedTypes());
      final PsiMethod[] superMethods = aClass.findMethodsBySignature(method, true);
      PsiMethod topSuper = null;
      PsiClass topSuperContainingClass = null;
      methodLoop:
      for (PsiMethod superMethod : superMethods) {
        final PsiClass superClass = superMethod.getContainingClass();
        if (superClass == null) {
          continue;
        }
        if (aClass.equals(superClass)) {
          continue;
        }
        if (!isAccessibleFrom(superMethod, element)) {
          continue;
        }
        if (returnType != null) {
          final PsiType superReturnType = superMethod.getReturnType();
          if (superReturnType == null) {
            continue;
          }
          if (!returnType.isAssignableFrom(superReturnType)) {
            continue;
          }
        }
        if (topSuper != null && superClass.isInheritor(topSuperContainingClass, true)) {
          continue;
        }
        final PsiReferenceList superThrowsList = superMethod.getThrowsList();
        final PsiClassType[] superThrownTypes = superThrowsList.getReferencedTypes();
        for (PsiClassType superThrownType : superThrownTypes) {
          if (!ExceptionUtil.isUncheckedException(superThrownType) && !thrownTypes.contains(superThrownType)) {
            continue methodLoop;
          }
        }
        topSuper = superMethod;
        topSuperContainingClass = superClass;
      }
      return topSuper;
    }

    private static boolean isAccessibleFrom(PsiMember member, PsiElement referencingLocation) {
      if (member.hasModifierProperty(PsiModifier.PUBLIC)) {
        return true;
      }
      final PsiClass containingClass = member.getContainingClass();
      if (containingClass == null) {
        return false;
      }
      final PsiClass referencingClass = PsiUtil.getContainingClass(referencingLocation);
      if (referencingClass == null) {
        return false;
      }
      if (referencingClass.equals(containingClass)) {
        return true;
      }
      if (member.hasModifierProperty(PsiModifier.PRIVATE)) {
        return false;
      }
      return ClassUtils.inSamePackage(containingClass, referencingLocation);
    }

    private static boolean isArithmeticOperation(@NotNull IElementType sign) {
      return arithmeticOps.contains(sign);
    }

    private static boolean isBooleanOperation(@NotNull IElementType sign) {
      return booleanOps.contains(sign);
    }

    private static boolean isShiftOperation(@NotNull IElementType sign) {
      return JavaBinaryOperations.SHIFT_OPS.contains(sign);
    }

    private static boolean isOperatorAssignmentOperation(@NotNull IElementType sign) {
      return operatorAssignmentOps.contains(sign);
    }

    private static int getParameterPosition(@NotNull PsiExpressionList expressionList, PsiExpression expression) {
      return ArrayUtil.indexOf(expressionList.getExpressions(), expression);
    }

    private static @Nullable PsiType getTypeOfParameter(@NotNull JavaResolveResult result, int parameterPosition) {
      if (parameterPosition < 0 ) {
        return null;
      }
      final PsiMethod method = (PsiMethod)result.getElement();
      if (method == null) {
        return null;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      if (parameters.length == 0) {
        return null;
      }
      final boolean isVarargs = result instanceof MethodCandidateInfo &&
                                ((MethodCandidateInfo)result).getApplicabilityLevel() == MethodCandidateInfo.ApplicabilityLevel.VARARGS;
      final PsiType parameterType = PsiTypesUtil.getParameterType(parameters, parameterPosition, isVarargs);
      final PsiSubstitutor substitutor = result.getSubstitutor();
      final PsiType type = GenericsUtil.getVariableTypeByExpressionType(substitutor.substitute(parameterType));
      if (type == null) {
        return null;
      }
      final TypeStringCreator typeStringCreator = new TypeStringCreator();
      type.accept(typeStringCreator);
      if (typeStringCreator.isModified()) {
        final PsiManager manager = method.getManager();
        final Project project = manager.getProject();
        final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        try {
          final String typeString = typeStringCreator.getTypeString();
          return factory.createTypeFromText(typeString, method);
        }
        catch (IncorrectOperationException e) {
          throw new AssertionError("incorrect type string generated from " + type + ": " + e.getMessage());
        }
      }
      return type;
    }

    /**
     * Creates a new type string without any wildcards with final
     * extends bounds from the visited type.
     */
    private static class TypeStringCreator extends PsiTypeVisitor<Object> {

      private final StringBuilder typeString = new StringBuilder();
      private boolean modified = false;

      @Override
      public Object visitType(@NotNull PsiType type) {
        typeString.append(type.getCanonicalText());
        return super.visitType(type);
      }

      @Override
      public Object visitWildcardType(@NotNull PsiWildcardType wildcardType) {
        if (wildcardType.isExtends()) {
          final PsiType extendsBound = wildcardType.getExtendsBound();
          if (extendsBound instanceof PsiClassType classType) {
            final PsiClass aClass = classType.resolve();
            if (aClass != null && aClass.hasModifierProperty(PsiModifier.FINAL)) {
              modified = true;
              return super.visitClassType(classType);
            }
          }
        }
        return super.visitWildcardType(wildcardType);
      }

      @Override
      public Object visitClassType(@NotNull PsiClassType classType) {
        final PsiClassType rawType = classType.rawType();
        typeString.append(rawType.getCanonicalText());
        final PsiType[] parameterTypes = classType.getParameters();
        if (parameterTypes.length > 0) {
          typeString.append('<');
          final PsiType parameterType1 = parameterTypes[0];
          // IDEADEV-25549 says this can be null
          if (parameterType1 != null) {
            parameterType1.accept(this);
          }
          for (int i = 1; i < parameterTypes.length; i++) {
            typeString.append(',');
            final PsiType parameterType = parameterTypes[i];
            // IDEADEV-25549 again
            if (parameterType != null) {
              parameterType.accept(this);
            }
          }
          typeString.append('>');
        }
        return null;
      }

      public String getTypeString() {
        return typeString.toString();
      }

      public boolean isModified() {
        return modified;
      }
    }
  }
}

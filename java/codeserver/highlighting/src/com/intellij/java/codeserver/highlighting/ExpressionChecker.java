// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.java.codeserver.highlighting.errors.JavaIncompatibleTypeErrorContext;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

final class ExpressionChecker {
  private final @NotNull JavaErrorVisitor myVisitor;

  ExpressionChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  void checkQualifiedNew(@NotNull PsiNewExpression expression, @Nullable PsiType type, @Nullable PsiClass aClass) {
    PsiExpression qualifier = expression.getQualifier();
    if (qualifier == null) return;
    if (type instanceof PsiArrayType) {
      myVisitor.report(JavaErrorKinds.NEW_EXPRESSION_QUALIFIED_MALFORMED.create(expression));
      return;
    }
    if (aClass == null) return;
    if (aClass.hasModifierProperty(PsiModifier.STATIC)) {
      myVisitor.report(JavaErrorKinds.NEW_EXPRESSION_QUALIFIED_STATIC_CLASS.create(expression, aClass));
      return;
    }
    if (aClass instanceof PsiAnonymousClass anonymousClass) {
      PsiClass baseClass = PsiUtil.resolveClassInType(anonymousClass.getBaseClassType());
      if (baseClass != null && baseClass.isInterface()) {
        myVisitor.report(JavaErrorKinds.NEW_EXPRESSION_QUALIFIED_ANONYMOUS_IMPLEMENTS_INTERFACE.create(expression, aClass));
        return;
      }
    }
    PsiJavaCodeReferenceElement reference = expression.getClassOrAnonymousClassReference();
    if (reference != null) {
      PsiElement refQualifier = reference.getQualifier();
      if (refQualifier != null) {
        myVisitor.report(JavaErrorKinds.NEW_EXPRESSION_QUALIFIED_QUALIFIED_CLASS_REFERENCE.create(refQualifier));
      }
    }
  }

  void checkCreateInnerClassFromStaticContext(@NotNull PsiNewExpression expression, @NotNull PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass anonymousClass) {
      aClass = anonymousClass.getBaseClassType().resolve();
      if (aClass == null) return;
    }

    PsiExpression qualifier = expression.getQualifier();
    PsiElement placeToSearchEnclosingFrom;
    if (qualifier != null) {
      placeToSearchEnclosingFrom = PsiUtil.resolveClassInType(qualifier.getType());
    }
    else {
      placeToSearchEnclosingFrom = expression;
    }
    if (placeToSearchEnclosingFrom == null) {
      return;
    }
    checkCreateInnerClassFromStaticContext(expression, placeToSearchEnclosingFrom, aClass);
  }

  void checkCreateInnerClassFromStaticContext(@NotNull PsiElement element,
                                              @NotNull PsiElement placeToSearchEnclosingFrom,
                                              @NotNull PsiClass aClass) {
    if (!PsiUtil.isInnerClass(aClass)) return;
    PsiClass outerClass = aClass.getContainingClass();
    if (outerClass == null) return;

    if (outerClass instanceof PsiSyntheticClass ||
        InheritanceUtil.hasEnclosingInstanceInScope(outerClass, placeToSearchEnclosingFrom, true, false)) {
      return;
    }
    checkIllegalEnclosingUsage(placeToSearchEnclosingFrom, aClass, outerClass, element);
  }

  void checkIllegalEnclosingUsage(@NotNull PsiElement place,
                                  @Nullable PsiClass aClass,
                                  @NotNull PsiClass outerClass,
                                  @NotNull PsiElement elementToHighlight) {
    var context = new JavaErrorKinds.ClassStaticReferenceErrorContext(outerClass, aClass, place);
    if (!PsiTreeUtil.isContextAncestor(outerClass, place, false)) {
      myVisitor.report(JavaErrorKinds.CLASS_NOT_ENCLOSING.create(elementToHighlight, context));
    }
    else if (context.enclosingStaticElement() != null) {
      myVisitor.report(JavaErrorKinds.CLASS_CANNOT_BE_REFERENCED_FROM_STATIC_CONTEXT.create(elementToHighlight, context));
    }
  }

  void checkAssignability(@Nullable PsiType lType,
                          @Nullable PsiType rType,
                          @Nullable PsiExpression expression,
                          @NotNull PsiElement elementToHighlight) {
    if (lType == rType) return;
    if (expression == null) {
      if (rType == null || lType == null || TypeConversionUtil.isAssignable(lType, rType)) return;
    }
    else if (TypeConversionUtil.areTypesAssignmentCompatible(lType, expression) || PsiTreeUtil.hasErrorElements(expression)) {
      return;
    }
    if (rType == null) {
      rType = expression.getType();
    }
    if (lType == null || lType == PsiTypes.nullType()) {
      return;
    }
    if (expression != null && IncompleteModelUtil.isIncompleteModel(expression) &&
        IncompleteModelUtil.isPotentiallyConvertible(lType, expression)) {
      return;
    }
    myVisitor.report(JavaErrorKinds.TYPE_INCOMPATIBLE.create(elementToHighlight, new JavaIncompatibleTypeErrorContext(lType, rType)));
  }

  void checkLocalClassReferencedFromAnotherSwitchBranch(@NotNull PsiJavaCodeReferenceElement ref, @NotNull PsiClass aClass) {
    if (!(aClass.getParent() instanceof PsiDeclarationStatement declarationStatement) ||
        !(declarationStatement.getParent() instanceof PsiCodeBlock codeBlock) ||
        !(codeBlock.getParent() instanceof PsiSwitchBlock)) {
      return;
    }
    boolean classSeen = false;
    for (PsiStatement statement : codeBlock.getStatements()) {
      if (classSeen) {
        if (PsiTreeUtil.isAncestor(statement, ref, true)) break;
        if (statement instanceof PsiSwitchLabelStatement) {
          myVisitor.report(JavaErrorKinds.REFERENCE_LOCAL_CLASS_OTHER_SWITCH_BRANCH.create(ref, aClass));
          return;
        }
      }
      else if (statement == declarationStatement) {
        classSeen = true;
      }
    }
  }

  void checkIllegalVoidType(@NotNull PsiKeyword type) {
    if (!PsiKeyword.VOID.equals(type.getText())) return;

    PsiElement parent = type.getParent();
    if (parent instanceof PsiErrorElement) return;
    if (parent instanceof PsiTypeElement) {
      PsiElement typeOwner = parent.getParent();
      if (typeOwner != null) {
        // do not highlight incomplete declarations
        if (PsiUtilCore.hasErrorElementChild(typeOwner)) return;
      }

      if (typeOwner instanceof PsiMethod method) {
        if (method.getReturnTypeElement() == parent && PsiTypes.voidType().equals(method.getReturnType())) return;
      }
      else if (typeOwner instanceof PsiClassObjectAccessExpression classAccess) {
        if (TypeConversionUtil.isVoidType(classAccess.getOperand().getType())) return;
      }
      else if (typeOwner instanceof JavaCodeFragment) {
        if (typeOwner.getUserData(PsiUtil.VALID_VOID_TYPE_IN_CODE_FRAGMENT) != null) return;
      }
    }
    myVisitor.report(JavaErrorKinds.TYPE_VOID_ILLEGAL.create(type));
  }

  void checkMustBeBoolean(@NotNull PsiExpression expr) {
    PsiElement parent = expr.getParent();
    if (parent instanceof PsiIfStatement ||
        parent instanceof PsiConditionalLoopStatement statement && expr.equals(statement.getCondition()) ||
        parent instanceof PsiConditionalExpression condExpr && condExpr.getCondition() == expr) {
      if (expr.getNextSibling() instanceof PsiErrorElement) return;

      PsiType type = expr.getType();
      if (!TypeConversionUtil.isBooleanType(type) && !PsiTreeUtil.hasErrorElements(expr)) {
        if (type == null &&
            IncompleteModelUtil.isIncompleteModel(expr) &&
            IncompleteModelUtil.mayHaveUnknownTypeDueToPendingReference(expr)) {
          return;
        }
        myVisitor.report(JavaErrorKinds.TYPE_INCOMPATIBLE.create(expr, new JavaIncompatibleTypeErrorContext(PsiTypes.booleanType(), type)));
      }
    }
  }

  void checkAssertOperatorTypes(@NotNull PsiExpression expression) {
    if (!(expression.getParent() instanceof PsiAssertStatement assertStatement)) return;
    PsiType type = expression.getType();
    if (type == null) return;
    if (expression == assertStatement.getAssertCondition() && !TypeConversionUtil.isBooleanType(type)) {
      myVisitor.report(JavaErrorKinds.TYPE_INCOMPATIBLE.create(
        expression, new JavaIncompatibleTypeErrorContext(PsiTypes.booleanType(), type)));
    }
    else if (expression == assertStatement.getAssertDescription() && TypeConversionUtil.isVoidType(type)) {
      myVisitor.report(JavaErrorKinds.TYPE_VOID_NOT_ALLOWED.create(expression));
    }
  }

  void checkSynchronizedExpressionType(@NotNull PsiExpression expression) {
    if (expression.getParent() instanceof PsiSynchronizedStatement synchronizedStatement &&
        expression == synchronizedStatement.getLockExpression()) {
      PsiType type = expression.getType();
      if (type == null) return;
      if (type instanceof PsiPrimitiveType || TypeConversionUtil.isNullType(type)) {
        PsiClassType objectType = PsiType.getJavaLangObject(myVisitor.file().getManager(), expression.getResolveScope());
        myVisitor.report(JavaErrorKinds.TYPE_INCOMPATIBLE.create(expression, new JavaIncompatibleTypeErrorContext(objectType, type)));
      }
    }
  }

  void checkArrayInitializer(@NotNull PsiExpression initializer, @NotNull PsiArrayInitializerExpression initializerList) {
    PsiType arrayType = initializerList.getType();
    if (!(arrayType instanceof PsiArrayType theArrayType)) return;
    PsiType componentType = theArrayType.getComponentType();
    PsiType initializerType = initializer.getType();
    checkArrayInitializerCompatibleTypes(initializer, initializerType, componentType);
  }

  private void checkArrayInitializerCompatibleTypes(@NotNull PsiExpression initializer,
                                                    @Nullable PsiType initializerType,
                                                    @NotNull PsiType componentType) {
    if (initializerType == null) {
      if (IncompleteModelUtil.isIncompleteModel(initializer) && IncompleteModelUtil.mayHaveUnknownTypeDueToPendingReference(initializer)) {
        return;
      }
      myVisitor.report(JavaErrorKinds.ARRAY_ILLEGAL_INITIALIZER.create(initializer, componentType));
    } else {
      PsiExpression expression = initializer instanceof PsiArrayInitializerExpression ? null : initializer;
      checkAssignability(componentType, initializerType, expression, initializer);
    }
  }

  void checkPatternVariableRequired(@NotNull PsiReferenceExpression expression, @NotNull JavaResolveResult resultForIncompleteCode) {
    if (!(expression.getParent() instanceof PsiCaseLabelElementList)) return;
    PsiClass resolved = tryCast(resultForIncompleteCode.getElement(), PsiClass.class);
    if (resolved == null) return;
    myVisitor.report(JavaErrorKinds.PATTERN_TYPE_PATTERN_EXPECTED.create(expression, resolved));
  }

  void checkExpressionRequired(@NotNull PsiReferenceExpression expression, @NotNull JavaResolveResult resultForIncompleteCode) {
    if (expression.getNextSibling() instanceof PsiErrorElement) return;

    PsiElement resolved = resultForIncompleteCode.getElement();
    if (resolved == null || resolved instanceof PsiVariable) return;

    PsiElement parent = expression.getParent();
    if (parent instanceof PsiReferenceExpression || parent instanceof PsiMethodCallExpression || parent instanceof PsiBreakStatement) {
      return;
    }
    myVisitor.report(JavaErrorKinds.EXPRESSION_EXPECTED.create(expression));
  }

  void checkArrayInitializerApplicable(@NotNull PsiArrayInitializerExpression expression) {
    /*
    JLS 10.6 Array Initializers
    An array initializer may be specified in a declaration, or as part of an array creation expression
    */
    PsiElement parent = expression.getParent();
    if (parent instanceof PsiVariable variable) {
      PsiTypeElement typeElement = variable.getTypeElement();
      boolean isInferredType = typeElement != null && typeElement.isInferredType();
      if (!isInferredType && variable.getType() instanceof PsiArrayType) return;
    }
    else if (parent instanceof PsiNewExpression || parent instanceof PsiArrayInitializerExpression) {
      return;
    }
    myVisitor.report(JavaErrorKinds.ARRAY_INITIALIZER_NOT_ALLOWED.create(expression));
  }

  void checkValidArrayAccessExpression(@NotNull PsiArrayAccessExpression arrayAccessExpression) {
    PsiExpression arrayExpression = arrayAccessExpression.getArrayExpression();
    PsiType arrayExpressionType = arrayExpression.getType();

    if (arrayExpressionType != null && !(arrayExpressionType instanceof PsiArrayType)) {
      myVisitor.report(JavaErrorKinds.ARRAY_TYPE_EXPECTED.create(arrayExpression, arrayExpressionType));
    }

    PsiExpression indexExpression = arrayAccessExpression.getIndexExpression();
    if (indexExpression != null) {
      checkAssignability(PsiTypes.intType(), indexExpression.getType(), indexExpression, indexExpression);
    }
  }

  /**
   * See JLS 8.3.3.
   */
  void checkIllegalForwardReferenceToField(@NotNull PsiReferenceExpression expression, @NotNull PsiField referencedField) {
    JavaPsiReferenceUtil.ForwardReferenceProblem problem = JavaPsiReferenceUtil.checkForwardReference(expression, referencedField, false);
    if (problem == JavaPsiReferenceUtil.ForwardReferenceProblem.LEGAL) return;
    var errorKind = referencedField instanceof PsiEnumConstant
                    ? (problem == JavaPsiReferenceUtil.ForwardReferenceProblem.ILLEGAL_FORWARD_REFERENCE
                       ? JavaErrorKinds.REFERENCE_ENUM_FORWARD
                       : JavaErrorKinds.REFERENCE_ENUM_SELF)
                    : (problem == JavaPsiReferenceUtil.ForwardReferenceProblem.ILLEGAL_FORWARD_REFERENCE
                       ? JavaErrorKinds.REFERENCE_FIELD_FORWARD
                       : JavaErrorKinds.REFERENCE_FIELD_SELF);
    myVisitor.report(errorKind.create(expression, referencedField));
  }

  void checkMethodCall(@NotNull PsiMethodCallExpression methodCall) {
    PsiExpressionList list = methodCall.getArgumentList();
    PsiReferenceExpression referenceToMethod = methodCall.getMethodExpression();
    JavaResolveResult[] results = referenceToMethod.multiResolve(true);
    JavaResolveResult resolveResult = results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
    PsiElement resolved = resolveResult.getElement();

    boolean isDummy = isDummyConstructorCall(methodCall, list, referenceToMethod);
    if (isDummy) return;

    PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    if (resolved instanceof PsiMethod psiMethod && resolveResult.isValidResult()) {
      
    }
    else {
      MethodCandidateInfo candidateInfo = resolveResult instanceof MethodCandidateInfo ? (MethodCandidateInfo)resolveResult : null;
      PsiMethod resolvedMethod = candidateInfo != null ? candidateInfo.getElement() : null;

      if (!resolveResult.isAccessible() || !resolveResult.isStaticsScopeCorrect()) {
      }
      else if (candidateInfo != null && !candidateInfo.isApplicable()) {

      }
      else {
        myVisitor.report(JavaErrorKinds.CALL_EXPECTED.create(methodCall));
      }
    }
  }

  boolean isDummyConstructorCall(@NotNull PsiMethodCallExpression methodCall,
                                        @NotNull PsiExpressionList list,
                                        @NotNull PsiReferenceExpression referenceToMethod) {
    boolean isDummy = false;
    boolean isThisOrSuper = referenceToMethod.getReferenceNameElement() instanceof PsiKeyword;
    if (isThisOrSuper) {
      // super(..) or this(..)
      if (list.isEmpty()) { // implicit ctr call
        CandidateInfo[] candidates = PsiResolveHelper.getInstance(myVisitor.project())
          .getReferencedMethodCandidates(methodCall, true);
        if (candidates.length == 1 && !candidates[0].getElement().isPhysical()) {
          isDummy = true;// dummy constructor
        }
      }
    }
    return isDummy;
  }
}

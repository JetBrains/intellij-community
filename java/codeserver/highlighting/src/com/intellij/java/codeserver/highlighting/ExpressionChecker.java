// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.core.JavaPsiBundle;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.java.codeserver.highlighting.errors.JavaIncompatibleTypeErrorContext;
import com.intellij.java.codeserver.highlighting.errors.JavaMismatchedCallContext;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
      checkUnhandledExceptions(methodCall);
      if (myVisitor.hasErrorResults()) return;
      if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass != null && containingClass.isInterface()) {
          PsiElement element = ObjectUtils.notNull(referenceToMethod.getReferenceNameElement(), referenceToMethod);
          myVisitor.checkFeature(element, JavaFeature.STATIC_INTERFACE_CALLS);
          if (myVisitor.hasErrorResults()) return;
          checkStaticInterfaceCallQualifier(referenceToMethod, resolveResult, containingClass);
        }
      }
      MethodCandidateInfo methodInfo = (MethodCandidateInfo)resolveResult;
      myVisitor.myGenericsChecker.checkInferredIntersections(substitutor, methodCall);
      if (myVisitor.hasErrorResults()) return;
      checkVarargParameterErasureToBeAccessible(methodInfo, methodCall);
      if (myVisitor.hasErrorResults()) return;
      checkIncompatibleType(methodCall, methodInfo, methodCall);
      if (myVisitor.hasErrorResults()) return;
      checkInferredReturnTypeAccessible(methodInfo, methodCall);
    }
    else {
      MethodCandidateInfo candidateInfo = resolveResult instanceof MethodCandidateInfo ? (MethodCandidateInfo)resolveResult : null;
      PsiMethod resolvedMethod = candidateInfo != null ? candidateInfo.getElement() : null;

      if (resolveResult.isAccessible() && resolveResult.isStaticsScopeCorrect()) {
        if (candidateInfo != null && !candidateInfo.isApplicable()) {
          if (candidateInfo.isTypeArgumentsApplicable()) {
            checkIncompatibleCall(list, candidateInfo);
          }
          else {
            PsiReferenceParameterList typeArgumentList = methodCall.getTypeArgumentList();
            PsiSubstitutor applicabilitySubstitutor = candidateInfo.getSubstitutor(false);
            if (typeArgumentList.getTypeArguments().length == 0 && resolvedMethod.hasTypeParameters()) {
              checkInferredTypeArguments(resolvedMethod, methodCall, applicabilitySubstitutor);
            }
            else {
              myVisitor.myGenericsChecker.checkParameterizedReferenceTypeArguments(resolved, referenceToMethod, applicabilitySubstitutor);
            }
          }
        }
        else {
          myVisitor.report(JavaErrorKinds.CALL_EXPECTED.create(methodCall));
        }
      }
    }
    if (!myVisitor.hasErrorResults()) {
      myVisitor.myGenericsChecker.checkParameterizedReferenceTypeArguments(resolved, referenceToMethod, substitutor);
    }
  }

  void checkInferredTypeArguments(@NotNull PsiTypeParameterListOwner listOwner,
                                  @NotNull PsiMethodCallExpression call,
                                  @NotNull PsiSubstitutor substitutor) {
    PsiTypeParameter[] typeParameters = listOwner.getTypeParameters();
    Pair<PsiTypeParameter, PsiType> inferredTypeArgument = GenericsUtil.findTypeParameterWithBoundError(
      typeParameters, substitutor, call, false);
    if (inferredTypeArgument != null) {
      PsiType extendsType = inferredTypeArgument.second;
      PsiTypeParameter typeParameter = inferredTypeArgument.first;
      PsiClass boundClass = extendsType instanceof PsiClassType classType ? classType.resolve() : null;

      var kind = boundClass == null || typeParameter.isInterface() == boundClass.isInterface()
                 ? JavaErrorKinds.TYPE_PARAMETER_INFERRED_TYPE_NOT_WITHIN_EXTEND_BOUND
                 : JavaErrorKinds.TYPE_PARAMETER_INFERRED_TYPE_NOT_WITHIN_IMPLEMENT_BOUND;
      myVisitor.report(kind.create(call, new JavaErrorKinds.TypeParameterBoundMismatchContext(
        typeParameter, extendsType, Objects.requireNonNull(substitutor.substitute(typeParameter)))));
    }
  }

  private void checkIncompatibleCall(@NotNull PsiExpressionList list, @NotNull MethodCandidateInfo candidateInfo) {
    if (PsiTreeUtil.hasErrorElements(list)) return;
    JavaMismatchedCallContext context = JavaMismatchedCallContext.create(list, candidateInfo);
    List<PsiExpression> mismatchedExpressions = context.mismatchedExpressions();
    if (mismatchedExpressions.isEmpty() && IncompleteModelUtil.isIncompleteModel(list)) return;

    if (mismatchedExpressions.size() == list.getExpressions().length || mismatchedExpressions.isEmpty()) {
      PsiElement anchor = list.getTextRange().isEmpty() ? ObjectUtils.notNull(list.getPrevSibling(), list) : list;
      myVisitor.report(JavaErrorKinds.CALL_WRONG_ARGUMENTS.create(anchor, context));
    }
    else {
      for (PsiExpression wrongArg : mismatchedExpressions) {
        myVisitor.report(JavaErrorKinds.CALL_WRONG_ARGUMENTS.create(wrongArg, context));
      }
    }
  }

  private void checkInferredReturnTypeAccessible(@NotNull MethodCandidateInfo info, @NotNull PsiMethodCallExpression methodCall) {
    PsiMethod method = info.getElement();
    PsiClass targetClass = PsiUtil.resolveClassInClassTypeOnly(method.getReturnType());
    if (targetClass instanceof PsiTypeParameter typeParameter && typeParameter.getOwner() == method) {
      PsiClass inferred = PsiUtil.resolveClassInClassTypeOnly(info.getSubstitutor().substitute(typeParameter));
      if (inferred != null && !PsiUtil.isAccessible(inferred, methodCall, null)) {
        myVisitor.report(JavaErrorKinds.TYPE_INACCESSIBLE.create(methodCall.getArgumentList(), inferred));
      }
    }
  }

  void checkTemplateExpression(@NotNull PsiTemplateExpression templateExpression) {
    myVisitor.checkFeature(templateExpression, JavaFeature.STRING_TEMPLATES);
    if (myVisitor.hasErrorResults()) return;
    PsiExpression processor = templateExpression.getProcessor();
    if (processor == null) {
      myVisitor.report(JavaErrorKinds.STRING_TEMPLATE_PROCESSOR_MISSING.create(templateExpression));
      return;
    }
    PsiType type = processor.getType();
    if (type == null) return;

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(processor.getProject());
    PsiClassType processorType = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_STRING_TEMPLATE_PROCESSOR, processor.getResolveScope());
    if (!TypeConversionUtil.isAssignable(processorType, type)) {
      if (IncompleteModelUtil.isIncompleteModel(templateExpression) && IncompleteModelUtil.isPotentiallyConvertible(processorType, processor)) {
        return;
      }
      myVisitor.report(JavaErrorKinds.TYPE_INCOMPATIBLE.create(processor, new JavaIncompatibleTypeErrorContext(processorType, type)));
      return;
    }

    PsiClass processorClass = processorType.resolve();
    if (processorClass == null) return;
    for (PsiClassType classType : PsiTypesUtil.getClassTypeComponents(type)) {
      if (!TypeConversionUtil.isAssignable(processorType, classType)) continue;
      PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      PsiClass aClass = resolveResult.getElement();
      if (aClass == null) continue;
      PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(processorClass, aClass, resolveResult.getSubstitutor());
      if (substitutor == null) continue;
      Map<PsiTypeParameter, PsiType> substitutionMap = substitutor.getSubstitutionMap();
      if (substitutionMap.isEmpty() || substitutionMap.containsValue(null)) {
        myVisitor.report(JavaErrorKinds.STRING_TEMPLATE_RAW_PROCESSOR.create(processor, type));
        return;
      }
    }
  }

  void checkNewExpression(@NotNull PsiNewExpression expression, PsiType type) {
    if (!(type instanceof PsiClassType classType)) return;
    PsiClassType.ClassResolveResult typeResult = classType.resolveGenerics();
    PsiClass aClass = typeResult.getElement();
    if (aClass == null) return;
    if (aClass instanceof PsiAnonymousClass anonymousClass) {
      classType = anonymousClass.getBaseClassType();
      typeResult = classType.resolveGenerics();
      aClass = typeResult.getElement();
      if (aClass == null) return;
    }

    PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
    checkConstructorCall(typeResult, expression, classType, classReference);
  }

  void checkAmbiguousConstructorCall(@NotNull PsiJavaCodeReferenceElement ref, PsiElement resolved) {
    if (resolved instanceof PsiClass psiClass &&
        ref.getParent() instanceof PsiNewExpression newExpression && psiClass.getConstructors().length > 0) {
      PsiExpressionList argumentList = newExpression.getArgumentList();
      if (newExpression.resolveMethod() == null && !PsiTreeUtil.findChildrenOfType(argumentList, PsiFunctionalExpression.class).isEmpty()) {
        PsiType type = newExpression.getType();
        if (type instanceof PsiClassType classType) {
          checkConstructorCall(classType.resolveGenerics(), newExpression, type, newExpression.getClassReference());
        }
      }
    }
  }

  void checkConstructorCallProblems(@NotNull PsiMethodCallExpression methodCall) {
    if (!JavaPsiConstructorUtil.isConstructorCall(methodCall)) return;
    PsiMethod method = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
    if (method == null || !method.isConstructor()) {
      myVisitor.report(JavaErrorKinds.CALL_CONSTRUCTOR_ONLY_ALLOWED_IN_CONSTRUCTOR.create(methodCall));
      return;
    }
    PsiMethodCallExpression constructorCall = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method);
    if (constructorCall != methodCall) {
      myVisitor.report(JavaErrorKinds.CALL_CONSTRUCTOR_DUPLICATE.create(methodCall));
      return;
    }
    PsiElement codeBlock = methodCall.getParent().getParent();
    if (!(codeBlock instanceof PsiCodeBlock) || !(codeBlock.getParent() instanceof PsiMethod)) {
      myVisitor.report(JavaErrorKinds.CALL_CONSTRUCTOR_MUST_BE_TOP_LEVEL_STATEMENT.create(methodCall));
      return;
    }
    if (JavaPsiRecordUtil.isCompactConstructor(method) || JavaPsiRecordUtil.isExplicitCanonicalConstructor(method)) {
      myVisitor.report(JavaErrorKinds.CALL_CONSTRUCTOR_RECORD_IN_CANONICAL.create(methodCall));
      return;
    }
    PsiStatement prevStatement = PsiTreeUtil.getPrevSiblingOfType(methodCall.getParent(), PsiStatement.class);
    if (prevStatement != null) {
      if (!myVisitor.isApplicable(JavaFeature.STATEMENTS_BEFORE_SUPER)) {
        myVisitor.report(JavaErrorKinds.CALL_CONSTRUCTOR_MUST_BE_FIRST_STATEMENT.create(methodCall));
        return;
      }
    }
    if (JavaPsiConstructorUtil.isChainedConstructorCall(methodCall) && JavaPsiConstructorUtil.isRecursivelyCalledConstructor(method)) {
      myVisitor.report(JavaErrorKinds.CALL_CONSTRUCTOR_RECURSIVE.create(methodCall));
    }
  }

  void checkSuperAbstractMethodDirectCall(@NotNull PsiMethodCallExpression methodCallExpression) {
    PsiReferenceExpression expression = methodCallExpression.getMethodExpression();
    if (!(expression.getQualifierExpression() instanceof PsiSuperExpression)) return;
    PsiMethod method = methodCallExpression.resolveMethod();
    if (method != null && method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      myVisitor.report(JavaErrorKinds.CALL_DIRECT_ABSTRACT_METHOD_ACCESS.create(methodCallExpression, method));
    }
  }

  private @Nullable PsiClass checkQualifier(@NotNull PsiQualifiedExpression expr) {
    PsiJavaCodeReferenceElement qualifier = expr.getQualifier();
    if (qualifier != null) {
      PsiElement resolved = qualifier.advancedResolve(true).getElement();
      if (resolved instanceof PsiClass cls) {
        return cls;
      }
      if (resolved != null) {
        myVisitor.report(JavaErrorKinds.EXPRESSION_QUALIFIED_CLASS_EXPECTED.create(qualifier));
      }
      return null;
    }
    return PsiUtil.getContainingClass(expr);
  }

  void checkThisExpressionInIllegalContext(@NotNull PsiThisExpression expr) {
    PsiClass aClass = checkQualifier(expr);
    if (aClass == null) return;

    if (!InheritanceUtil.hasEnclosingInstanceInScope(aClass, expr, false, false)) {
      checkIllegalEnclosingUsage(expr, null, aClass, expr);
    }
  }

  void checkSuperExpressionInIllegalContext(@NotNull PsiSuperExpression expr) {
    PsiJavaCodeReferenceElement qualifier = expr.getQualifier();
    PsiElement parent = expr.getParent();
    if (!(parent instanceof PsiReferenceExpression ref)) {
      // like in 'Object o = super;'
      myVisitor.report(JavaErrorKinds.EXPRESSION_SUPER_DOT_EXPECTED.create(expr));
      return;
    }

    PsiClass aClass = checkQualifier(expr);
    if (aClass == null) return;

    boolean extensionQualifier = qualifier != null && myVisitor.isApplicable(JavaFeature.EXTENSION_METHODS);

    if (!InheritanceUtil.hasEnclosingInstanceInScope(aClass, expr, false, false)) {
      boolean resolvesToImmediateSuperInterface =
        extensionQualifier && aClass.equals(PsiUtil.resolveClassInClassTypeOnly(expr.getType())) &&
        PsiUtil.getEnclosingStaticElement(expr, PsiUtil.getContainingClass(expr)) == null;
      if (!resolvesToImmediateSuperInterface) {
        checkIllegalEnclosingUsage(expr, null, aClass, expr);
        if (myVisitor.hasErrorResults()) return;
      }
      PsiElement resolved = ref.resolve();
      //15.11.2
      //The form T.super.Identifier refers to the field named Identifier of the lexically enclosing instance corresponding to T,
      //but with that instance viewed as an instance of the superclass of T.
      if (resolved instanceof PsiField) {
        myVisitor.report(JavaErrorKinds.EXPRESSION_SUPER_NOT_ENCLOSING_CLASS.create(expr, aClass));
      }
    }
    if (extensionQualifier && aClass.isInterface()) {
      //15.12.1 for method invocation expressions; 15.13 for method references
      //If TypeName denotes an interface, I, then let T be the type declaration immediately enclosing the method reference expression.
      //It is a compile-time error if I is not a direct superinterface of T,
      //or if there exists some other direct superclass or direct superinterface of T, J, such that J is a subtype of I.
      PsiClass classT = PsiUtil.getContainingClass(expr);
      if (classT != null) {
        PsiElement resolved = ref.resolve();

        PsiClass containingClass =
          ObjectUtils.notNull(resolved instanceof PsiMethod psiMethod ? psiMethod.getContainingClass() : null, aClass);
        for (PsiClass superClass : classT.getSupers()) {
          if (superClass.isInheritor(containingClass, true)) {
            if (superClass.isInheritor(aClass, true)) {
              myVisitor.report(JavaErrorKinds.EXPRESSION_SUPER_BAD_QUALIFIER_REDUNDANT_EXTENDED.create(
                qualifier, new JavaErrorKinds.SuperclassSubclassContext(superClass, containingClass)));
              return;
            }
            if (resolved instanceof PsiMethod psiMethod &&
                MethodSignatureUtil.findMethodBySuperMethod(superClass, psiMethod, true) != resolved) {
              myVisitor.report(JavaErrorKinds.EXPRESSION_SUPER_BAD_QUALIFIER_METHOD_OVERRIDDEN.create(expr, superClass));
              return;
            }
          }
        }

        if (!classT.isInheritor(aClass, false)) {
          myVisitor.report(JavaErrorKinds.EXPRESSION_SUPER_NO_ENCLOSING_INSTANCE.create(qualifier, aClass));
        }
      }
    }
  }

  void checkAssignmentCompatibleTypes(@NotNull PsiAssignmentExpression assignment) {
    PsiExpression lExpr = assignment.getLExpression();
    PsiExpression rExpr = assignment.getRExpression();
    if (rExpr == null) return;
    PsiType lType = lExpr.getType();
    PsiType rType = rExpr.getType();
    if (rType == null) return;

    IElementType sign = assignment.getOperationTokenType();
    if (JavaTokenType.EQ.equals(sign)) {
      checkAssignability(lType, rType, rExpr, rExpr);
    }
    else {
      // 15.26.2. Compound Assignment Operators
      IElementType opSign = TypeConversionUtil.convertEQtoOperation(sign);
      PsiType type = TypeConversionUtil.calcTypeForBinaryExpression(lType, rType, opSign, true);
      if (type == null || lType == null || lType instanceof PsiLambdaParameterType || type instanceof PsiLambdaParameterType ||
          TypeConversionUtil.areTypesConvertible(type, lType)) {
        return;
      }
      if (IncompleteModelUtil.isIncompleteModel(assignment) && IncompleteModelUtil.isPotentiallyConvertible(lType, rExpr)) {
        return;
      }
      myVisitor.report(JavaErrorKinds.TYPE_INCOMPATIBLE.create(rExpr, new JavaIncompatibleTypeErrorContext(lType, type)));
    }
  }

  void checkAssignmentOperatorApplicable(@NotNull PsiAssignmentExpression assignment) {
    PsiJavaToken operationSign = assignment.getOperationSign();
    IElementType eqOpSign = operationSign.getTokenType();
    IElementType opSign = TypeConversionUtil.convertEQtoOperation(eqOpSign);
    if (opSign == null) return;
    PsiType lType = assignment.getLExpression().getType();
    PsiExpression rExpression = assignment.getRExpression();
    if (rExpression == null) return;
    PsiType rType = rExpression.getType();
    if (!TypeConversionUtil.isBinaryOperatorApplicable(opSign, lType, rType, true)) {
      if (lType instanceof PsiLambdaParameterType || rType instanceof PsiLambdaParameterType) {
        // Unresolved lambda parameter type is used: an error for parameter should be more descriptive, so let's avoid reporting type error
        return;
      }
      myVisitor.report(JavaErrorKinds.BINARY_OPERATOR_NOT_APPLICABLE.create(
        operationSign, new JavaIncompatibleTypeErrorContext(lType, rType)));
    }
  }

  void checkUnaryOperatorApplicable(@NotNull PsiUnaryExpression unary) {
    PsiJavaToken token = unary.getOperationSign();
    PsiExpression operand = unary.getOperand();
    if (operand != null && !TypeConversionUtil.isUnaryOperatorApplicable(token, operand)) {
      PsiType type = operand.getType();
      if (type == null || type instanceof PsiLambdaParameterType) return;
      myVisitor.report(JavaErrorKinds.UNARY_OPERATOR_NOT_APPLICABLE.create(unary, type));
    }
  }

  void checkPolyadicOperatorApplicable(@NotNull PsiPolyadicExpression expression) {
    PsiExpression[] operands = expression.getOperands();

    PsiType lType = operands[0].getType();
    IElementType operationSign = expression.getOperationTokenType();
    for (int i = 1; i < operands.length; i++) {
      PsiExpression operand = operands[i];
      PsiType rType = operand.getType();
      if (lType instanceof PsiLambdaParameterType || rType instanceof PsiLambdaParameterType) return;
      if (!TypeConversionUtil.isBinaryOperatorApplicable(operationSign, lType, rType, false) &&
          !(IncompleteModelUtil.isIncompleteModel(expression) &&
            IncompleteModelUtil.isPotentiallyConvertible(lType, rType, expression))) {
        PsiJavaToken token = expression.getTokenBeforeOperand(operand);
        assert token != null : expression;
        myVisitor.report(JavaErrorKinds.BINARY_OPERATOR_NOT_APPLICABLE.create(token, new JavaIncompatibleTypeErrorContext(lType, rType)));
        return;
      }
      lType = TypeConversionUtil.calcTypeForBinaryExpression(lType, rType, operationSign, true);
    }
  }

  /**
   * JEP 440-441
   * Any variable that is used but not declared by a guard must either be final or effectively final (4.12.4)
   * and cannot be assigned to (15.26),
   * incremented (15.14.2), or decremented (15.14.3), otherwise a compile-time error occurs
   **/
  void checkOutsideDeclaredCantBeAssignmentInGuard(@Nullable PsiExpression expressionVariable) {
    if (expressionVariable == null) return;
    if (!PsiUtil.isAccessedForWriting(expressionVariable)) return;
    PsiSwitchLabelStatementBase label = PsiTreeUtil.getParentOfType(expressionVariable, PsiSwitchLabelStatementBase.class);
    if (label == null) return;
    PsiExpression guardingExpression = label.getGuardExpression();
    if (!PsiTreeUtil.isAncestor(guardingExpression, expressionVariable, false)) return;
    if (!(expressionVariable instanceof PsiReferenceExpression referenceExpression &&
          referenceExpression.resolve() instanceof PsiVariable psiVariable)) {
      return;
    }
    if (PsiTreeUtil.isAncestor(guardingExpression, psiVariable, false)) return;
    myVisitor.report(JavaErrorKinds.ASSIGNMENT_DECLARED_OUTSIDE_GUARD.create(expressionVariable, psiVariable));
  }

  void checkUnqualifiedSuperInDefaultMethod(@NotNull PsiReferenceExpression expr,
                                            @Nullable PsiExpression qualifier) {
    if (myVisitor.isApplicable(JavaFeature.EXTENSION_METHODS) && qualifier instanceof PsiSuperExpression superExpression) {
      PsiMethod method = PsiTreeUtil.getParentOfType(expr, PsiMethod.class);
      if (method != null && method.hasModifierProperty(PsiModifier.DEFAULT) && superExpression.getQualifier() == null) {
        myVisitor.report(JavaErrorKinds.EXPRESSION_SUPER_UNQUALIFIED_DEFAULT_METHOD.create(expr, superExpression));
      }
    }
  }

  private static boolean favorParentReport(@NotNull PsiCall methodCall, @NotNull String errorMessage) {
    // Parent resolve failed as well, and it's likely more informative.
    // Suppress this error to allow reporting from parent
    return (errorMessage.equals(JavaPsiBundle.message("error.incompatible.type.failed.to.resolve.argument")) ||
            errorMessage.equals(JavaPsiBundle.message("error.incompatible.type.declaration.for.the.method.reference.not.found"))) &&
           hasSurroundingInferenceError(methodCall);
  }

  static boolean hasSurroundingInferenceError(@NotNull PsiElement context) {
    PsiCall topCall = LambdaUtil.treeWalkUp(context);
    if (topCall == null) return false;
    while (context != topCall) {
      context = context.getParent();
      if (context instanceof PsiMethodCallExpression call &&
          call.resolveMethodGenerics() instanceof MethodCandidateInfo info &&
          info.getInferenceErrorMessage() != null) {
        // Possibly inapplicable method reference due to the surrounding call inference failure:
        // suppress method reference error in order to display more relevant inference error.
        return true;
      }
    }
    return false;
  }

  private void checkIncompatibleType(@NotNull PsiCall methodCall,
                                     @NotNull MethodCandidateInfo resolveResult,
                                     @NotNull PsiElement elementToHighlight) {
    String errorMessage = resolveResult.getInferenceErrorMessage();
    if (errorMessage == null) return;
    if (favorParentReport(methodCall, errorMessage)) return;
    PsiMethod method = resolveResult.getElement();
    PsiType expectedTypeByParent = InferenceSession.getTargetTypeByParent(methodCall);
    PsiType actualType =
      methodCall instanceof PsiExpression ? ((PsiExpression)methodCall.copy()).getType() :
      resolveResult.getSubstitutor(false).substitute(method.getReturnType());
    if (expectedTypeByParent != null && actualType != null && !expectedTypeByParent.isAssignableFrom(actualType)) {
      myVisitor.report(JavaErrorKinds.TYPE_INCOMPATIBLE.create(
        elementToHighlight, new JavaIncompatibleTypeErrorContext(expectedTypeByParent, actualType, errorMessage)));
    }
    else {
      myVisitor.report(JavaErrorKinds.CALL_TYPE_INFERENCE_ERROR.create(methodCall, errorMessage));
    }
  }

  /**
   * If the compile-time declaration is applicable by variable arity invocation,
   * then where the last formal parameter type of the invocation type of the method is Fn[],
   * it is a compile-time error if the type which is the erasure of Fn is not accessible at the point of invocation.
   */
  private void checkVarargParameterErasureToBeAccessible(@NotNull MethodCandidateInfo info, @NotNull PsiCall place) {
    PsiMethod method = info.getElement();
    if (info.isVarargs() || method.isVarArgs() && !PsiUtil.isLanguageLevel8OrHigher(place)) {
      PsiParameter[] parameters = method.getParameterList().getParameters();
      PsiType componentType = ((PsiEllipsisType)parameters[parameters.length - 1].getType()).getComponentType();
      PsiType substitutedTypeErasure = TypeConversionUtil.erasure(info.getSubstitutor().substitute(componentType));
      PsiClass targetClass = PsiUtil.resolveClassInClassTypeOnly(substitutedTypeErasure);
      if (targetClass != null && !PsiUtil.isAccessible(targetClass, place, null)) {
        myVisitor.report(JavaErrorKinds.CALL_FORMAL_VARARGS_ELEMENT_TYPE_INACCESSIBLE_HERE.create(place, targetClass));
      }
    }
  }

  private void checkStaticInterfaceCallQualifier(@NotNull PsiReferenceExpression referenceToMethod,
                                                 @NotNull JavaResolveResult resolveResult,
                                                 @NotNull PsiClass containingClass) {
    PsiElement scope = resolveResult.getCurrentFileResolveScope();
    PsiElement qualifierExpression = referenceToMethod.getQualifier();
    if (qualifierExpression == null && PsiTreeUtil.isAncestor(containingClass, referenceToMethod, true)) return;
    PsiElement resolve = null;
    if (qualifierExpression == null && scope instanceof PsiImportStaticStatement statement) {
      resolve = statement.resolveTargetClass();
    }
    else if (qualifierExpression instanceof PsiJavaCodeReferenceElement element) {
      resolve = element.resolve();
    }
    if (containingClass.getManager().areElementsEquivalent(resolve, containingClass)) return;
    if (resolve instanceof PsiTypeParameter typeParameter) {
      Set<PsiClass> classes = new HashSet<>();
      for (PsiClassType type : typeParameter.getExtendsListTypes()) {
        PsiClass aClass = type.resolve();
        if (aClass != null) {
          classes.add(aClass);
        }
      }

      if (classes.size() == 1 && classes.contains(containingClass)) return;
    }

    myVisitor.report(JavaErrorKinds.CALL_STATIC_INTERFACE_METHOD_QUALIFIER.create(referenceToMethod));
  }

  private static boolean shouldHighlightUnhandledException(@NotNull PsiElement element) {
    // JSP top-level errors are handled by UnhandledExceptionInJSP inspection
    if (FileTypeUtils.isInServerPageFile(element)) {
      PsiMethod targetMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class, true, PsiLambdaExpression.class);
      if (targetMethod instanceof SyntheticElement) {
        return false;
      }
    }

    return true;
  }

  void checkUnhandledExceptions(@NotNull PsiElement element) {
    List<PsiClassType> unhandled = ExceptionUtil.getOwnUnhandledExceptions(element);
    if (unhandled.isEmpty()) return;
    unhandled = ContainerUtil.filter(unhandled, type -> type.resolve() != null);
    if (unhandled.isEmpty()) return;

    if (!shouldHighlightUnhandledException(element)) return;

    myVisitor.report(JavaErrorKinds.EXCEPTION_UNHANDLED.create(element, unhandled));
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

  void checkConstructorCall(@NotNull PsiClassType.ClassResolveResult typeResolveResult,
                            @NotNull PsiConstructorCall constructorCall,
                            @NotNull PsiType type,
                            @Nullable PsiJavaCodeReferenceElement classReference) {
    PsiExpressionList list = constructorCall.getArgumentList();
    if (list == null) return;
    PsiClass aClass = typeResolveResult.getElement();
    if (aClass == null) return;
    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(myVisitor.project()).getResolveHelper();
    PsiClass accessObjectClass = null;
    if (constructorCall instanceof PsiNewExpression newExpression) {
      PsiExpression qualifier = newExpression.getQualifier();
      if (qualifier != null) {
        accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement();
      }
    }
    if (classReference != null && !resolveHelper.isAccessible(aClass, constructorCall, accessObjectClass)) {
      myVisitor.myModifierChecker.reportAccessProblem(classReference, aClass, typeResolveResult);
    }
    PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) {
      if (!list.isEmpty()) {
        myVisitor.report(JavaErrorKinds.NEW_EXPRESSION_ARGUMENTS_TO_DEFAULT_CONSTRUCTOR_CALL.create(constructorCall));
      }
      else if (classReference != null && aClass.hasModifierProperty(PsiModifier.PROTECTED) &&
               callingProtectedConstructorFromDerivedClass(constructorCall, aClass)) {
        myVisitor.myModifierChecker.reportAccessProblem(classReference, aClass, typeResolveResult);
      }
      else if (aClass.isInterface() && constructorCall instanceof PsiNewExpression newExpression) {
        PsiReferenceParameterList typeArgumentList = newExpression.getTypeArgumentList();
        if (typeArgumentList.getTypeArguments().length > 0) {
          myVisitor.report(JavaErrorKinds.NEW_EXPRESSION_ANONYMOUS_IMPLEMENTS_INTERFACE_WITH_TYPE_ARGUMENTS.create(typeArgumentList));
        }
      }
      return;
    }
    PsiElement place = list;
    if (constructorCall instanceof PsiNewExpression newExpression) {
      PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
      if (anonymousClass != null) place = anonymousClass;
    }

    JavaResolveResult[] results = resolveHelper.multiResolveConstructor((PsiClassType)type, list, place);
    MethodCandidateInfo result = null;
    if (results.length == 1) result = (MethodCandidateInfo)results[0];

    PsiMethod constructor = result == null ? null : result.getElement();

    boolean applicable = true;
    try {
      PsiDiamondType diamondType =
        constructorCall instanceof PsiNewExpression newExpression ? PsiDiamondType.getDiamondType(newExpression) : null;
      JavaResolveResult staticFactory = diamondType != null ? diamondType.getStaticFactory() : null;
      if (staticFactory instanceof MethodCandidateInfo info) {
        if (info.isApplicable()) {
          result = info;
          if (constructor == null) {
            constructor = info.getElement();
          }
        }
        else {
          applicable = false;
        }
      }
      else {
        applicable = result != null && result.isApplicable();
      }
    }
    catch (IndexNotReadyException ignored) {
    }
    if (constructor == null) {
      if (IncompleteModelUtil.isIncompleteModel(list) &&
          ContainerUtil.exists(results, r -> r instanceof MethodCandidateInfo info && info.isPotentiallyCompatible() == ThreeState.YES) &&
          ContainerUtil.exists(list.getExpressions(), e -> IncompleteModelUtil.mayHaveUnknownTypeDueToPendingReference(e))) {
        return;
      }
      myVisitor.report(JavaErrorKinds.NEW_EXPRESSION_UNRESOLVED_CONSTRUCTOR.create(
        constructorCall, new JavaErrorKinds.UnresolvedConstructorContext(aClass, results)));
      return;
    }
    if (classReference != null &&
        (!result.isAccessible() ||
         constructor.hasModifierProperty(PsiModifier.PROTECTED) && callingProtectedConstructorFromDerivedClass(constructorCall, aClass))) {
      myVisitor.myModifierChecker.reportAccessProblem(classReference, constructor, result);
      return;
    }
    if (!applicable) {
      checkIncompatibleCall(list, result);
      if (myVisitor.hasErrorResults()) return;
    }
    else if (constructorCall instanceof PsiNewExpression newExpression) {
      PsiReferenceParameterList typeArgumentList = newExpression.getTypeArgumentList();
      myVisitor.myGenericsChecker.checkReferenceTypeArgumentList(constructor, typeArgumentList, result.getSubstitutor());
      if (myVisitor.hasErrorResults()) return;
    }
    checkVarargParameterErasureToBeAccessible(result, constructorCall);
    if (myVisitor.hasErrorResults()) return;
    checkIncompatibleType(constructorCall, result, constructorCall);
  }

  private static boolean callingProtectedConstructorFromDerivedClass(@NotNull PsiConstructorCall place,
                                                                     @NotNull PsiClass constructorClass) {
    // indirect instantiation via anonymous class is ok
    if (place instanceof PsiNewExpression newExpression && newExpression.getAnonymousClass() != null) return false;
    PsiElement curElement = place;
    PsiClass containingClass = constructorClass.getContainingClass();
    while (true) {
      PsiClass aClass = PsiTreeUtil.getParentOfType(curElement, PsiClass.class);
      if (aClass == null) return false;
      curElement = aClass;
      if ((aClass.isInheritor(constructorClass, true) || containingClass != null && aClass.isInheritor(containingClass, true))
          && !JavaPsiFacade.getInstance(aClass.getProject()).arePackagesTheSame(aClass, constructorClass)) {
        return true;
      }
    }
  }
}

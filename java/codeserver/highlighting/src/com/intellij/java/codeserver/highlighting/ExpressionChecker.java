// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.core.JavaPsiBundle;
import com.intellij.java.codeserver.core.JavaPsiReferenceUtil;
import com.intellij.java.codeserver.highlighting.errors.*;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ImplicitClassSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

final class ExpressionChecker {
  private final @NotNull JavaErrorVisitor myVisitor;
  private final Map<PsiElement, PsiMethod> myInsideConstructorOfClassCache = new HashMap<>(); // null value means "cached but no corresponding ctr found"

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
    if (aClass.hasModifierProperty(PsiModifier.STATIC)) return;
    PsiClass outerClass = aClass.getContainingClass();
    if (outerClass == null) {
      if (!(aClass.getParent() instanceof PsiDeclarationStatement)) return; // local class
      PsiMember scope = PsiTreeUtil.getParentOfType(aClass, PsiMember.class);
      if (scope == null) return;
      if (scope.hasModifierProperty(PsiModifier.STATIC)) {
        PsiModifierListOwner enclosingStaticElement = PsiUtil.getEnclosingStaticElement(element, null);
        assert enclosingStaticElement != null;
        if (enclosingStaticElement != scope) {
          myVisitor.report(JavaErrorKinds.INSTANTIATION_LOCAL_CLASS_WRONG_STATIC_CONTEXT.create(element, aClass));
        }
        return;
      }
      outerClass = scope.getContainingClass();
      if (outerClass == null) return;
    }

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

  /**
   * @return true if there's no assignability problem reported
   */
  boolean checkAssignability(@Nullable PsiType lType,
                             @Nullable PsiType rType,
                             @Nullable PsiExpression expression,
                             @NotNull PsiElement elementToHighlight) {
    if (lType == rType) return true;
    if (expression == null) {
      if (rType == null || lType == null || TypeConversionUtil.isAssignable(lType, rType)) return true;
    }
    else if (TypeConversionUtil.areTypesAssignmentCompatible(lType, expression) || PsiTreeUtil.hasErrorElements(expression)) {
      return true;
    }
    if (rType == null) {
      rType = expression.getType();
    }
    if (lType == null || lType == PsiTypes.nullType()) {
      return true;
    }
    if (expression != null && myVisitor.isIncompleteModel() && IncompleteModelUtil.isPotentiallyConvertible(lType, expression)) {
      return true;
    }
    return myVisitor.reportIncompatibleType(lType, rType, elementToHighlight);
  }

  void checkMustBeBoolean(@NotNull PsiExpression expr) {
    PsiElement parent = expr.getParent();
    if (parent instanceof PsiIfStatement ||
        parent instanceof PsiConditionalLoopStatement statement && expr.equals(statement.getCondition()) ||
        parent instanceof PsiConditionalExpression condExpr && condExpr.getCondition() == expr) {
      if (expr.getNextSibling() instanceof PsiErrorElement) return;

      PsiType type = expr.getType();
      if (!TypeConversionUtil.isBooleanType(type) && !PsiTreeUtil.hasErrorElements(expr)) {
        if (type == null && myVisitor.isIncompleteModel() && IncompleteModelUtil.mayHaveUnknownTypeDueToPendingReference(expr)) {
          return;
        }
        myVisitor.reportIncompatibleType(PsiTypes.booleanType(), type, expr);
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
      if (myVisitor.isIncompleteModel() && IncompleteModelUtil.mayHaveUnknownTypeDueToPendingReference(initializer)) return;
      myVisitor.report(JavaErrorKinds.ARRAY_ILLEGAL_INITIALIZER.create(initializer, componentType));
    } else {
      PsiExpression expression = initializer instanceof PsiArrayInitializerExpression ? null : initializer;
      checkAssignability(componentType, initializerType, expression, initializer);
    }
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
        typeParameter, extendsType, requireNonNull(substitutor.substitute(typeParameter)))));
    }
  }

  private void checkIncompatibleCall(@NotNull PsiExpressionList list, @NotNull MethodCandidateInfo candidateInfo) {
    if (PsiTreeUtil.hasErrorElements(list)) return;
    JavaMismatchedCallContext context = JavaMismatchedCallContext.create(list, candidateInfo);
    List<PsiExpression> mismatchedExpressions = context.mismatchedExpressions();
    if (mismatchedExpressions.isEmpty() && myVisitor.isIncompleteModel()) return;

    if (mismatchedExpressions.size() == list.getExpressions().length || mismatchedExpressions.isEmpty()) {
      PsiElement anchor = list.getTextRange().isEmpty() ? ObjectUtils.notNull(list.getPrevSibling(), list) : list;
      if (!mismatchedExpressions.isEmpty() && !context.argCountMismatch() &&
          ContainerUtil.and(mismatchedExpressions, e -> e.getType() instanceof PsiLambdaParameterType)) {
        // Likely, the problem is induced
        return;
      }
      myVisitor.report(JavaErrorKinds.CALL_WRONG_ARGUMENTS.create(anchor, context));
    }
    else {
      for (PsiExpression wrongArg : mismatchedExpressions) {
        if (wrongArg.getType() instanceof PsiLambdaParameterType) continue;
        myVisitor.report(JavaErrorKinds.CALL_WRONG_ARGUMENTS.create(wrongArg, context));
      }
    }
  }

  private void checkInferredReturnTypeAccessible(@NotNull MethodCandidateInfo info, @NotNull PsiMethodCallExpression methodCall) {
    PsiMethod method = info.getElement();
    PsiClass targetClass = PsiUtil.resolveClassInClassTypeOnly(method.getReturnType());
    if (targetClass instanceof PsiTypeParameter typeParameter && typeParameter.getOwner() == method) {
      PsiClass targetType = PsiUtil.resolveClassInClassTypeOnly(InferenceSession.getTargetTypeByParent(methodCall));
      if (targetType != null && !PsiUtil.isAccessible(targetType, methodCall, null)) {
        myVisitor.report(JavaErrorKinds.TYPE_INACCESSIBLE.create(methodCall.getArgumentList(), targetType));
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

    PsiElementFactory factory = myVisitor.factory();
    PsiClassType processorType = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_STRING_TEMPLATE_PROCESSOR, processor.getResolveScope());
    if (!TypeConversionUtil.isAssignable(processorType, type)) {
      if (myVisitor.isIncompleteModel() && IncompleteModelUtil.isPotentiallyConvertible(processorType, processor)) return;
      myVisitor.reportIncompatibleType(processorType, type, processor);
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
    checkConstructorCall(typeResult, expression, classReference);
  }

  void checkAmbiguousConstructorCall(@NotNull PsiJavaCodeReferenceElement ref, PsiElement resolved) {
    if (resolved instanceof PsiClass psiClass &&
        ref.getParent() instanceof PsiNewExpression newExpression && psiClass.getConstructors().length > 0) {
      PsiExpressionList argumentList = newExpression.getArgumentList();
      if (newExpression.resolveMethod() == null && !PsiTreeUtil.findChildrenOfType(argumentList, PsiFunctionalExpression.class).isEmpty()) {
        PsiType type = newExpression.getType();
        if (type instanceof PsiClassType classType) {
          checkConstructorCall(classType.resolveGenerics(), newExpression, newExpression.getClassReference());
        }
      }
    }
  }

  void checkConstructorCallProblems(@NotNull PsiMethodCallExpression methodCall) {
    if (!JavaPsiConstructorUtil.isConstructorCall(methodCall)) return;
    PsiMethod method = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
    PsiMethod contextMethod =
      method != null ? method : PsiTreeUtil.getContextOfType(methodCall, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
    if (contextMethod == null || !contextMethod.isConstructor()) {
      myVisitor.report(JavaErrorKinds.CALL_CONSTRUCTOR_ONLY_ALLOWED_IN_CONSTRUCTOR.create(methodCall));
      return;
    }
    if (JavaPsiRecordUtil.isCompactConstructor(contextMethod) || JavaPsiRecordUtil.isExplicitCanonicalConstructor(contextMethod)) {
      myVisitor.report(JavaErrorKinds.CALL_CONSTRUCTOR_RECORD_IN_CANONICAL.create(methodCall));
      return;
    }
    if (method == null) {
      // Do not report other errors for detached constructor call, as they are likely non-relevant
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
      if (myVisitor.isIncompleteModel() && IncompleteModelUtil.isPotentiallyConvertible(lType, rExpr)) return;
      myVisitor.reportIncompatibleType(lType, type, rExpr);
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
          !(myVisitor.isIncompleteModel() && IncompleteModelUtil.isPotentiallyConvertible(lType, rType, expression))) {
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

  void checkUnderscore(@NotNull PsiIdentifier identifier) {
    if ("_".equals(identifier.getText())) {
      PsiElement parent = identifier.getParent();
      LanguageLevel languageLevel = myVisitor.languageLevel();
      if (languageLevel.isAtLeast(LanguageLevel.JDK_1_9) && !(parent instanceof PsiUnnamedPattern) &&
          !(parent instanceof PsiVariable var && var.isUnnamed())) {
        JavaErrorKind.Simple<PsiIdentifier> text = myVisitor.isApplicable(JavaFeature.UNNAMED_PATTERNS_AND_VARIABLES) ?
                                                   JavaErrorKinds.UNDERSCORE_IDENTIFIER_UNNAMED :
                                                   JavaErrorKinds.UNDERSCORE_IDENTIFIER;
        myVisitor.report(text.create(identifier));
      }
      else if (myVisitor.isApplicable(JavaFeature.LAMBDA_EXPRESSIONS)) {
        if (parent instanceof PsiParameter parameter && parameter.getDeclarationScope() instanceof PsiLambdaExpression &&
            !parameter.isUnnamed()) {
          myVisitor.report(JavaErrorKinds.UNDERSCORE_IDENTIFIER_LAMBDA.create(identifier));
        }
      }
    }
  }

  void checkUnhandledCloserExceptions(@NotNull PsiResourceListElement resource) {
    List<PsiClassType> unhandled = ExceptionUtil.getUnhandledCloserExceptions(resource, null);
    if (unhandled.isEmpty()) return;

    if (!shouldHighlightUnhandledException(resource)) return;
    myVisitor.report(JavaErrorKinds.EXCEPTION_UNHANDLED_CLOSE.create(resource, unhandled));
  }

  void checkVarTypeSelfReferencing(@NotNull PsiLocalVariable variable, @NotNull PsiReferenceExpression ref) {
    if (PsiTreeUtil.isAncestor(variable.getInitializer(), ref, false) && variable.getTypeElement().isInferredType()) {
      myVisitor.report(JavaErrorKinds.LVTI_SELF_REFERENCED.create(ref, variable));
    }
  }

  void checkVariableExpected(@NotNull PsiExpression expression) {
    PsiExpression lValue;
    if (expression instanceof PsiAssignmentExpression assignment) {
      lValue = assignment.getLExpression();
    }
    else if (PsiUtil.isIncrementDecrementOperation(expression)) {
      lValue = ((PsiUnaryExpression)expression).getOperand();
    }
    else {
      lValue = null;
    }
    if (lValue != null && !TypeConversionUtil.isLValue(lValue) && !PsiTreeUtil.hasErrorElements(expression) &&
        !(myVisitor.isIncompleteModel() &&
          PsiUtil.skipParenthesizedExprDown(lValue) instanceof PsiReferenceExpression ref &&
          IncompleteModelUtil.canBePendingReference(ref))) {
      myVisitor.report(JavaErrorKinds.LVALUE_VARIABLE_EXPECTED.create(lValue));
    }
  }

  void checkInconvertibleTypeCast(@NotNull PsiTypeCastExpression expression) {
    PsiTypeElement castTypeElement = expression.getCastType();
    if (castTypeElement == null) return;
    PsiType castType = castTypeElement.getType();

    PsiExpression operand = expression.getOperand();
    if (operand == null) return;
    PsiType operandType = operand.getType();

    if (operandType != null &&
        !TypeConversionUtil.areTypesConvertible(operandType, castType, PsiUtil.getLanguageLevel(expression)) &&
        !PsiUtil.isInSignaturePolymorphicCall(expression)) {
      if (myVisitor.isIncompleteModel() && IncompleteModelUtil.isPotentiallyConvertible(castType, operand)) return;
      myVisitor.report(JavaErrorKinds.CAST_INCONVERTIBLE.create(
        expression, new JavaIncompatibleTypeErrorContext(operandType, castType)));
    }
  }

  /**
   * 15.16 Cast Expressions
   * ( ReferenceType {AdditionalBound} ) expression, where AdditionalBound: & InterfaceType then all must be true
   * - ReferenceType must denote a class or interface type.
   * - The erasures of all the listed types must be pairwise different.
   * - No two listed types may be subtypes of different parameterization of the same generic interface.
   */
  void checkIntersectionInTypeCast(@NotNull PsiTypeCastExpression expression) {
    PsiTypeElement castTypeElement = expression.getCastType();
    if (castTypeElement == null || !isIntersection(castTypeElement, castTypeElement.getType())) return;
    myVisitor.checkFeature(expression, JavaFeature.INTERSECTION_CASTS);
    if (myVisitor.hasErrorResults()) return;

    PsiTypeElement[] conjuncts = PsiTreeUtil.getChildrenOfType(castTypeElement, PsiTypeElement.class);
    if (conjuncts != null) {
      Set<PsiType> erasures = new HashSet<>(conjuncts.length);
      erasures.add(TypeConversionUtil.erasure(conjuncts[0].getType()));
      List<PsiTypeElement> conjList = new ArrayList<>(Arrays.asList(conjuncts));
      for (int i = 1; i < conjuncts.length; i++) {
        PsiTypeElement conjunct = conjuncts[i];
        PsiType conjType = conjunct.getType();
        if (conjType instanceof PsiClassType classType) {
          PsiClass aClass = classType.resolve();
          if (aClass != null && !aClass.isInterface()) {
            myVisitor.report(JavaErrorKinds.CAST_INTERSECTION_NOT_INTERFACE.create(conjunct));
            continue;
          }
        }
        else {
          myVisitor.report(JavaErrorKinds.CAST_INTERSECTION_UNEXPECTED_TYPE.create(conjunct));
          continue;
        }
        if (!erasures.add(TypeConversionUtil.erasure(conjType))) {
          myVisitor.report(JavaErrorKinds.CAST_INTERSECTION_REPEATED_INTERFACE.create(conjunct));
        }
      }
      if (myVisitor.hasErrorResults()) return;

      List<PsiType> typeList = ContainerUtil.map(conjList, PsiTypeElement::getType);
      Ref<Pair<PsiType, PsiType>> differentArguments = new Ref<>();
      PsiClass sameGenericParameterization =
        InferenceSession.findParameterizationOfTheSameGenericClass(typeList, pair -> {
          if (!TypesDistinctProver.provablyDistinct(pair.first, pair.second)) {
            return true;
          }
          differentArguments.set(pair);
          return false;
        });
      if (differentArguments.get() != null && sameGenericParameterization != null) {
        var context = new JavaErrorKinds.InheritTypeClashContext(
          sameGenericParameterization, differentArguments.get().getFirst(), differentArguments.get().getSecond());
        myVisitor.report(JavaErrorKinds.CAST_INTERSECTION_INHERITANCE_CLASH.create(expression, context));
      }
    }
  }

  void checkResourceVariableIsFinal(@NotNull PsiResourceExpression resource) {
    PsiExpression expression = resource.getExpression();

    if (expression instanceof PsiThisExpression) return;

    if (expression instanceof PsiReferenceExpression ref) {
      PsiElement target = ref.resolve();
      if (target == null) return;

      if (target instanceof PsiVariable variable) {
        PsiModifierList modifierList = variable.getModifierList();
        if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.FINAL)) return;

        if (!(variable instanceof PsiField) && ControlFlowUtil.isEffectivelyFinal(variable, resource)) return;
      }

      myVisitor.report(JavaErrorKinds.VARIABLE_MUST_BE_FINAL_RESOURCE.create(ref));
      return;
    }

    myVisitor.report(JavaErrorKinds.RESOURCE_DECLARATION_OR_VARIABLE_EXPECTED.create(expression));
  }

  void checkClassReferenceAfterQualifier(@NotNull PsiReferenceExpression expression, @Nullable PsiElement resolved) {
    if (!(resolved instanceof PsiClass psiClass)) return;
    PsiExpression qualifier = expression.getQualifierExpression();
    if (qualifier == null) return;
    if (qualifier instanceof PsiReferenceExpression qExpression) {
      PsiElement qualifierResolved = qExpression.resolve();
      if (qualifierResolved instanceof PsiClass || qualifierResolved instanceof PsiPackage) return;

      if (qualifierResolved == null) {
        while (true) {
          PsiElement qResolve = qExpression.resolve();
          if (qResolve == null || qResolve instanceof PsiClass || qResolve instanceof PsiPackage) {
            PsiExpression qualifierExpression = qExpression.getQualifierExpression();
            if (qualifierExpression == null) return;
            if (qualifierExpression instanceof PsiReferenceExpression ref) {
              qExpression = ref;
              continue;
            }
          }
          break;
        }
      }
    }
    myVisitor.report(JavaErrorKinds.CLASS_OR_PACKAGE_EXPECTED.create(expression, psiClass));
  }

  private static boolean isIntersection(@NotNull PsiTypeElement castTypeElement, @NotNull PsiType castType) {
    if (castType instanceof PsiIntersectionType) return true;
    return castType instanceof PsiClassType && PsiTreeUtil.getChildrenOfType(castTypeElement, PsiTypeElement.class) != null;
  }

  static boolean isArrayDeclaration(@NotNull PsiVariable variable) {
    // Java-style 'var' arrays are prohibited by the parser; for C-style ones, looking for a bracket is enough
    return ContainerUtil.or(variable.getChildren(), e -> PsiUtil.isJavaToken(e, JavaTokenType.LBRACKET));
  }

  void checkUnnamedVariableDeclaration(@NotNull PsiVariable variable) {
    if (isArrayDeclaration(variable)) {
      myVisitor.report(JavaErrorKinds.UNNAMED_VARIABLE_BRACKETS.create(variable));
      return;
    }
    if (variable instanceof PsiPatternVariable) return;
    if (variable instanceof PsiResourceVariable) return;
    if (variable instanceof PsiLocalVariable local) {
      if (local.getInitializer() == null) {
        myVisitor.report(JavaErrorKinds.UNNAMED_VARIABLE_WITHOUT_INITIALIZER.create(local));
      }
    }
    else if (variable instanceof PsiParameter parameter) {
      if (parameter.getDeclarationScope() instanceof PsiMethod) {
        myVisitor.report(JavaErrorKinds.UNNAMED_METHOD_PARAMETER_NOT_ALLOWED.create(parameter));
      }
    }
    else if (variable instanceof PsiField field) {
      myVisitor.report(JavaErrorKinds.UNNAMED_FIELD_NOT_ALLOWED.create(field));
    }
    else {
      myVisitor.report(JavaErrorKinds.UNNAMED_VARIABLE_NOT_ALLOWED_IN_THIS_CONTEXT.create(variable));
    }
  }

  private static @NotNull PsiJavaCodeReferenceElement getOuterReferenceParent(@NotNull PsiJavaCodeReferenceElement ref) {
    PsiJavaCodeReferenceElement element = ref;
    while (true) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement) {
        element = (PsiJavaCodeReferenceElement)parent;
      }
      else {
        break;
      }
    }
    return element;
  }

  void checkReference(@NotNull PsiJavaCodeReferenceElement ref, @NotNull JavaResolveResult result) {
    PsiElement refName = ref.getReferenceNameElement();
    if (!(refName instanceof PsiIdentifier) && !(refName instanceof PsiKeyword)) return;
    PsiElement resolved = result.getElement();

    PsiElement refParent = ref.getParent();

    if (refParent instanceof PsiReferenceExpression && refParent.getParent() instanceof PsiMethodCallExpression granny) {
      PsiReferenceExpression referenceToMethod = granny.getMethodExpression();
      PsiExpression qualifierExpression = referenceToMethod.getQualifierExpression();
      if (qualifierExpression == ref && resolved != null && !(resolved instanceof PsiClass) && !(resolved instanceof PsiVariable)) {
        myVisitor.report(JavaErrorKinds.REFERENCE_QUALIFIER_NOT_EXPRESSION.create(qualifierExpression));
        return;
      }
    }
    else if (refParent instanceof PsiMethodCallExpression) {
      return;  // methods checked elsewhere
    }

    if (resolved == null) {
      checkUnresolvedReference(ref, result);
      return;
    }
    if (resolved instanceof PsiClass psiClass &&
        psiClass.getContainingClass() == null &&
        PsiUtil.isFromDefaultPackage(resolved) &&
        (PsiTreeUtil.getParentOfType(ref, PsiImportStatementBase.class) != null ||
         PsiUtil.isModuleFile(myVisitor.file()) ||
         !PsiUtil.isFromDefaultPackage(myVisitor.file()))) {
      myVisitor.report(JavaErrorKinds.REFERENCE_CLASS_IN_DEFAULT_PACKAGE.create(ref, psiClass));
    }
    if ((resolved instanceof PsiLocalVariable || resolved instanceof PsiParameter) && !(resolved instanceof ImplicitVariable)) {
      myVisitor.myControlFlowChecker.checkVariableMustBeFinal((PsiVariable)resolved, ref);
    }
    boolean skipValidityChecks =
      PsiUtil.isInsideJavadocComment(ref) ||
      PsiTreeUtil.getParentOfType(ref, PsiPackageStatement.class, true) != null ||
      resolved instanceof PsiPackage && ref.getParent() instanceof PsiJavaCodeReferenceElement;

    if (skipValidityChecks) return;

    if (!result.isAccessible() && resolved instanceof PsiModifierListOwner owner) {
      myVisitor.myModifierChecker.reportAccessProblem(ref, owner, result);
      return;
    }
    if (!result.isStaticsScopeCorrect()) {
      myVisitor.report(JavaErrorKinds.REFERENCE_NON_STATIC_FROM_STATIC_CONTEXT.create(ref, resolved));
    }
    if (resolved instanceof PsiModifierListOwner owner) {
      myVisitor.myModuleChecker.checkModuleAccess(owner, ref);
    }
  }

  private void checkUnresolvedReference(@NotNull PsiJavaCodeReferenceElement ref, @NotNull JavaResolveResult result) {
    // do not highlight unknown packages (javac does not care), Javadoc, and module references (checked elsewhere)
    PsiJavaCodeReferenceElement parent = getOuterReferenceParent(ref);
    PsiElement outerParent = parent.getParent();
    if (outerParent instanceof PsiPackageStatement ||
        result.isPackagePrefixPackageReference() ||
        PsiUtil.isInsideJavadocComment(ref) ||
        parent.resolve() instanceof PsiMember ||
        outerParent instanceof PsiPackageAccessibilityStatement) {
      return;
    }

    //do not highlight the 'module' keyword if the statement is not complete
    //see com.intellij.lang.java.parser.BasicFileParser.parseImportStatement
    if (JavaKeywords.MODULE.equals(ref.getText()) && ref.getParent() instanceof PsiImportStatement &&
        PsiUtil.isAvailable(JavaFeature.MODULE_IMPORT_DECLARATIONS, ref)) {
      PsiElement importKeywordExpected = PsiTreeUtil.skipWhitespacesAndCommentsBackward(ref);
      PsiElement errorElementExpected = PsiTreeUtil.skipWhitespacesAndCommentsForward(ref);
      if (importKeywordExpected instanceof PsiKeyword keyword &&
          keyword.textMatches(JavaKeywords.IMPORT) &&
          errorElementExpected instanceof PsiErrorElement errorElement &&
          JavaPsiBundle.message("expected.identifier.or.semicolon").equals(errorElement.getErrorDescription())) {
        return;
      }
    }

    JavaResolveResult[] results = ref.multiResolve(true);
    if (results.length > 1) {
      if (ref instanceof PsiMethodReferenceExpression methodRef &&
          myVisitor.isIncompleteModel() && IncompleteModelUtil.isUnresolvedClassType(methodRef.getFunctionalInterfaceType())) {
        return;
      }
      myVisitor.report(JavaErrorKinds.REFERENCE_AMBIGUOUS.create(ref, Arrays.asList(results)));
    }
    else {
      boolean definitelyIncorrect = false;
      if (ref instanceof PsiReferenceExpression expression) {
        PsiExpression qualifierExpression = expression.getQualifierExpression();
        if (qualifierExpression != null) {
          PsiType type = qualifierExpression.getType();
          if (type instanceof PsiPrimitiveType primitiveType && !primitiveType.equals(PsiTypes.nullType())) {
            myVisitor.report(JavaErrorKinds.REFERENCE_QUALIFIER_PRIMITIVE.create(ref, primitiveType));
            return;
          }
        }
      }
      else if (ImplicitClassSearch.search(ref.getQualifiedName(), myVisitor.project(), ref.getResolveScope()).findFirst() != null) {
        myVisitor.report(JavaErrorKinds.REFERENCE_IMPLICIT_CLASS.create(ref));
        return;
      }
      if (!definitelyIncorrect && myVisitor.isIncompleteModel() && IncompleteModelUtil.canBePendingReference(ref)) {
        myVisitor.report(JavaErrorKinds.REFERENCE_PENDING.create(requireNonNull(ref.getReferenceNameElement())));
        return;
      }
      myVisitor.report(JavaErrorKinds.REFERENCE_UNRESOLVED.create(ref));
    }
  }

  static boolean favorParentReport(@NotNull PsiCall methodCall, @NotNull String errorMessage) {
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
        // suppress method reference error in order to display a more relevant inference error.
        return true;
      }
    }
    return false;
  }

  void checkIncompatibleType(@NotNull PsiCall methodCall,
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

  void checkStaticInterfaceCallQualifier(@NotNull PsiJavaCodeReferenceElement referenceToMethod,
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
    boolean isThisOrSuper = referenceToMethod.getReferenceNameElement() instanceof PsiKeyword;
    if (isThisOrSuper) {
      // super(..) or this(..)
      if (list.isEmpty()) { // implicit ctr call
        CandidateInfo[] candidates = PsiResolveHelper.getInstance(myVisitor.project())
          .getReferencedMethodCandidates(methodCall, true);
        if (candidates.length == 1 && !candidates[0].getElement().isPhysical()) {
          return true; // dummy constructor
        }
      }
    }
    return false;
  }

  void checkConstructorCall(@NotNull PsiClassType.ClassResolveResult typeResolveResult,
                            @NotNull PsiConstructorCall constructorCall,
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
    JavaResolveResult[] results = constructorCall.multiResolve(false);
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
      if (myVisitor.isIncompleteModel() &&
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

  private static @NotNull Pair<MethodCandidateInfo, MethodCandidateInfo> findCandidates(JavaResolveResult @NotNull [] resolveResults) {
    MethodCandidateInfo methodCandidate1 = null;
    MethodCandidateInfo methodCandidate2 = null;
    for (JavaResolveResult result : resolveResults) {
      if (!(result instanceof MethodCandidateInfo candidate)) continue;
      if (candidate.isApplicable() && !candidate.getElement().isConstructor()) {
        if (methodCandidate1 == null) {
          methodCandidate1 = candidate;
        }
        else {
          methodCandidate2 = candidate;
          break;
        }
      }
    }
    return Pair.pair(methodCandidate1, methodCandidate2);
  }

  void checkAmbiguousMethodCallArguments(JavaResolveResult @NotNull [] resolveResults,
                                         @NotNull JavaResolveResult resolveResult,
                                         @NotNull PsiMethodCallExpression methodCall) {
    PsiExpressionList list = methodCall.getArgumentList();
    Pair<MethodCandidateInfo, MethodCandidateInfo> pair = findCandidates(resolveResults);
    MethodCandidateInfo methodCandidate1 = pair.first;
    MethodCandidateInfo methodCandidate2 = pair.second;

    PsiExpression[] expressions = list.getExpressions();
    if (PsiTreeUtil.hasErrorElements(list)) return;
    if (methodCandidate2 != null) {
      if (myVisitor.isIncompleteModel() &&
          ContainerUtil.exists(expressions, e -> IncompleteModelUtil.mayHaveUnknownTypeDueToPendingReference(e))) {
        return;
      }
      myVisitor.report(JavaErrorKinds.CALL_AMBIGUOUS.create(
        methodCall, new JavaAmbiguousCallContext(resolveResults, methodCandidate1, methodCandidate2)));
    }
    else {
      if (resolveResult.getElement() != null && (!resolveResult.isAccessible() || !resolveResult.isStaticsScopeCorrect())) return;
      if (!ContainerUtil.exists(resolveResults, result -> result instanceof MethodCandidateInfo && result.isAccessible())) return;
      if (myVisitor.isIncompleteModel() &&
          ContainerUtil.exists(expressions, IncompleteModelUtil::mayHaveUnknownTypeDueToPendingReference)) {
        return;
      }
      if (ContainerUtil.exists(expressions, e -> e.getType() == null)) return;
      myVisitor.report(JavaErrorKinds.CALL_UNRESOLVED.create(methodCall, resolveResults));
    }
  }

  void checkAmbiguousMethodCallIdentifier(JavaResolveResult @NotNull [] resolveResults,
                                          @NotNull JavaResolveResult resolveResult,
                                          @NotNull PsiMethodCallExpression methodCall) {
    PsiReferenceExpression referenceToMethod = methodCall.getMethodExpression();
    PsiElement element = resolveResult.getElement();
    MethodCandidateInfo methodCandidate2 = findCandidates(resolveResults).second;
    if (methodCandidate2 != null) return;

    PsiElement anchor = requireNonNullElse(referenceToMethod.getReferenceNameElement(), referenceToMethod);
    if (element instanceof PsiModifierListOwner owner && !resolveResult.isAccessible()) {
      myVisitor.myModifierChecker.reportAccessProblem(referenceToMethod, owner, resolveResult);
    }
    else if (element != null && !resolveResult.isStaticsScopeCorrect()) {
      if (element instanceof PsiMethod psiMethod && psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass != null && containingClass.isInterface()) {
          myVisitor.checkFeature(anchor, JavaFeature.STATIC_INTERFACE_CALLS);
          if (myVisitor.hasErrorResults()) return;
          checkStaticInterfaceCallQualifier(referenceToMethod, resolveResult, containingClass);
          if (myVisitor.hasErrorResults()) return;
        }
      }
      myVisitor.report(JavaErrorKinds.REFERENCE_NON_STATIC_FROM_STATIC_CONTEXT.create(referenceToMethod, element));
    }
    else if (!ContainerUtil.exists(resolveResults, result -> result instanceof MethodCandidateInfo && result.isAccessible())) {
      PsiClass qualifierClass = RefactoringChangeUtil.getQualifierClass(referenceToMethod);
      String className = qualifierClass != null ? qualifierClass.getName() : null;
      PsiExpression qualifierExpression = referenceToMethod.getQualifierExpression();

      if (className != null) {
        if (myVisitor.isIncompleteModel() && IncompleteModelUtil.canBePendingReference(referenceToMethod)) {
          myVisitor.report(JavaErrorKinds.REFERENCE_PENDING.create(anchor));
          return;
        }
        myVisitor.report(JavaErrorKinds.CALL_AMBIGUOUS_NO_MATCH.create(methodCall, resolveResults));
      }
      else if (qualifierExpression != null &&
               qualifierExpression.getType() instanceof PsiPrimitiveType primitiveType &&
               !primitiveType.equals(PsiTypes.nullType())) {
        myVisitor.report(JavaErrorKinds.CALL_QUALIFIER_PRIMITIVE.create(methodCall, primitiveType));
      }
      else {
        if (myVisitor.isIncompleteModel() && IncompleteModelUtil.canBePendingReference(referenceToMethod)) {
          myVisitor.report(JavaErrorKinds.REFERENCE_PENDING.create(anchor));
          return;
        }
        myVisitor.report(JavaErrorKinds.CALL_UNRESOLVED_NAME.create(methodCall, resolveResults));
      }
    }
  }


  void checkRestrictedIdentifierReference(@NotNull PsiJavaCodeReferenceElement ref, @NotNull PsiClass resolved) {
    String name = resolved.getName();
    if (PsiTypesUtil.isRestrictedIdentifier(name, myVisitor.languageLevel())) {
      myVisitor.report(JavaErrorKinds.TYPE_RESTRICTED_IDENTIFIER.create(ref));
    }
  }

  void checkInstanceOfApplicable(@NotNull PsiInstanceOfExpression expression) {
    PsiExpression operand = expression.getOperand();
    PsiTypeElement typeElement = expression.getCheckType();
    if (typeElement == null) {
      typeElement = JavaPsiPatternUtil.getPatternTypeElement(expression.getPattern());
    }
    if (typeElement == null) return;
    PsiType checkType = typeElement.getType();
    PsiType operandType = operand.getType();
    if (operandType == null) return;
    boolean operandIsPrimitive = TypeConversionUtil.isPrimitiveAndNotNull(operandType);
    boolean checkIsPrimitive = TypeConversionUtil.isPrimitiveAndNotNull(checkType);
    boolean convertible = TypeConversionUtil.areTypesConvertible(operandType, checkType);
    boolean primitiveInPatternsEnabled = PsiUtil.isAvailable(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS, expression);
    if (((operandIsPrimitive || checkIsPrimitive) && !primitiveInPatternsEnabled) || !convertible) {
      if (!convertible && myVisitor.isIncompleteModel() && IncompleteModelUtil.isPotentiallyConvertible(checkType, operand)) {
        return;
      }
      if (((operandIsPrimitive || checkIsPrimitive) && !primitiveInPatternsEnabled) && convertible) {
        myVisitor.checkFeature(expression, JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS);
        if (myVisitor.hasErrorResults()) return;
      }
      myVisitor.report(JavaErrorKinds.CAST_INCONVERTIBLE.create(
        expression, new JavaIncompatibleTypeErrorContext(operandType, checkType)));
      return;
    }
    PsiPrimaryPattern pattern = expression.getPattern();
    if (pattern instanceof PsiDeconstructionPattern deconstruction) {
      myVisitor.myPatternChecker.checkDeconstructionErrors(deconstruction);
    }
  }

  void checkConditionalExpressionBranchTypesMatch(@NotNull PsiExpression expression, @Nullable PsiType type) {
    PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiConditionalExpression conditionalExpression)) return;
    // check else branches only
    if (conditionalExpression.getElseExpression() != expression) return;
    PsiExpression thenExpression = conditionalExpression.getThenExpression();
    assert thenExpression != null;
    PsiType thenType = thenExpression.getType();
    if (thenType == null || type == null) return;
    if (conditionalExpression.getType() == null) {
      if (PsiUtil.isLanguageLevel8OrHigher(conditionalExpression) && PsiPolyExpressionUtil.isPolyExpression(conditionalExpression)) {
        return;
      }
      // cannot derive type of conditional expression
      // elseType will never be cast-able to thenType, so no quick fix here
      myVisitor.reportIncompatibleType(thenType, type, expression);
    }
  }

  void checkSelectFromTypeParameter(@NotNull PsiJavaCodeReferenceElement ref, @Nullable PsiElement resolved) {
    if ((ref.getParent() instanceof PsiJavaCodeReferenceElement || ref.isQualified()) &&
        resolved instanceof PsiTypeParameter) {
      boolean canSelectFromTypeParameter = myVisitor.sdkVersion().isAtLeast(JavaSdkVersion.JDK_1_7);
      if (canSelectFromTypeParameter) {
        PsiClass containingClass = PsiTreeUtil.getParentOfType(ref, PsiClass.class);
        if (containingClass != null) {
          if (PsiTreeUtil.isAncestor(containingClass.getExtendsList(), ref, false) ||
              PsiTreeUtil.isAncestor(containingClass.getImplementsList(), ref, false)) {
            canSelectFromTypeParameter = false;
          }
        }
      }
      if (!canSelectFromTypeParameter) {
        myVisitor.report(JavaErrorKinds.REFERENCE_SELECT_FROM_TYPE_PARAMETER.create(ref));
      }
    }
  }

  // element -> a constructor inside which this element is contained
  private PsiMethod findSurroundingConstructor(@NotNull PsiElement entry) {
    PsiMethod result = null;
    PsiElement element;
    for (element = entry; element != null && !(element instanceof PsiFile); element = element.getParent()) {
      result = myInsideConstructorOfClassCache.get(element);
      if (result != null || myInsideConstructorOfClassCache.containsKey(element)) {
        break;
      }
      if (element instanceof PsiMethod method && method.isConstructor()) {
        result = method;
        break;
      }
    }
    for (PsiElement e = entry; e != null && !(e instanceof PsiFile); e = e.getParent()) {
      myInsideConstructorOfClassCache.put(e, result);
      if (e == element) break;
    }
    return result;
  }

  private static boolean isOnSimpleAssignmentLeftHand(@NotNull PsiElement expr) {
    PsiElement parent = PsiTreeUtil.skipParentsOfType(expr, PsiParenthesizedExpression.class);
    return parent instanceof PsiAssignmentExpression assignment &&
           JavaTokenType.EQ == assignment.getOperationTokenType() &&
           PsiTreeUtil.isAncestor(assignment.getLExpression(), expr, false);
  }

  private static boolean isThisOrSuperReference(@Nullable PsiExpression qualifierExpression, @NotNull PsiClass aClass) {
    if (qualifierExpression == null) return true;
    if (!(qualifierExpression instanceof PsiQualifiedExpression expression)) return false;
    PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
    if (qualifier == null) return true;
    PsiElement resolved = qualifier.resolve();
    return resolved instanceof PsiClass && InheritanceUtil.isInheritorOrSelf(aClass, (PsiClass)resolved, true);
  }
  
  void checkMemberReferencedBeforeConstructorCalled(@NotNull PsiElement expression, @Nullable PsiElement resolved) {
    PsiMethod constructor = findSurroundingConstructor(expression);
    // not inside expression inside constructor
    if (constructor == null) return;
    PsiMethodCallExpression constructorCall = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(constructor);
    if (constructorCall == null) return;
    if (expression.getTextOffset() > constructorCall.getTextOffset() + constructorCall.getTextLength()) return;
    // is in or before this() or super() call

    PsiClass referencedClass;
    String resolvedName;
    PsiElement parent = expression.getParent();
    if (expression instanceof PsiJavaCodeReferenceElement referenceElement) {
      // redirected ctr
      if (JavaKeywords.THIS.equals(referenceElement.getReferenceName())
          && resolved instanceof PsiMethod psiMethod
          && psiMethod.isConstructor()) {
        return;
      }
      PsiElement qualifier = referenceElement.getQualifier();
      referencedClass = PsiUtil.resolveClassInType(qualifier instanceof PsiExpression psiExpression ? psiExpression.getType() : null);

      boolean isSuperCall = JavaPsiConstructorUtil.isSuperConstructorCall(parent);
      if (resolved == null && isSuperCall) {
        if (qualifier instanceof PsiReferenceExpression referenceExpression) {
          resolved = referenceExpression.resolve();
          expression = qualifier;
          referencedClass = PsiUtil.resolveClassInType(referenceExpression.getType());
        }
        else if (qualifier == null) {
          resolved = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiMember.class);
          if (resolved instanceof PsiMethod psiMethod) {
            referencedClass = psiMethod.getContainingClass();
          }
        }
        else if (qualifier instanceof PsiThisExpression thisExpression) {
          referencedClass = PsiUtil.resolveClassInType(thisExpression.getType());
        }
      }
      if (resolved instanceof PsiField field) {
        if (field.hasModifierProperty(PsiModifier.STATIC)) return;
        if (myVisitor.isApplicable(JavaFeature.STATEMENTS_BEFORE_SUPER) &&
            myVisitor.languageLevel() != LanguageLevel.JDK_22_PREVIEW &&
            isOnSimpleAssignmentLeftHand(expression) &&
            field.getContainingClass() == PsiTreeUtil.getParentOfType(expression, PsiClass.class, PsiLambdaExpression.class)) {
          if (field.hasInitializer()) {
            myVisitor.report(JavaErrorKinds.FIELD_INITIALIZED_BEFORE_CONSTRUCTOR_CALL.create(expression, field));
          }
          return;
        }
        resolvedName =
          PsiFormatUtil.formatVariable(field, PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY);
        referencedClass = field.getContainingClass();
      }
      else if (resolved instanceof PsiMethod method) {
        if (method.hasModifierProperty(PsiModifier.STATIC)) return;
        PsiElement nameElement =
          expression instanceof PsiThisExpression ? expression : ((PsiJavaCodeReferenceElement)expression).getReferenceNameElement();
        String name = nameElement == null ? null : nameElement.getText();
        if (isSuperCall) {
          if (referencedClass == null) return;
          if (qualifier == null) {
            PsiClass superClass = referencedClass.getSuperClass();
            if (superClass != null
                && PsiUtil.isInnerClass(superClass)
                && InheritanceUtil.isInheritorOrSelf(referencedClass, superClass.getContainingClass(), true)) {
              // by default super() is considered "this"-qualified
              resolvedName = JavaKeywords.THIS;
            }
            else {
              return;
            }
          }
          else {
            resolvedName = qualifier.getText();
          }
        }
        else if (JavaKeywords.THIS.equals(name)) {
          resolvedName = JavaKeywords.THIS;
        }
        else {
          resolvedName = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
                                                    PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_NAME, 0);
          if (referencedClass == null) referencedClass = method.getContainingClass();
        }
      }
      else if (resolved instanceof PsiClass aClass) {
        if (expression instanceof PsiReferenceExpression) return;
        if (aClass.hasModifierProperty(PsiModifier.STATIC)) return;
        referencedClass = aClass.getContainingClass();
        if (referencedClass == null) return;
        resolvedName = PsiFormatUtil.formatClass(aClass, PsiFormatUtilBase.SHOW_NAME);
      }
      else {
        return;
      }
    }
    else if (expression instanceof PsiQualifiedExpression qualifiedExpression) {
      referencedClass = PsiUtil.resolveClassInType(qualifiedExpression.getType());
      String keyword = expression instanceof PsiThisExpression ? JavaKeywords.THIS : JavaKeywords.SUPER;
      PsiJavaCodeReferenceElement qualifier = qualifiedExpression.getQualifier();
      resolvedName = qualifier != null && qualifier.resolve() instanceof PsiClass aClass
                     ? PsiFormatUtil.formatClass(aClass, PsiFormatUtilBase.SHOW_NAME) + "." + keyword
                     : keyword;
    }
    else {
      return;
    }

    if (referencedClass == null ||
        PsiTreeUtil.getParentOfType(expression, PsiReferenceParameterList.class, true, PsiExpression.class) != null) {
      return;
    }

    PsiClass parentClass = constructor.getContainingClass();
    if (parentClass == null) return;

    // references to private methods from the outer class are not calls to super methods
    // even if the outer class is the superclass
    if (resolved instanceof PsiMember member && member.hasModifierProperty(PsiModifier.PRIVATE) && referencedClass != parentClass) return;
    // field or method should be declared in this class or super
    if (!InheritanceUtil.isInheritorOrSelf(parentClass, referencedClass, true)) return;
    // and point to our instance
    if (expression instanceof PsiReferenceExpression ref) {
      PsiExpression qualifier = ref.getQualifierExpression();
      if (!isThisOrSuperReference(qualifier, parentClass)) return;
      else if (qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) {
        if (((PsiQualifiedExpression)qualifier).getQualifier() != null) return;
      }
    }

    if (expression instanceof PsiThisExpression && referencedClass != parentClass) return;

    if (expression instanceof PsiJavaCodeReferenceElement) {
      if (!parentClass.equals(PsiTreeUtil.getParentOfType(expression, PsiClass.class)) &&
          PsiTreeUtil.getParentOfType(expression, PsiTypeElement.class) != null) {
        return;
      }

      if (PsiTreeUtil.getParentOfType(expression, PsiClassObjectAccessExpression.class) != null) return;

      if (parent instanceof PsiNewExpression newExpression &&
          newExpression.isArrayCreation() &&
          newExpression.getClassOrAnonymousClassReference() == expression) {
        return;
      }
      if (parent instanceof PsiThisExpression || parent instanceof PsiSuperExpression) return;
    }
    if (!(expression instanceof PsiThisExpression) && !(expression instanceof PsiSuperExpression) ||
        ((PsiQualifiedExpression)expression).getQualifier() == null) {
      PsiClass expressionClass = PsiUtil.getContainingClass(expression);
      while (expressionClass != null && parentClass != expressionClass) {
        if (InheritanceUtil.isInheritorOrSelf(expressionClass, referencedClass, true)) return;
        expressionClass = PsiUtil.getContainingClass(expressionClass);
      }
    }

    if (expression instanceof PsiThisExpression) {
      LanguageLevel languageLevel = PsiUtil.getLanguageLevel(expression);
      if (JavaFeature.STATEMENTS_BEFORE_SUPER.isSufficient(languageLevel) && languageLevel != LanguageLevel.JDK_22_PREVIEW) {
        parent = PsiUtil.skipParenthesizedExprUp(parent);
        if (isOnSimpleAssignmentLeftHand(parent) &&
            parent instanceof PsiReferenceExpression ref &&
            ref.resolve() instanceof PsiField field &&
            field.getContainingClass() == PsiTreeUtil.getParentOfType(expression, PsiClass.class, PsiLambdaExpression.class)) {
          return;
        }
      }
    }
    var kind = resolved instanceof PsiMethod ? 
               JavaErrorKinds.CALL_MEMBER_BEFORE_CONSTRUCTOR : 
               JavaErrorKinds.REFERENCE_MEMBER_BEFORE_CONSTRUCTOR;
    myVisitor.report(kind.create(expression, resolvedName));
  }

  void checkPackageAndClassConflict(@NotNull PsiJavaCodeReferenceElement ref) {
    if (ref.isQualified() && getOuterReferenceParent(ref).getParent() instanceof PsiPackageStatement) {
      Module module = ModuleUtilCore.findModuleForFile(myVisitor.file());
      if (module != null) {
        GlobalSearchScope scope = module.getModuleWithDependenciesAndLibrariesScope(false);
        PsiClass aClass = JavaPsiFacade.getInstance(ref.getProject()).findClass(ref.getCanonicalText(), scope);
        if (aClass != null) {
          myVisitor.report(JavaErrorKinds.PACKAGE_CLASHES_WITH_CLASS.create(ref));
        }
      }
    }
  }
}

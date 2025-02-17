// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.java.codeserver.core.JavaPsiEnumUtil;
import com.intellij.java.codeserver.highlighting.errors.JavaCompilationError;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.java.codeserver.highlighting.errors.JavaIncompatibleTypeErrorContext;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static java.util.Objects.requireNonNull;

final class GenericsChecker {
  private final @NotNull JavaErrorVisitor myVisitor;
  private final Set<PsiClass> myOverrideEquivalentMethodsVisitedClasses = new HashSet<>();
  // stored "clashing signatures" errors for the method (if the key is a PsiModifierList of the method), or the class (if the key is a PsiModifierList of the class)
  private final Map<PsiMember, JavaCompilationError<PsiMember, ?>> myOverrideEquivalentMethodsErrors = new HashMap<>();

  GenericsChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  void checkElementInTypeParameterExtendsList(@NotNull PsiReferenceList referenceList,
                                              @NotNull PsiTypeParameter typeParameter,
                                              @NotNull JavaResolveResult resolveResult,
                                              @NotNull PsiJavaCodeReferenceElement element) {
    PsiJavaCodeReferenceElement[] referenceElements = referenceList.getReferenceElements();
    PsiClass extendFrom = (PsiClass)resolveResult.getElement();
    if (extendFrom == null) return;
    if (!extendFrom.isInterface() && referenceElements.length != 0 && element != referenceElements[0]) {
      myVisitor.report(JavaErrorKinds.TYPE_PARAMETER_EXTENDS_INTERFACE_EXPECTED.create(element, typeParameter));
    }
    else if (referenceElements.length != 0 &&
             element != referenceElements[0] &&
             referenceElements[0].resolve() instanceof PsiTypeParameter) {
      myVisitor.report(JavaErrorKinds.TYPE_PARAMETER_CANNOT_BE_FOLLOWED_BY_OTHER_BOUNDS.create(element, typeParameter));
    }
  }

  void checkCannotInheritFromTypeParameter(@Nullable PsiClass superClass, @NotNull PsiJavaCodeReferenceElement toHighlight) {
    if (superClass instanceof PsiTypeParameter) {
      myVisitor.report(JavaErrorKinds.CLASS_INHERITS_TYPE_PARAMETER.create(toHighlight, superClass));
    }
  }

  void checkTypeParametersList(@NotNull PsiTypeParameterList list, PsiTypeParameter @NotNull [] parameters) {
    PsiElement parent = list.getParent();
    if (parent instanceof PsiClass psiClass && psiClass.isEnum()) {
      myVisitor.report(JavaErrorKinds.TYPE_PARAMETER_ON_ENUM.create(list));
      return;
    }
    if (PsiUtil.isAnnotationMethod(parent)) {
      myVisitor.report(JavaErrorKinds.TYPE_PARAMETER_ON_ANNOTATION_MEMBER.create(list));
      return;
    }
    if (parent instanceof PsiClass psiClass && psiClass.isAnnotationType()) {
      myVisitor.report(JavaErrorKinds.TYPE_PARAMETER_ON_ANNOTATION.create(list));
      return;
    }

    for (int i = 0; i < parameters.length; i++) {
      PsiTypeParameter typeParameter1 = parameters[i];
      myVisitor.myClassChecker.checkCyclicInheritance(typeParameter1);
      if (myVisitor.hasErrorResults()) return;
      String name1 = typeParameter1.getName();
      for (int j = i + 1; j < parameters.length; j++) {
        PsiTypeParameter typeParameter2 = parameters[j];
        String name2 = typeParameter2.getName();
        if (Objects.equals(name1, name2)) {
          myVisitor.report(JavaErrorKinds.TYPE_PARAMETER_DUPLICATE.create(typeParameter2));
        }
      }
    }
  }

  void checkForEachParameterType(@NotNull PsiForeachStatement statement, @NotNull PsiParameter parameter) {
    PsiExpression expression = statement.getIteratedValue();
    PsiType itemType = expression == null ? null : JavaGenericsUtil.getCollectionItemType(expression);
    if (itemType == null) return;

    PsiType parameterType = parameter.getType();
    if (TypeConversionUtil.isAssignable(parameterType, itemType)) return;
    if (myVisitor.isIncompleteModel() && IncompleteModelUtil.isPotentiallyConvertible(parameterType, itemType, expression)) {
      return;
    }
    myVisitor.report(JavaErrorKinds.TYPE_INCOMPATIBLE.create(parameter, new JavaIncompatibleTypeErrorContext(itemType, parameterType)));
  }

  void checkDiamondTypeNotAllowed(@NotNull PsiNewExpression expression) {
    PsiReferenceParameterList typeArgumentList = expression.getTypeArgumentList();
    PsiTypeElement[] typeParameterElements = typeArgumentList.getTypeParameterElements();
    if (typeParameterElements.length == 1 && typeParameterElements[0].getType() instanceof PsiDiamondType) {
      myVisitor.report(JavaErrorKinds.NEW_EXPRESSION_DIAMOND_NOT_ALLOWED.create(typeArgumentList));
    }
  }

  void checkSelectStaticClassFromParameterizedType(@Nullable PsiElement resolved, @NotNull PsiJavaCodeReferenceElement ref) {
    if (resolved instanceof PsiClass psiClass && psiClass.hasModifierProperty(PsiModifier.STATIC)) {
      PsiElement qualifier = ref.getQualifier();
      if (qualifier instanceof PsiJavaCodeReferenceElement referenceElement) {
        PsiReferenceParameterList parameterList = referenceElement.getParameterList();
        if (parameterList != null && parameterList.getTypeArguments().length > 0) {
          myVisitor.report(JavaErrorKinds.REFERENCE_TYPE_ARGUMENT_STATIC_CLASS.create(parameterList, psiClass));
        }
      }
    }
  }

  /**
   * see <a href="http://docs.oracle.com/javase/specs/jls/se7/html/jls-4.html#jls-4.8">JLS 4.8 on raw types</a>
   */
  void checkRawOnParameterizedType(@NotNull PsiJavaCodeReferenceElement parent, @Nullable PsiElement resolved) {
    PsiReferenceParameterList list = parent.getParameterList();
    if (list == null || list.getTypeArguments().length > 0) return;
    if (parent.getQualifier() instanceof PsiJavaCodeReferenceElement ref &&
        ref.getTypeParameters().length > 0 &&
        resolved instanceof PsiTypeParameterListOwner typeParameterListOwner &&
        typeParameterListOwner.hasTypeParameters() &&
        !typeParameterListOwner.hasModifierProperty(PsiModifier.STATIC) && 
        parent.getReferenceNameElement() != null) {
      myVisitor.report(JavaErrorKinds.REFERENCE_TYPE_NEEDS_TYPE_ARGUMENTS.create(parent));
    }
  }

  void checkInterfaceMultipleInheritance(@NotNull PsiClass aClass) {
    PsiClassType[] types = aClass.getSuperTypes();
    if (types.length < 2) return;
    checkInterfaceMultipleInheritance(aClass,
                                      aClass,
                                      PsiSubstitutor.EMPTY, new HashMap<>(),
                                      new HashSet<>());
  }

  private void checkInterfaceMultipleInheritance(@NotNull PsiClass aClass,
                                                 @NotNull PsiClass place,
                                                 @NotNull PsiSubstitutor derivedSubstitutor,
                                                 @NotNull Map<PsiClass, PsiSubstitutor> inheritedClasses,
                                                 @NotNull Set<? super PsiClass> visited) {
    List<PsiClassType.ClassResolveResult> superTypes = PsiClassImplUtil.getScopeCorrectedSuperTypes(aClass, place.getResolveScope());
    for (PsiClassType.ClassResolveResult result : superTypes) {
      PsiClass superClass = result.getElement();
      if (superClass == null || visited.contains(superClass)) continue;
      PsiSubstitutor superTypeSubstitutor = result.getSubstitutor();
      //JLS 4.8 The superclasses (respectively, superinterfaces) of a raw type are the erasures 
      // of the superclasses (superinterfaces) of any of the parameterizations of the generic type.
      superTypeSubstitutor = PsiUtil.isRawSubstitutor(aClass, derivedSubstitutor)
                             ? myVisitor.factory().createRawSubstitutor(superClass)
                             : MethodSignatureUtil.combineSubstitutors(superTypeSubstitutor, derivedSubstitutor);

      PsiSubstitutor inheritedSubstitutor = inheritedClasses.get(superClass);
      if (inheritedSubstitutor != null) {
        PsiTypeParameter[] typeParameters = superClass.getTypeParameters();
        for (PsiTypeParameter typeParameter : typeParameters) {
          PsiType type1 = inheritedSubstitutor.substitute(typeParameter);
          PsiType type2 = superTypeSubstitutor.substitute(typeParameter);

          if (!Comparing.equal(type1, type2)) {
            var context = new JavaErrorKinds.InheritTypeClashContext(superClass, type1, type2);
            if (type1 != null && type2 != null) {
              myVisitor.report(JavaErrorKinds.CLASS_INHERITANCE_DIFFERENT_TYPE_ARGUMENTS.create(place, context));
            }
            else {
              myVisitor.report(JavaErrorKinds.CLASS_INHERITANCE_RAW_AND_GENERIC.create(place, context));
            }
            return;
          }
        }
      }
      inheritedClasses.put(superClass, superTypeSubstitutor);
      visited.add(superClass);
      checkInterfaceMultipleInheritance(superClass, place, superTypeSubstitutor, inheritedClasses, visited);
      visited.remove(superClass);
      if (myVisitor.hasErrorResults()) return;
    }
  }

  void checkClassSupersAccessibility(@NotNull PsiClass aClass) {
    checkClassSupersAccessibility(aClass, aClass, aClass.getResolveScope(), true);
  }

  void checkClassSupersAccessibility(@NotNull PsiClass aClass, @NotNull PsiElement ref, @NotNull GlobalSearchScope scope) {
    checkClassSupersAccessibility(ref, aClass, scope, false);
  }

  private void checkClassSupersAccessibility(@NotNull PsiElement anchor,
                                             @NotNull PsiClass aClass,
                                             @NotNull GlobalSearchScope resolveScope,
                                             boolean checkParameters) {
    JavaPsiFacade factory = JavaPsiFacade.getInstance(myVisitor.project());
    for (PsiClassType superType : aClass.getSuperTypes()) {
      HashSet<PsiClass> checked = new HashSet<>();
      checked.add(aClass);
      checkTypeAccessible(anchor, superType, checked, checkParameters, true, resolveScope, factory);
    }
  }

  private void checkTypeAccessible(@NotNull PsiElement anchor,
                                   @Nullable PsiType type,
                                   @NotNull Set<? super PsiClass> classes,
                                   boolean checkParameters,
                                   boolean checkSuperTypes,
                                   @NotNull GlobalSearchScope resolveScope,
                                   @NotNull JavaPsiFacade factory) {
    type = PsiClassImplUtil.correctType(type, resolveScope);

    PsiClass aClass = PsiUtil.resolveClassInType(type);
    if (aClass != null && classes.add(aClass)) {
      VirtualFile vFile = PsiUtilCore.getVirtualFile(aClass);
      if (vFile == null) return;
      FileIndexFacade index = FileIndexFacade.getInstance(myVisitor.project());
      if (!index.isInSource(vFile) && !index.isInLibraryClasses(vFile)) return;

      PsiImplicitClass parentImplicitClass = PsiTreeUtil.getParentOfType(aClass, PsiImplicitClass.class);
      String qualifiedName = aClass.getQualifiedName();
      if (parentImplicitClass == null && qualifiedName != null && factory.findClass(qualifiedName, resolveScope) == null) {
        myVisitor.report(JavaErrorKinds.CLASS_NOT_ACCESSIBLE.create(anchor, aClass));
        return;
      }

      if (!checkParameters) return;

      if (type instanceof PsiClassType classType) {
        for (PsiType parameterType : classType.getParameters()) {
          checkTypeAccessible(anchor, parameterType, classes, true, false, resolveScope, factory);
          if (myVisitor.hasErrorResults()) return;
        }
      }

      if (!checkSuperTypes) return;

      boolean isInLibrary = !index.isInContent(vFile);
      for (PsiClassType superType : aClass.getSuperTypes()) {
        checkTypeAccessible(anchor, superType, classes, !isInLibrary, true, resolveScope, factory);
        if (myVisitor.hasErrorResults()) return;
      }
    }
  }

  void checkInferredIntersections(@NotNull PsiSubstitutor substitutor, @NotNull PsiMethodCallExpression call) {
    for (Map.Entry<PsiTypeParameter, PsiType> typeEntry : substitutor.getSubstitutionMap().entrySet()) {
      if (typeEntry.getValue() instanceof PsiIntersectionType intersectionType) {
        String conflictingConjunctsMessage = intersectionType.getConflictingConjunctsMessage();
        if (conflictingConjunctsMessage != null) {
          myVisitor.report(JavaErrorKinds.TYPE_PARAMETER_INCOMPATIBLE_UPPER_BOUNDS.create(
            call, new JavaErrorKinds.IncompatibleIntersectionContext(typeEntry.getKey(), conflictingConjunctsMessage)));
        }
      }
    }
  }

  void checkParameterizedReferenceTypeArguments(@Nullable PsiElement resolved,
                                                @NotNull PsiJavaCodeReferenceElement referenceElement,
                                                @NotNull PsiSubstitutor substitutor) {
    if (!(resolved instanceof PsiTypeParameterListOwner typeParameterListOwner)) return;
    checkReferenceTypeArgumentList(typeParameterListOwner, referenceElement.getParameterList(), substitutor);
  }

  void checkReferenceTypeArgumentList(@NotNull PsiTypeParameterListOwner typeParameterListOwner,
                                      @Nullable PsiReferenceParameterList referenceParameterList,
                                      @NotNull PsiSubstitutor substitutor) {
    PsiDiamondType.DiamondInferenceResult inferenceResult = null;
    PsiTypeElement[] referenceElements = null;
    if (referenceParameterList != null) {
      referenceElements = referenceParameterList.getTypeParameterElements();
      if (referenceElements.length == 1 && referenceElements[0].getType() instanceof PsiDiamondType diamondType) {
        if (!typeParameterListOwner.hasTypeParameters()) {
          myVisitor.report(JavaErrorKinds.NEW_EXPRESSION_DIAMOND_NOT_APPLICABLE.create(referenceParameterList));
          return;
        }
        inferenceResult = diamondType.resolveInferredTypes();
        String errorMessage = inferenceResult.getErrorMessage();
        if (errorMessage != null) {
          PsiType expectedType = detectExpectedType(referenceParameterList);
          if (!(inferenceResult.failedToInfer() && expectedType instanceof PsiClassType classType && classType.isRaw())) {
            if (inferenceResult == PsiDiamondType.DiamondInferenceResult.ANONYMOUS_INNER_RESULT ||
                inferenceResult == PsiDiamondType.DiamondInferenceResult.EXPLICIT_CONSTRUCTOR_TYPE_ARGS) {
              myVisitor.report(JavaErrorKinds.NEW_EXPRESSION_DIAMOND_INFERENCE_FAILURE.create(referenceParameterList, inferenceResult));
              return;
            }
          }
        }

        PsiElement parent = referenceParameterList.getParent().getParent();
        if (parent instanceof PsiAnonymousClass anonymousClass &&
            ContainerUtil.exists(anonymousClass.getMethods(),
                                 method -> !method.hasModifierProperty(PsiModifier.PRIVATE) && method.findSuperMethods().length == 0)) {
          myVisitor.report(JavaErrorKinds.NEW_EXPRESSION_DIAMOND_ANONYMOUS_INNER_NON_PRIVATE.create(referenceParameterList));
          return;
        }
      }
    }

    PsiTypeParameter[] typeParameters = typeParameterListOwner.getTypeParameters();
    int targetParametersNum = typeParameters.length;
    int refParametersNum = referenceParameterList == null ? 0 : referenceParameterList.getTypeArguments().length;
    if (targetParametersNum != refParametersNum && refParametersNum != 0) {
      if (targetParametersNum == 0) {
        boolean shouldSuppress = PsiTreeUtil.getParentOfType(referenceParameterList, PsiCall.class) != null &&
                                 typeParameterListOwner instanceof PsiMethod psiMethod &&
                                 (myVisitor.sdkVersion().isAtLeast(JavaSdkVersion.JDK_1_7) || hasSuperMethodsWithTypeParams(psiMethod));
        if (!shouldSuppress) {
          if (typeParameterListOwner instanceof PsiMethod psiMethod) {
            myVisitor.report(JavaErrorKinds.TYPE_PARAMETER_ABSENT_METHOD.create(referenceParameterList, psiMethod));
          }
          else if (typeParameterListOwner instanceof PsiClass psiClass) {
            myVisitor.report(JavaErrorKinds.TYPE_PARAMETER_ABSENT_CLASS.create(referenceParameterList, psiClass));
          }
          return;
        }
      }
      else {
        myVisitor.report(JavaErrorKinds.TYPE_PARAMETER_COUNT_MISMATCH.create(referenceParameterList, typeParameterListOwner));
        return;
      }
    }

    // bounds check
    if (targetParametersNum > 0 && refParametersNum != 0) {
      if (inferenceResult != null) {
        PsiType[] types = inferenceResult.getTypes();
        for (int i = 0; i < typeParameters.length; i++) {
          checkTypeParameterWithinItsBound(typeParameters[i], substitutor, types[i], referenceElements[0], referenceParameterList);
          if (myVisitor.hasErrorResults()) return;
        }
      }
      else {
        for (int i = 0; i < typeParameters.length; i++) {
          PsiTypeElement typeElement = referenceElements[i];
          checkTypeParameterWithinItsBound(typeParameters[i], substitutor, typeElement.getType(), typeElement, referenceParameterList);
          if (myVisitor.hasErrorResults()) return;
        }
      }
    }
  }

  private void checkTypeParameterWithinItsBound(@NotNull PsiTypeParameter classParameter,
                                                @NotNull PsiSubstitutor substitutor,
                                                @NotNull PsiType type,
                                                @NotNull PsiTypeElement typeElement2Highlight,
                                                @Nullable PsiReferenceParameterList referenceParameterList) {
    PsiClass referenceClass = type instanceof PsiClassType classType ? classType.resolve() : null;
    PsiType psiType = substitutor.substitute(classParameter);
    if (psiType instanceof PsiClassType && !(PsiUtil.resolveClassInType(psiType) instanceof PsiTypeParameter)) {
      if (GenericsUtil.checkNotInBounds(type, psiType, referenceParameterList)) {
        myVisitor.report(JavaErrorKinds.TYPE_PARAMETER_ACTUAL_INFERRED_MISMATCH.create(typeElement2Highlight));
        return;
      }
    }

    PsiClassType[] bounds = classParameter.getSuperTypes();
    for (PsiType bound : bounds) {
      bound = substitutor.substitute(bound);
      if (!bound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) && GenericsUtil.checkNotInBounds(type, bound, referenceParameterList)) {
        PsiClass boundClass = bound instanceof PsiClassType classType ? classType.resolve() : null;

        boolean extend = boundClass == null ||
                         referenceClass == null ||
                         referenceClass.isInterface() == boundClass.isInterface() ||
                         referenceClass instanceof PsiTypeParameter;
        var kind = extend
                   ? JavaErrorKinds.TYPE_PARAMETER_TYPE_NOT_WITHIN_EXTEND_BOUND
                   : JavaErrorKinds.TYPE_PARAMETER_TYPE_NOT_WITHIN_IMPLEMENT_BOUND;
        myVisitor.report(kind.create(typeElement2Highlight, new JavaErrorKinds.TypeParameterBoundMismatchContext(
          classParameter, bound, type)));
      }
    }
  }

  void checkCatchParameterIsClass(@NotNull PsiParameter parameter) {
    if (!(parameter.getDeclarationScope() instanceof PsiCatchSection)) return;

    List<PsiTypeElement> typeElements = PsiUtil.getParameterTypeElements(parameter);
    for (PsiTypeElement typeElement : typeElements) {
      if (PsiUtil.resolveClassInClassTypeOnly(typeElement.getType()) instanceof PsiTypeParameter) {
        myVisitor.report(JavaErrorKinds.CATCH_TYPE_PARAMETER.create(typeElement));
      }
    }
  }

  void checkReferenceTypeUsedAsTypeArgument(@NotNull PsiTypeElement typeElement) {
    PsiType type = typeElement.getType();
    PsiType wildCardBind = type instanceof PsiWildcardType wildcardType ? wildcardType.getBound() : null;
    if (type != PsiTypes.nullType() && type instanceof PsiPrimitiveType || wildCardBind instanceof PsiPrimitiveType) {
      if (!(typeElement.getParent() instanceof PsiReferenceParameterList list)) return;
      PsiElement parent = list.getParent();
      if (!(parent instanceof PsiJavaCodeReferenceElement) && !(parent instanceof PsiNewExpression)) return;
      myVisitor.report(JavaErrorKinds.TYPE_ARGUMENT_PRIMITIVE.create(typeElement));
    }
  }

  void checkWildcardUsage(@NotNull PsiTypeElement typeElement) {
    PsiType type = typeElement.getType();
    if (type instanceof PsiWildcardType) {
      if (typeElement.getParent() instanceof PsiReferenceParameterList) {
        PsiElement parent = typeElement.getParent().getParent();
        PsiElement refParent = parent.getParent();
        if (refParent instanceof PsiAnonymousClass) refParent = refParent.getParent();
        if (refParent instanceof PsiNewExpression newExpression) {
          if (!(newExpression.getType() instanceof PsiArrayType)) {
            myVisitor.report(JavaErrorKinds.TYPE_WILDCARD_CANNOT_BE_INSTANTIATED.create(typeElement));
          }
        }
        else if (refParent instanceof PsiReferenceList) {
          PsiElement refPParent = refParent.getParent();
          if (!(refPParent instanceof PsiTypeParameter typeParameter) || refParent != typeParameter.getExtendsList()) {
            myVisitor.report(JavaErrorKinds.TYPE_WILDCARD_NOT_EXPECTED.create(typeElement));
          }
        }
      }
      else if (!typeElement.isInferredType()){
        myVisitor.report(JavaErrorKinds.TYPE_WILDCARD_MAY_BE_USED_ONLY_AS_REFERENCE_PARAMETERS.create(typeElement));
      }
    }
  }

  void checkParametersAllowed(@NotNull PsiReferenceParameterList refParamList) {
    PsiElement parent = refParamList.getParent();
    if (parent instanceof PsiReferenceExpression) {
      PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression) && !(parent instanceof PsiMethodReferenceExpression)) {
        myVisitor.report(JavaErrorKinds.TYPE_ARGUMENT_NOT_ALLOWED.create(refParamList));
      }
    }
  }

  void checkParametersOnRaw(@NotNull PsiReferenceParameterList refParamList) {
    JavaResolveResult resolveResult = null;
    PsiElement parent = refParamList.getParent();
    PsiElement qualifier = null;
    if (parent instanceof PsiJavaCodeReferenceElement referenceElement) {
      resolveResult = referenceElement.advancedResolve(false);
      qualifier = referenceElement.getQualifier();
    }
    else if (parent instanceof PsiCallExpression callExpression) {
      resolveResult = callExpression.resolveMethodGenerics();
      if (parent instanceof PsiMethodCallExpression methodCallExpression) {
        PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        qualifier = methodExpression.getQualifier();
      }
    }
    if (resolveResult != null) {
      PsiElement element = resolveResult.getElement();
      if (!(element instanceof PsiTypeParameterListOwner owner)) return;
      if (owner.hasModifierProperty(PsiModifier.STATIC)) return;
      if (qualifier instanceof PsiJavaCodeReferenceElement referenceElement && referenceElement.resolve() instanceof PsiTypeParameter) return;
      PsiClass containingClass = owner.getContainingClass();
      if (containingClass != null && PsiUtil.isRawSubstitutor(containingClass, resolveResult.getSubstitutor())) {
        if (element instanceof PsiMethod psiMethod) {
          if (myVisitor.languageLevel().isAtLeast(LanguageLevel.JDK_1_7)) return;
          if (psiMethod.findSuperMethods().length > 0) return;
          if (qualifier instanceof PsiReferenceExpression expression) {
            PsiType type = expression.getType();
            boolean isJavac7 = JavaVersionService.getInstance().isAtLeast(containingClass, JavaSdkVersion.JDK_1_7);
            if (type instanceof PsiClassType psiClassType && isJavac7 && psiClassType.isRaw()) return;
            PsiClass typeParameter = PsiUtil.resolveClassInType(type);
            if (typeParameter instanceof PsiTypeParameter) {
              if (isJavac7) return;
              for (PsiClassType classType : typeParameter.getExtendsListTypes()) {
                PsiClass resolve = classType.resolve();
                if (resolve != null) {
                  PsiMethod[] superMethods = resolve.findMethodsBySignature(psiMethod, true);
                  for (PsiMethod superMethod : superMethods) {
                    if (!PsiUtil.isRawSubstitutor(superMethod, resolveResult.getSubstitutor())) {
                      return;
                    }
                  }
                }
              }
            }
          }
        }
        var kind = element instanceof PsiClass ? JavaErrorKinds.TYPE_ARGUMENT_ON_RAW_TYPE : JavaErrorKinds.TYPE_ARGUMENT_ON_RAW_METHOD;
        myVisitor.report(kind.create(refParamList));
      }
    }
  }

  void checkInstanceOfGenericType(@NotNull PsiInstanceOfExpression expression) {
    PsiTypeElement typeElement = expression.getCheckType();
    if (typeElement == null) {
      typeElement = JavaPsiPatternUtil.getPatternTypeElement(expression.getPattern());
    }
    if (typeElement == null) return;
    PsiType checkType = typeElement.getType();
    if (myVisitor.isApplicable(JavaFeature.PATTERNS)) {
      PsiPrimaryPattern pattern = expression.getPattern();
      if (pattern != null) {
        myVisitor.myPatternChecker.checkUncheckedPatternConversion(pattern);
      } else {
        checkUnsafeCastInInstanceOf(typeElement, checkType, expression.getOperand().getType());
      }
    } else {
      checkIllegalForInstanceOf(checkType, typeElement);
    }
  }

  void checkTypeParameterReference(@NotNull PsiJavaCodeReferenceElement ref, @NotNull PsiTypeParameter typeParameter) {
    PsiTypeParameterListOwner owner = typeParameter.getOwner();
    if (owner instanceof PsiClass outerClass) {
      if (!InheritanceUtil.hasEnclosingInstanceInScope(outerClass, ref, false, false)) {
        myVisitor.myExpressionChecker.checkIllegalEnclosingUsage(ref, null, outerClass, ref);
      }
    }
    else if (owner instanceof PsiMethod) {
      PsiModifierListOwner staticElement = PsiUtil.getEnclosingStaticElement(ref, null);
      if (staticElement != null && PsiTreeUtil.isAncestor(owner, staticElement, true)) {
        PsiClass ownerContainingClass = owner.getContainingClass();
        if (ownerContainingClass != null) {
          myVisitor.report(JavaErrorKinds.REFERENCE_OUTER_TYPE_PARAMETER_FROM_STATIC_CONTEXT.create(ref, typeParameter));
        }
      }
    }
  }

  private void checkUnsafeCastInInstanceOf(@NotNull PsiTypeElement checkTypeElement, @NotNull PsiType checkType, @Nullable PsiType expressionType) {
    if (expressionType != null && JavaGenericsUtil.isUncheckedCast(checkType, expressionType)) {
      myVisitor.report(JavaErrorKinds.INSTANCEOF_UNSAFE_CAST.create(
        checkTypeElement, new JavaIncompatibleTypeErrorContext(expressionType, checkType)));
    }
  }

  /**
   * 15.20.2 Type Comparison Operator instanceof
   * ReferenceType mentioned after the instanceof operator is reifiable
   */
  private void checkIllegalForInstanceOf(@Nullable PsiType type, @NotNull PsiTypeElement typeElement) {
    PsiClass resolved = PsiUtil.resolveClassInClassTypeOnly(type);
    if (resolved instanceof PsiTypeParameter) {
      myVisitor.report(JavaErrorKinds.INSTANCEOF_TYPE_PARAMETER.create(typeElement));
    }
    else if (!JavaGenericsUtil.isReifiableType(type)) {
      myVisitor.report(JavaErrorKinds.INSTANCEOF_ILLEGAL_GENERIC_TYPE.create(typeElement));
    }
  }

  void checkDefaultMethodOverridesMemberOfJavaLangObject(@NotNull PsiClass aClass, @NotNull PsiMethod method) {
    if (!myVisitor.isApplicable(JavaFeature.EXTENSION_METHODS) || !aClass.isInterface() || 
        !method.hasModifierProperty(PsiModifier.DEFAULT)) {
      return;
    }
    if (doesMethodOverrideMemberOfJavaLangObject(method)) {
      myVisitor.report(JavaErrorKinds.METHOD_DEFAULT_OVERRIDES_OBJECT_MEMBER.create(method));
    }
  }

  void checkUnrelatedConcrete(@NotNull PsiClass psiClass) {
    PsiClass superClass = psiClass.getSuperClass();
    if (superClass != null && superClass.hasTypeParameters()) {
      Collection<HierarchicalMethodSignature> visibleSignatures = superClass.getVisibleSignatures();
      Map<MethodSignature, PsiMethod> overrideEquivalent = MethodSignatureUtil.createErasedMethodSignatureMap();
      for (HierarchicalMethodSignature hms : visibleSignatures) {
        PsiMethod method = hms.getMethod();
        if (method.isConstructor()) continue;
        if (method.hasModifierProperty(PsiModifier.ABSTRACT) ||
            method.hasModifierProperty(PsiModifier.DEFAULT) ||
            method.hasModifierProperty(PsiModifier.STATIC)) continue;
        if (psiClass.findMethodsBySignature(method, false).length > 0) continue;
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) continue;
        PsiSubstitutor containingClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(containingClass, psiClass, PsiSubstitutor.EMPTY);
        PsiSubstitutor finalSubstitutor = PsiSuperMethodUtil
          .obtainFinalSubstitutor(containingClass, containingClassSubstitutor, hms.getSubstitutor(), false);
        MethodSignatureBackedByPsiMethod signature = MethodSignatureBackedByPsiMethod.create(method, finalSubstitutor, false);
        PsiMethod foundMethod = overrideEquivalent.get(signature);
        if (foundMethod != null &&
            !foundMethod.hasModifierProperty(PsiModifier.ABSTRACT) &&
            !foundMethod.hasModifierProperty(PsiModifier.DEFAULT) &&
            foundMethod.getContainingClass() != null) {
          myVisitor.report(JavaErrorKinds.CLASS_INHERITANCE_METHOD_CLASH.create(
            psiClass, new JavaErrorKinds.OverrideClashContext(method, foundMethod)));
          return;
        }
        overrideEquivalent.put(signature, method);
      }
    }
  }

  void checkGenericCannotExtendException(@NotNull PsiReferenceList list) {
    PsiElement parent = list.getParent();
    if (parent instanceof PsiClass klass) {
      if (hasGenericSignature(klass) && klass.getExtendsList() == list) {
        PsiClass throwableClass = null;
        for (PsiJavaCodeReferenceElement refElement : list.getReferenceElements()) {
          PsiElement resolved = refElement.resolve();
          if (!(resolved instanceof PsiClass psiClass)) continue;
          if (throwableClass == null) {
            throwableClass =
              JavaPsiFacade.getInstance(myVisitor.project()).findClass(CommonClassNames.JAVA_LANG_THROWABLE, klass.getResolveScope());
          }
          if (InheritanceUtil.isInheritorOrSelf(psiClass, throwableClass, true)) {
            myVisitor.report(JavaErrorKinds.CLASS_GENERIC_EXTENDS_EXCEPTION.create(refElement));
          }
        }
      }
    }
    else if (parent instanceof PsiMethod method && method.getThrowsList() == list) {
      for (PsiJavaCodeReferenceElement refElement : list.getReferenceElements()) {
        PsiReferenceParameterList parameterList = refElement.getParameterList();
        if (parameterList != null && parameterList.getTypeParameterElements().length != 0) {
          myVisitor.report(JavaErrorKinds.CLASS_GENERIC_EXTENDS_EXCEPTION.create(refElement));
        }
      }
    }
  }
  
  void checkGenericCannotExtendException(@NotNull PsiAnonymousClass anonymousClass) {
    if (hasGenericSignature(anonymousClass) &&
        InheritanceUtil.isInheritor(anonymousClass, true, CommonClassNames.JAVA_LANG_THROWABLE)) {
      myVisitor.report(JavaErrorKinds.CLASS_GENERIC_EXTENDS_EXCEPTION.create(anonymousClass.getBaseClassReference()));
    }
  }

  void checkClassObjectAccessExpression(@NotNull PsiClassObjectAccessExpression expression) {
    PsiType type = expression.getOperand().getType();
    if (type instanceof PsiClassType classType) {
      checkClassAccess(classType, expression.getOperand());
    }
    if (type instanceof PsiArrayType) {
      PsiType arrayComponentType = type.getDeepComponentType();
      if (arrayComponentType instanceof PsiClassType classType) {
        checkClassAccess(classType, expression.getOperand());
      }
    }
  }

  private void checkClassAccess(@NotNull PsiClassType type, @NotNull PsiTypeElement operand) {
    PsiClass aClass = type.resolve();
    if (aClass instanceof PsiTypeParameter) {
      myVisitor.report(JavaErrorKinds.EXPRESSION_CLASS_TYPE_PARAMETER.create(operand));
      return;
    }
    if (type.getParameters().length > 0) {
      myVisitor.report(JavaErrorKinds.EXPRESSION_CLASS_PARAMETERIZED_TYPE.create(operand));
    }
  }

  void checkTypeParameterInstantiation(@NotNull PsiNewExpression expression) {
    PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
    if (classReference == null) return;
    JavaResolveResult result = classReference.advancedResolve(false);
    PsiElement element = result.getElement();
    if (element instanceof PsiTypeParameter typeParameter) {
      myVisitor.report(JavaErrorKinds.NEW_EXPRESSION_TYPE_PARAMETER.create(classReference, typeParameter));
    }
  }

  //http://docs.oracle.com/javase/specs/jls/se7/html/jls-8.html#jls-8.9.2
  void checkAccessStaticFieldFromEnumConstructor(@NotNull PsiReferenceExpression expr,
                                                 @NotNull JavaResolveResult result) {
    PsiField field = ObjectUtils.tryCast(result.getElement(), PsiField.class);
    if (field == null) return;

    PsiClass enumClass = JavaPsiEnumUtil.getEnumClassForExpressionInInitializer(expr);
    if (enumClass == null || !JavaPsiEnumUtil.isRestrictedStaticEnumField(field, enumClass)) return;
    myVisitor.report(JavaErrorKinds.ENUM_CONSTANT_ILLEGAL_ACCESS_IN_CONSTRUCTOR.create(expr, field));
  }

  private static boolean hasGenericSignature(@NotNull PsiClass klass) {
    PsiClass containingClass = klass;
    while (containingClass != null && PsiUtil.isLocalOrAnonymousClass(containingClass)) {
      if (containingClass.hasTypeParameters()) return true;
      containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class);
    }
    return containingClass != null && PsiUtil.typeParametersIterator(containingClass).hasNext();
  }

  private static boolean doesMethodOverrideMemberOfJavaLangObject(@NotNull PsiMethod method) {
    for (HierarchicalMethodSignature methodSignature : method.getHierarchicalMethodSignature().getSuperSignatures()) {
      PsiMethod objectMethod = methodSignature.getMethod();
      PsiClass containingClass = objectMethod.getContainingClass();
      if (containingClass != null &&
          CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName()) &&
          objectMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
        return true;
      }
      if (doesMethodOverrideMemberOfJavaLangObject(objectMethod)) return true;
    }
    return false;
  }

  private static PsiType detectExpectedType(@NotNull PsiReferenceParameterList referenceParameterList) {
    PsiNewExpression newExpression = requireNonNull(PsiTreeUtil.getParentOfType(referenceParameterList, PsiNewExpression.class));
    PsiElement parent = newExpression.getParent();
    PsiType expectedType = null;
    if (parent instanceof PsiVariable psiVariable && newExpression.equals(psiVariable.getInitializer())) {
      expectedType = psiVariable.getType();
    }
    else if (parent instanceof PsiAssignmentExpression expression && newExpression.equals(expression.getRExpression())) {
      expectedType = expression.getLExpression().getType();
    }
    else if (parent instanceof PsiReturnStatement) {
      PsiElement method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class, PsiLambdaExpression.class);
      if (method instanceof PsiMethod psiMethod) {
        expectedType = psiMethod.getReturnType();
      }
    }
    else if (parent instanceof PsiExpressionList) {
      PsiElement pParent = parent.getParent();
      if (pParent instanceof PsiCallExpression callExpression) {
        PsiExpressionList argumentList = callExpression.getArgumentList();
        if (parent.equals(argumentList)) {
          PsiMethod method = callExpression.resolveMethod();
          if (method != null) {
            PsiExpression[] expressions = argumentList.getExpressions();
            int idx = ArrayUtilRt.find(expressions, newExpression);
            if (idx > -1) {
              PsiParameter parameter = method.getParameterList().getParameter(idx);
              if (parameter != null) {
                expectedType = parameter.getType();
              }
            }
          }
        }
      }
    }
    return expectedType;
  }

  private static boolean hasSuperMethodsWithTypeParams(@NotNull PsiMethod method) {
    for (PsiMethod superMethod : method.findDeepestSuperMethods()) {
      if (superMethod.hasTypeParameters()) return true;
    }
    return false;
  }

  void checkGenericArrayCreation(@NotNull PsiElement element, @Nullable PsiType type) {
    if (type instanceof PsiArrayType arrayType) {
      if (element instanceof PsiNewExpression newExpression) {
        PsiReferenceParameterList typeArgumentList = newExpression.getTypeArgumentList();
        if (typeArgumentList.getTypeArgumentCount() > 0) {
          myVisitor.report(JavaErrorKinds.ARRAY_TYPE_ARGUMENTS.create(typeArgumentList));
          return;
        }
        PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
        if (classReference != null) {
          PsiReferenceParameterList parameterList = classReference.getParameterList();
          if (parameterList != null) {
            PsiTypeElement[] typeParameterElements = parameterList.getTypeParameterElements();
            if (typeParameterElements.length == 1 && typeParameterElements[0].getType() instanceof PsiDiamondType) {
              myVisitor.report(JavaErrorKinds.ARRAY_EMPTY_DIAMOND.create(parameterList));
              return;
            }
            if (typeParameterElements.length >= 1 && !JavaGenericsUtil.isReifiableType(arrayType.getComponentType())) {
              myVisitor.report(JavaErrorKinds.ARRAY_GENERIC.create(parameterList));
              return;
            }
          }
        }
      }
      if (!JavaGenericsUtil.isReifiableType(arrayType.getComponentType())) {
        if (element.getParent() instanceof PsiMethodReferenceExpression && element.getFirstChild() instanceof PsiTypeElement typeElement) {
          PsiJavaCodeReferenceElement referenceElement = PsiTreeUtil.findChildOfType(typeElement, PsiJavaCodeReferenceElement.class);
          if (referenceElement != null) {
            PsiReferenceParameterList parameterList = referenceElement.getParameterList();
            if (parameterList != null && parameterList.getTypeArgumentCount() > 0) {
              myVisitor.report(JavaErrorKinds.ARRAY_GENERIC.create(parameterList));
              return;
            }
          }
        }
        myVisitor.report(JavaErrorKinds.ARRAY_GENERIC.create(element));
      }
    }
  }

  Map<PsiMember, JavaCompilationError<PsiMember, ?>> computeOverrideEquivalentMethodErrors(@NotNull PsiClass aClass) {
    if (myOverrideEquivalentMethodsVisitedClasses.add(aClass)) {
      Collection<HierarchicalMethodSignature> signaturesWithSupers = aClass.getVisibleSignatures();
      PsiManager manager = aClass.getManager();
      Map<MethodSignature, MethodSignatureBackedByPsiMethod> sameErasureMethods =
        MethodSignatureUtil.createErasedMethodSignatureMap();

      Set<MethodSignature> foundProblems = MethodSignatureUtil.createErasedMethodSignatureSet();
      for (HierarchicalMethodSignature signature : signaturesWithSupers) {
        JavaCompilationError<PsiMember, ?> error =
          checkSameErasureNotSubSignatureInner(signature, manager, aClass, sameErasureMethods);
        if (error != null && foundProblems.add(signature)) {
          myOverrideEquivalentMethodsErrors.put(error.psi(), error);
        }
        if (aClass instanceof PsiTypeParameter) {
          error = MethodChecker.getMethodIncompatibleReturnType(aClass, signature, signature.getSuperSignatures());
          if (error != null) {
            myOverrideEquivalentMethodsErrors.put(aClass, error);
          }
        }
      }
    }
    return myOverrideEquivalentMethodsErrors;
  }

  private static JavaCompilationError<PsiMember, ?> checkSameErasureNotSubSignatureInner(
    @NotNull HierarchicalMethodSignature signature,
    @NotNull PsiManager manager,
    @NotNull PsiClass aClass,
    @NotNull Map<MethodSignature, MethodSignatureBackedByPsiMethod> sameErasureMethods) {
    PsiMethod method = signature.getMethod();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    if (!facade.getResolveHelper().isAccessible(method, aClass, null)) return null;
    MethodSignature signatureToErase = method.getSignature(PsiSubstitutor.EMPTY);
    MethodSignatureBackedByPsiMethod sameErasure = sameErasureMethods.get(signatureToErase);
    if (sameErasure == null) {
      sameErasureMethods.put(signatureToErase, signature);
    }
    else if (aClass instanceof PsiTypeParameter ||
             MethodSignatureUtil.findMethodBySuperMethod(aClass, sameErasure.getMethod(), false) != null ||
             !(InheritanceUtil.isInheritorOrSelf(sameErasure.getMethod().getContainingClass(), method.getContainingClass(), true) ||
               InheritanceUtil.isInheritorOrSelf(method.getContainingClass(), sameErasure.getMethod().getContainingClass(), true))) {
      JavaCompilationError<PsiMember, ?> error = checkSameErasureNotSubSignatureOrSameClass(sameErasure, signature, aClass, method);
      if (error != null) return error;
    }
    List<HierarchicalMethodSignature> supers = signature.getSuperSignatures();
    for (HierarchicalMethodSignature superSignature : supers) {
      JavaCompilationError<PsiMember, ?> error =
        checkSameErasureNotSubSignatureInner(superSignature, manager, aClass, sameErasureMethods);
      if (error != null) return error;

      if (superSignature.isRaw() && !signature.isRaw()) {
        PsiType[] parameterTypes = signature.getParameterTypes();
        PsiType[] erasedTypes = superSignature.getErasedParameterTypes();
        for (int i = 0; i < erasedTypes.length; i++) {
          if (!Comparing.equal(parameterTypes[i], erasedTypes[i])) {
            return getSameErasureMessage(method, superSignature.getMethod(), aClass);
          }
        }
      }
    }
    return null;
  }

  private static JavaCompilationError<PsiMember, ?> checkSameErasureNotSubSignatureOrSameClass(@NotNull MethodSignatureBackedByPsiMethod signatureToCheck,
                                                                                               @NotNull HierarchicalMethodSignature superSignature,
                                                                                               @NotNull PsiClass aClass,
                                                                                               @NotNull PsiMethod superMethod) {
    PsiMethod checkMethod = signatureToCheck.getMethod();
    if (superMethod.equals(checkMethod)) return null;
    PsiClass checkContainingClass = requireNonNull(checkMethod.getContainingClass());
    PsiClass superContainingClass = superMethod.getContainingClass();
    boolean checkEqualsSuper = checkContainingClass.equals(superContainingClass);
    if (checkMethod.isConstructor()) {
      if (!superMethod.isConstructor() || !checkEqualsSuper) return null;
    }
    else if (superMethod.isConstructor()) return null;

    JavaVersionService javaVersionService = JavaVersionService.getInstance();
    boolean atLeast17 = javaVersionService.isAtLeast(aClass, JavaSdkVersion.JDK_1_7);
    if (checkMethod.hasModifierProperty(PsiModifier.STATIC) && !checkEqualsSuper && !atLeast17) {
      return null;
    }

    if (superMethod.hasModifierProperty(PsiModifier.STATIC) && superContainingClass != null &&
        superContainingClass.isInterface() && !checkEqualsSuper &&
        PsiUtil.isAvailable(JavaFeature.STATIC_INTERFACE_CALLS, superContainingClass)) {
      return null;
    }

    PsiType retErasure1 = TypeConversionUtil.erasure(checkMethod.getReturnType());
    PsiType retErasure2 = TypeConversionUtil.erasure(superMethod.getReturnType());

    boolean differentReturnTypeErasure = !Comparing.equal(retErasure1, retErasure2);
    if (checkEqualsSuper && atLeast17 && retErasure1 != null && retErasure2 != null) {
      differentReturnTypeErasure = !TypeConversionUtil.isAssignable(retErasure1, retErasure2);
    }

    if (differentReturnTypeErasure &&
        !TypeConversionUtil.isVoidType(retErasure1) &&
        !TypeConversionUtil.isVoidType(retErasure2) &&
        !(checkEqualsSuper && Arrays.equals(superSignature.getParameterTypes(), signatureToCheck.getParameterTypes())) &&
        !atLeast17) {
      int idx = 0;
      PsiType[] erasedTypes = signatureToCheck.getErasedParameterTypes();
      boolean erasure = erasedTypes.length > 0;
      for (PsiType type : superSignature.getParameterTypes()) {
        erasure &= Comparing.equal(type, erasedTypes[idx]);
        idx++;
      }

      if (!erasure) return null;
    }

    if (!checkEqualsSuper && MethodSignatureUtil.isSubsignature(superSignature, signatureToCheck)) {
      return null;
    }
    if (!javaVersionService.isCompilerVersionAtLeast(aClass, JavaSdkVersion.JDK_1_7)) {
      //javac <= 1.6 didn't check transitive overriding rules for interfaces
      if (superContainingClass != null &&
          !superContainingClass.isInterface() &&
          checkContainingClass.isInterface() &&
          !aClass.equals(superContainingClass)) {
        return null;
      }
    }
    PsiMember anchor = aClass.equals(checkContainingClass) ? checkMethod : aClass;
    return getSameErasureMessage(checkMethod, superMethod, anchor);
  }

  private static JavaCompilationError<PsiMember, ?> getSameErasureMessage(@NotNull PsiMethod method,
                                                                          @NotNull PsiMethod superMethod,
                                                                          @NotNull PsiMember anchor) {
    return JavaErrorKinds.METHOD_GENERIC_CLASH.create(anchor, new JavaErrorKinds.OverrideClashContext(method, superMethod));
  }

  void checkTypeParameterOverrideEquivalentMethods(@NotNull PsiClass typeParameter) {
    if (typeParameter instanceof PsiTypeParameter && myVisitor.languageLevel().isAtLeast(LanguageLevel.JDK_1_7)) {
      PsiReferenceList extendsList = typeParameter.getExtendsList();
      if (extendsList.getReferenceElements().length > 1) {
        //todo suppress erased methods which come from the same class
        var error = computeOverrideEquivalentMethodErrors(typeParameter).get(typeParameter);
        if (error != null) {
          myVisitor.report(error);
        }
      }
    }
  }
}

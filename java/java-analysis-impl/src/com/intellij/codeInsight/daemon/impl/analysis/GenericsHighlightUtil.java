/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixActionRegistrarImpl;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author cdr
 */
public class GenericsHighlightUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.analysis.GenericsHighlightUtil");

  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  private GenericsHighlightUtil() { }

  @Nullable
  public static HighlightInfo checkInferredTypeArguments(PsiTypeParameterListOwner listOwner,
                                                         PsiElement call,
                                                         PsiSubstitutor substitutor) {
    final Pair<PsiTypeParameter, PsiType> inferredTypeArgument =
      GenericsUtil.findTypeParameterWithBoundError(listOwner.getTypeParameters(), substitutor, call, false);
    if (inferredTypeArgument != null) {
      final PsiType extendsType = inferredTypeArgument.second;
      final PsiTypeParameter typeParameter = inferredTypeArgument.first;
      PsiClass boundClass = extendsType instanceof PsiClassType ? ((PsiClassType)extendsType).resolve() : null;

      @NonNls String messageKey = boundClass == null || typeParameter.isInterface() == boundClass.isInterface()
                                  ? "generics.inferred.type.for.type.parameter.is.not.within.its.bound.extend"
                                  : "generics.inferred.type.for.type.parameter.is.not.within.its.bound.implement";

      String description = JavaErrorMessages.message(
        messageKey,
        HighlightUtil.formatClass(typeParameter),
        JavaHighlightUtil.formatType(extendsType),
        JavaHighlightUtil.formatType(substitutor.substitute(typeParameter))
      );
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(call).descriptionAndTooltip(description).create();
    }

    return null;
  }

  @Nullable
  public static HighlightInfo checkParameterizedReferenceTypeArguments(final PsiElement resolved,
                                                                       final PsiJavaCodeReferenceElement referenceElement,
                                                                       final PsiSubstitutor substitutor,
                                                                       @NotNull JavaSdkVersion javaSdkVersion) {
    if (!(resolved instanceof PsiTypeParameterListOwner)) return null;
    final PsiTypeParameterListOwner typeParameterListOwner = (PsiTypeParameterListOwner)resolved;
    return checkReferenceTypeArgumentList(typeParameterListOwner, referenceElement.getParameterList(), substitutor, true, javaSdkVersion);
  }

  @Nullable
  public static HighlightInfo checkReferenceTypeArgumentList(final PsiTypeParameterListOwner typeParameterListOwner,
                                                             final PsiReferenceParameterList referenceParameterList,
                                                             final PsiSubstitutor substitutor,
                                                             boolean registerIntentions,
                                                             @NotNull JavaSdkVersion javaSdkVersion) {
    PsiDiamondType.DiamondInferenceResult inferenceResult = null;
    PsiTypeElement[] referenceElements = null;
    if (referenceParameterList != null) {
      referenceElements = referenceParameterList.getTypeParameterElements();
      if (referenceElements.length == 1 && referenceElements[0].getType() instanceof PsiDiamondType) {
        if (!typeParameterListOwner.hasTypeParameters()) {
          final String description = JavaErrorMessages.message("generics.diamond.not.applicable");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(referenceElements[0]).descriptionAndTooltip(description).create();
        }
        inferenceResult = ((PsiDiamondType)referenceElements[0].getType()).resolveInferredTypes();
        final String errorMessage = inferenceResult.getErrorMessage();
        if (errorMessage != null) {
          final PsiType expectedType = detectExpectedType(referenceParameterList);
          if (!(inferenceResult.failedToInfer() && expectedType instanceof PsiClassType && ((PsiClassType)expectedType).isRaw())) {
            return HighlightInfo
              .newHighlightInfo(HighlightInfoType.ERROR).range(referenceElements[0]).descriptionAndTooltip(errorMessage).create();
          }
        }
      }
    }

    final PsiTypeParameter[] typeParameters = typeParameterListOwner.getTypeParameters();
    final int targetParametersNum = typeParameters.length;
    final int refParametersNum = referenceParameterList == null ? 0 : referenceParameterList.getTypeArguments().length;
    if (targetParametersNum != refParametersNum && refParametersNum != 0) {
      final String description;
      if (targetParametersNum == 0) {
        if (PsiTreeUtil.getParentOfType(referenceParameterList, PsiCall.class) != null &&
            typeParameterListOwner instanceof PsiMethod &&
            javaSdkVersion.isAtLeast(JavaSdkVersion.JDK_1_7)) {
          description = null;
        }
        else {
          description = JavaErrorMessages.message(
            "generics.type.or.method.does.not.have.type.parameters",
            typeParameterListOwnerCategoryDescription(typeParameterListOwner),
            typeParameterListOwnerDescription(typeParameterListOwner)
          );
        }
      }
      else {
        description = JavaErrorMessages.message("generics.wrong.number.of.type.arguments", refParametersNum, targetParametersNum);
      }

      if (description != null) {
        final HighlightInfo highlightInfo =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(referenceParameterList).descriptionAndTooltip(description).create();
        if (registerIntentions) {
          PsiElement grandParent = referenceParameterList.getParent().getParent();
          if (grandParent instanceof PsiTypeElement) {
            PsiElement variable = grandParent.getParent();
            if (variable instanceof PsiVariable) {
              if (targetParametersNum == 0) {
                QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createRemoveTypeArgumentsFix(variable));
              }
              if (typeParameterListOwner instanceof PsiClass) {
                QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createChangeClassSignatureFromUsageFix((PsiClass)typeParameterListOwner, referenceParameterList));
              }
              registerVariableParameterizedTypeFixes(highlightInfo, (PsiVariable)variable, referenceParameterList, javaSdkVersion);
            }
          }
        }
        return highlightInfo;
      }
    }

    // bounds check
    if (targetParametersNum > 0 && refParametersNum != 0) {
      if (inferenceResult != null) {
        final PsiType[] types = inferenceResult.getTypes();
        for (int i = 0; i < typeParameters.length; i++) {
          final PsiType type = types[i];
          final HighlightInfo highlightInfo = checkTypeParameterWithinItsBound(typeParameters[i], substitutor, type, referenceElements[0], referenceParameterList);
          if (highlightInfo != null) return highlightInfo;
        }
      }
      else {
        for (int i = 0; i < typeParameters.length; i++) {
          final PsiTypeElement typeElement = referenceElements[i];
          final HighlightInfo highlightInfo = checkTypeParameterWithinItsBound(typeParameters[i], substitutor, typeElement.getType(), typeElement, referenceParameterList);
          if (highlightInfo != null) return highlightInfo;
        }
      }
    }

    return null;
  }

  private static PsiType detectExpectedType(PsiReferenceParameterList referenceParameterList) {
    final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(referenceParameterList, PsiNewExpression.class);
    LOG.assertTrue(newExpression != null);
    final PsiElement parent = newExpression.getParent();
    PsiType expectedType = null;
    if (parent instanceof PsiVariable && newExpression.equals(((PsiVariable)parent).getInitializer())) {
      expectedType = ((PsiVariable)parent).getType();
    }
    else if (parent instanceof PsiAssignmentExpression && newExpression.equals(((PsiAssignmentExpression)parent).getRExpression())) {
      expectedType = ((PsiAssignmentExpression)parent).getLExpression().getType();
    }
    else if (parent instanceof PsiReturnStatement) {
      PsiMethod method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class);
      if (method != null) {
        expectedType = method.getReturnType();
      }
    }
    else if (parent instanceof PsiExpressionList) {
      final PsiElement pParent = parent.getParent();
      if (pParent instanceof PsiCallExpression && parent.equals(((PsiCallExpression)pParent).getArgumentList())) {
        final PsiMethod method = ((PsiCallExpression)pParent).resolveMethod();
        if (method != null) {
          final PsiExpression[] expressions = ((PsiCallExpression)pParent).getArgumentList().getExpressions();
          final int idx = ArrayUtilRt.find(expressions, newExpression);
          if (idx > -1) {
            final PsiParameterList parameterList = method.getParameterList();
            if (idx < parameterList.getParametersCount()) {
              expectedType = parameterList.getParameters()[idx].getType();
            }
          }
        }
      }
    }
    return expectedType;
  }

  @Nullable
  private static HighlightInfo checkTypeParameterWithinItsBound(PsiTypeParameter classParameter,
                                                                final PsiSubstitutor substitutor,
                                                                final PsiType type,
                                                                final PsiElement typeElement2Highlight,
                                                                PsiReferenceParameterList referenceParameterList) {
    final PsiClass referenceClass = type instanceof PsiClassType ? ((PsiClassType)type).resolve() : null;
    final PsiType psiType = substitutor.substitute(classParameter);
    if (psiType instanceof PsiClassType && !(PsiUtil.resolveClassInType(psiType) instanceof PsiTypeParameter)) {
      if (checkNotInBounds(type, psiType, referenceParameterList)) {
        final String description = "Actual type argument and inferred type contradict each other";
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement2Highlight).descriptionAndTooltip(description).create();
      }
    }

    final PsiClassType[] bounds = classParameter.getSuperTypes();
    for (PsiClassType type1 : bounds) {
      PsiType bound = substitutor.substitute(type1);
      if (!bound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) && checkNotInBounds(type, bound, referenceParameterList)) {
        PsiClass boundClass = bound instanceof PsiClassType ? ((PsiClassType)bound).resolve() : null;

        @NonNls final String messageKey = boundClass == null || referenceClass == null || referenceClass.isInterface() == boundClass.isInterface()
                                          ? "generics.type.parameter.is.not.within.its.bound.extend"
                                          : "generics.type.parameter.is.not.within.its.bound.implement";

        String description = JavaErrorMessages.message(messageKey,
                                                       referenceClass != null ? HighlightUtil.formatClass(referenceClass) : type.getPresentableText(),
                                                       JavaHighlightUtil.formatType(bound));

        final HighlightInfo info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement2Highlight).descriptionAndTooltip(description).create();
        if (bound instanceof PsiClassType && referenceClass != null && info != null) {
          QuickFixAction
            .registerQuickFixAction(info, QUICK_FIX_FACTORY.createExtendsListFix(referenceClass, (PsiClassType)bound, true),
                                    null);
        }
        return info;
      }
    }
    return null;
  }

  private static boolean checkNotInBounds(PsiType type, PsiType bound, PsiReferenceParameterList referenceParameterList) {
    if (type instanceof PsiClassType) {
      return checkNotAssignable(bound, type, allowUncheckedConversions((PsiClassType)type, referenceParameterList));
    }
    if (type instanceof PsiWildcardType) {
      if (((PsiWildcardType)type).isExtends()) {
        return checkExtendsWildcardCaptureFailure((PsiWildcardType)type, bound);
      }
      else if (((PsiWildcardType)type).isSuper()) {
        final PsiType superBound = ((PsiWildcardType)type).getSuperBound();
        if (PsiUtil.resolveClassInType(superBound) instanceof PsiTypeParameter) return TypesDistinctProver.provablyDistinct(type, bound);
        return checkNotAssignable(bound, superBound, false);
      }
    }
    else if (type instanceof PsiArrayType) {
      return checkNotAssignable(bound, type, true);
    }
    return false;
  }

  //JLS 5.1.10
  private static boolean checkExtendsWildcardCaptureFailure(PsiWildcardType type, PsiType bound) {
    LOG.assertTrue(type.isExtends());
    final PsiType extendsBound = type.getExtendsBound();
    PsiType boundBound = bound;
    if (bound instanceof PsiWildcardType) {
      if (((PsiWildcardType)bound).isBounded()) {
        boundBound = ((PsiWildcardType)bound).isSuper()
                     ? ((PsiWildcardType)bound).getSuperBound()
                     : ((PsiWildcardType)bound).getExtendsBound();
      }
      else {
        return false;
      }
    }
    return !TypeConversionUtil.areTypesConvertible(boundBound, extendsBound) &&
           !TypeConversionUtil.areTypesConvertible(extendsBound, boundBound);
  }

  private static boolean checkNotAssignable(final PsiType bound,
                                            final PsiType type,
                                            final boolean allowUncheckedConversion) {
    if (bound instanceof PsiWildcardType) {
      if (((PsiWildcardType)bound).isBounded()) {
        final PsiType boundBound = ((PsiWildcardType)bound).isExtends()
                                   ? ((PsiWildcardType)bound).getExtendsBound()
                                   : ((PsiWildcardType)bound).getSuperBound();
        return !TypeConversionUtil.isAssignable(boundBound, type, allowUncheckedConversion);
      }
      else {
        return true;
      }
    }
    else {
      return !TypeConversionUtil.isAssignable(bound, type, allowUncheckedConversion);
    }
  }

  private static boolean allowUncheckedConversions(PsiClassType type, PsiReferenceParameterList referenceParameterList) {
    final PsiClass psiClass = type.resolve();
    if (psiClass != null) {
      for (PsiTypeParameter parameter : PsiUtil.typeParametersIterable(psiClass)) {
        if (parameter.getExtendsListTypes().length != 0) {
          return false;
        }
      }
      if (psiClass instanceof PsiTypeParameter && psiClass.getExtendsListTypes().length != 0) return false;
    }
    if (!type.isRaw()) return true;
    //allow unchecked conversions in method calls but not in type declaration
    return referenceParameterList.getParent() instanceof PsiReferenceExpression;
  }

  private static String typeParameterListOwnerDescription(final PsiTypeParameterListOwner typeParameterListOwner) {
    if (typeParameterListOwner instanceof PsiClass) {
      return HighlightUtil.formatClass((PsiClass)typeParameterListOwner);
    }
    else if (typeParameterListOwner instanceof PsiMethod) {
      return JavaHighlightUtil.formatMethod((PsiMethod)typeParameterListOwner);
    }
    else {
      LOG.error("Unknown " + typeParameterListOwner);
      return "?";
    }
  }

  private static String typeParameterListOwnerCategoryDescription(final PsiTypeParameterListOwner typeParameterListOwner) {
    if (typeParameterListOwner instanceof PsiClass) {
      return JavaErrorMessages.message("generics.holder.type");
    }
    else if (typeParameterListOwner instanceof PsiMethod) {
      return JavaErrorMessages.message("generics.holder.method");
    }
    else {
      LOG.error("Unknown " + typeParameterListOwner);
      return "?";
    }
  }

  @Nullable
  public static HighlightInfo checkElementInTypeParameterExtendsList(@NotNull PsiReferenceList referenceList,
                                                                     @NotNull PsiClass aClass,
                                                                     @NotNull JavaResolveResult resolveResult,
                                                                     @NotNull PsiElement element,
                                                                     @NotNull LanguageLevel languageLevel) {
    final PsiJavaCodeReferenceElement[] referenceElements = referenceList.getReferenceElements();
    PsiClass extendFrom = (PsiClass)resolveResult.getElement();
    if (extendFrom == null) return null;
    HighlightInfo errorResult = null;
    if (!extendFrom.isInterface() && referenceElements.length != 0 && element != referenceElements[0]) {
      String description = JavaErrorMessages.message("interface.expected");
      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
      PsiClassType type =
        JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(extendFrom, resolveResult.getSubstitutor());
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createMoveBoundClassToFrontFix(aClass, type), null);
    }
    else if (referenceElements.length != 0 && element != referenceElements[0] && referenceElements[0].resolve() instanceof PsiTypeParameter) {
      final String description = JavaErrorMessages.message("type.parameter.cannot.be.followed.by.other.bounds");
      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
      PsiClassType type =
        JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType(extendFrom, resolveResult.getSubstitutor());
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createExtendsListFix(aClass, type, false), null);
    }
    if (errorResult == null && languageLevel.isAtLeast(LanguageLevel.JDK_1_7) &&
        referenceElements.length > 1) {
      //todo suppress erased methods which come from the same class
      return checkOverrideEquivalentMethods(aClass);
    }
    return errorResult;
  }

  public static HighlightInfo checkInterfaceMultipleInheritance(PsiClass aClass) {
    final PsiClassType[] types = aClass.getSuperTypes();
    if (types.length < 2) return null;
    Map<PsiClass, PsiSubstitutor> inheritedClasses = new HashMap<PsiClass, PsiSubstitutor>();
    final TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
    return checkInterfaceMultipleInheritance(aClass,
                                             PsiSubstitutor.EMPTY, inheritedClasses,
                                             new HashSet<PsiClass>(), textRange);
  }

  private static HighlightInfo checkInterfaceMultipleInheritance(PsiClass aClass,
                                                                 PsiSubstitutor derivedSubstitutor,
                                                                 Map<PsiClass, PsiSubstitutor> inheritedClasses,
                                                                 Set<PsiClass> visited,
                                                                 TextRange textRange) {
    final PsiClassType[] superTypes = aClass.getSuperTypes();
    for (PsiClassType superType : superTypes) {
      final PsiClassType.ClassResolveResult result = superType.resolveGenerics();
      final PsiClass superClass = result.getElement();
      if (superClass == null || visited.contains(superClass)) continue;
      PsiSubstitutor superTypeSubstitutor = result.getSubstitutor();
      superTypeSubstitutor = MethodSignatureUtil.combineSubstitutors(superTypeSubstitutor, derivedSubstitutor);

      final PsiSubstitutor inheritedSubstitutor = inheritedClasses.get(superClass);
      if (inheritedSubstitutor != null) {
        final PsiTypeParameter[] typeParameters = superClass.getTypeParameters();
        for (PsiTypeParameter typeParameter : typeParameters) {
          PsiType type1 = GenericsUtil.eliminateWildcards(inheritedSubstitutor.substitute(typeParameter));
          PsiType type2 = GenericsUtil.eliminateWildcards(superTypeSubstitutor.substitute(typeParameter));

          if (!Comparing.equal(type1, type2)) {
            String description = JavaErrorMessages.message("generics.cannot.be.inherited.with.different.type.arguments",
                                                           HighlightUtil.formatClass(superClass),
                                                           JavaHighlightUtil.formatType(type1),
                                                           JavaHighlightUtil.formatType(type2));
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
          }
        }
      }
      inheritedClasses.put(superClass, superTypeSubstitutor);
      visited.add(superClass);
      final HighlightInfo highlightInfo = checkInterfaceMultipleInheritance(superClass, superTypeSubstitutor, inheritedClasses, visited, textRange);
      visited.remove(superClass);

      if (highlightInfo != null) return highlightInfo;
    }
    return null;
  }

  public static HighlightInfo checkOverrideEquivalentMethods(@NotNull PsiClass aClass) {
    final Collection<HierarchicalMethodSignature> signaturesWithSupers = aClass.getVisibleSignatures();
    PsiManager manager = aClass.getManager();
    Map<MethodSignature, MethodSignatureBackedByPsiMethod> sameErasureMethods =
      new THashMap<MethodSignature, MethodSignatureBackedByPsiMethod>(MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY);

    for (HierarchicalMethodSignature signature : signaturesWithSupers) {
      HighlightInfo info = checkSameErasureNotSubSignatureInner(signature, manager, aClass, sameErasureMethods);
      if (info != null) return info;
      if (aClass instanceof PsiTypeParameter) {
        info = HighlightMethodUtil.checkMethodIncompatibleReturnType(signature, signature.getSuperSignatures(), true, HighlightNamesUtil.getClassDeclarationTextRange(aClass));
        if (info != null) return info;
      }
    }

    final PsiIdentifier classIdentifier = aClass.getNameIdentifier();
    if (PsiUtil.isLanguageLevel8OrHigher(aClass) && classIdentifier != null) {
      HighlightInfo info = checkUnrelatedDefaultMethods(aClass, signaturesWithSupers, classIdentifier);
      if (info != null) return info;
      info = checkDefaultMethodOverrideEquivalentToObjectNonPrivate(aClass, signaturesWithSupers);
      if (info != null) return info;
    }

    return null;
  }

  private static HighlightInfo checkDefaultMethodOverrideEquivalentToObjectNonPrivate(PsiClass aClass,
                                                                                      Collection<HierarchicalMethodSignature> withSupers) {
    if (aClass.isInterface()) {
      for (HierarchicalMethodSignature sig : withSupers) {
        final PsiMethod method = sig.getMethod();
        if (method.hasModifierProperty(PsiModifier.DEFAULT)) {
          for (HierarchicalMethodSignature methodSignature : sig.getSuperSignatures()) {
            final PsiClass containingClass = methodSignature.getMethod().getContainingClass();
            if (containingClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
              final PsiIdentifier identifier = method.getNameIdentifier();
              LOG.assertTrue(identifier != null);
              return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                .descriptionAndTooltip("Default method " + sig.getName() + " overrides a member of java.lang.Object")
                .range(identifier)
                .create();
            }
          }
        }
      }
    }
    return null;
  }

  private static HighlightInfo checkUnrelatedDefaultMethods(PsiClass aClass,
                                                            Collection<HierarchicalMethodSignature> signaturesWithSupers,
                                                            PsiIdentifier classIdentifier) {
    for (HierarchicalMethodSignature methodSignature : signaturesWithSupers) {
      final PsiMethod method = methodSignature.getMethod();
      if (method.hasModifierProperty(PsiModifier.DEFAULT)) {
        final PsiClass containingClass = method.getContainingClass();
        List<HierarchicalMethodSignature> superSignatures = methodSignature.getSuperSignatures();
        if (!superSignatures.isEmpty()) {
          for (HierarchicalMethodSignature signature : superSignatures) {
            final PsiMethod superMethod = signature.getMethod();
            final PsiClass superContainingClass = superMethod.getContainingClass();
            if (containingClass != null && superContainingClass != null && !InheritanceUtil
              .isInheritorOrSelf(containingClass, superContainingClass, true)) {
              final boolean isDefault = superMethod.hasModifierProperty(PsiModifier.DEFAULT);
              if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT) && !isDefault) {
                final String message = JavaErrorMessages.message(
                  aClass instanceof PsiEnumConstantInitializer ? "enum.constant.should.implement.method" : "class.must.be.abstract",
                  HighlightUtil.formatClass(superContainingClass),
                  JavaHighlightUtil.formatMethod(superMethod),
                  HighlightUtil.formatClass(superContainingClass, false));
                return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                  .range(classIdentifier).descriptionAndTooltip(message)
                  .create();
              }

              if (isDefault || superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
                final String message = isDefault
                                       ? " inherits unrelated defaults for "
                                       : " inherits abstract and default for ";
                final String inheritUnrelatedDefaultsMessage = HighlightUtil.formatClass(aClass) +
                                                               message +
                                                               JavaHighlightUtil.formatMethod(method) +
                                                               " from types " +
                                                               HighlightUtil.formatClass(containingClass) +
                                                               " and " +
                                                               HighlightUtil.formatClass(superContainingClass);
                return HighlightInfo
                  .newHighlightInfo(HighlightInfoType.ERROR).range(classIdentifier).descriptionAndTooltip(inheritUnrelatedDefaultsMessage)
                  .create();
              }
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static HighlightInfo checkSameErasureNotSubSignatureInner(@NotNull HierarchicalMethodSignature signature,
                                                                    @NotNull PsiManager manager,
                                                                    @NotNull PsiClass aClass,
                                                                    @NotNull Map<MethodSignature, MethodSignatureBackedByPsiMethod> sameErasureMethods) {
    PsiMethod method = signature.getMethod();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    if (!facade.getResolveHelper().isAccessible(method, aClass, null)) return null;
    MethodSignature signatureToErase = method.getSignature(PsiSubstitutor.EMPTY);
    MethodSignatureBackedByPsiMethod sameErasure = sameErasureMethods.get(signatureToErase);
    HighlightInfo info;
    if (sameErasure != null) {
      if (aClass instanceof PsiTypeParameter ||
          MethodSignatureUtil.findMethodBySuperMethod(aClass, sameErasure.getMethod(), false) != null ||
          !(InheritanceUtil.isInheritorOrSelf(sameErasure.getMethod().getContainingClass(), method.getContainingClass(), true) ||
            InheritanceUtil.isInheritorOrSelf(method.getContainingClass(), sameErasure.getMethod().getContainingClass(), true))) {
        info = checkSameErasureNotSubSignatureOrSameClass(sameErasure, signature, aClass, method);
        if (info != null) return info;
      }
    }
    else {
      sameErasureMethods.put(signatureToErase, signature);
    }
    List<HierarchicalMethodSignature> supers = signature.getSuperSignatures();
    for (HierarchicalMethodSignature superSignature : supers) {
      info = checkSameErasureNotSubSignatureInner(superSignature, manager, aClass, sameErasureMethods);
      if (info != null) return info;

      if (superSignature.isRaw() && !signature.isRaw()) {
        final PsiType[] parameterTypes = signature.getParameterTypes();
        PsiType[] erasedTypes = superSignature.getErasedParameterTypes();
        for (int i = 0; i < erasedTypes.length; i++) {
          if (!Comparing.equal(parameterTypes[i], erasedTypes[i])) {
            return getSameErasureMessage(false, method, superSignature.getMethod(),
                                         HighlightNamesUtil.getClassDeclarationTextRange(aClass));
          }
        }
      }

    }
    return null;
  }

  @Nullable
  private static HighlightInfo checkSameErasureNotSubSignatureOrSameClass(final MethodSignatureBackedByPsiMethod signatureToCheck,
                                                                          final HierarchicalMethodSignature superSignature,
                                                                          final PsiClass aClass,
                                                                          final PsiMethod superMethod) {
    final PsiMethod checkMethod = signatureToCheck.getMethod();
    if (superMethod.equals(checkMethod)) return null;
    PsiClass checkContainingClass = checkMethod.getContainingClass();
    LOG.assertTrue(checkContainingClass != null);
    PsiClass superContainingClass = superMethod.getContainingClass();
    boolean checkEqualsSuper = checkContainingClass.equals(superContainingClass);
    if (checkMethod.isConstructor()) {
      if (!superMethod.isConstructor() || !checkEqualsSuper) return null;
    }
    else if (superMethod.isConstructor()) return null;

    final boolean atLeast17 = JavaVersionService.getInstance().isAtLeast(aClass, JavaSdkVersion.JDK_1_7);
    if (checkMethod.hasModifierProperty(PsiModifier.STATIC) && !checkEqualsSuper && !atLeast17) {
      return null;
    }

    final PsiType retErasure1 = TypeConversionUtil.erasure(checkMethod.getReturnType());
    final PsiType retErasure2 = TypeConversionUtil.erasure(superMethod.getReturnType());

    boolean differentReturnTypeErasure = !Comparing.equal(retErasure1, retErasure2);
    if (checkEqualsSuper && atLeast17) {
      if (retErasure1 != null && retErasure2 != null) {
        differentReturnTypeErasure = !TypeConversionUtil.isAssignable(retErasure1, retErasure2);
      }
      else {
        differentReturnTypeErasure = !(retErasure1 == null && retErasure2 == null);
      }
    }

    if (differentReturnTypeErasure &&
        !TypeConversionUtil.isVoidType(retErasure1) &&
        !TypeConversionUtil.isVoidType(retErasure2) &&
        !(checkEqualsSuper && Arrays.equals(superSignature.getParameterTypes(), signatureToCheck.getParameterTypes())) &&
        !atLeast17) {
      int idx = 0;
      final PsiType[] erasedTypes = signatureToCheck.getErasedParameterTypes();
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
    if (superContainingClass != null && !superContainingClass.isInterface() && checkContainingClass.isInterface() && !aClass.equals(superContainingClass)) return null;
    if (aClass.equals(checkContainingClass)) {
      boolean sameClass = aClass.equals(superContainingClass);
      return getSameErasureMessage(sameClass, checkMethod, superMethod, HighlightNamesUtil.getMethodDeclarationTextRange(checkMethod));
    }
    else {
      return getSameErasureMessage(false, checkMethod, superMethod, HighlightNamesUtil.getClassDeclarationTextRange(aClass));
    }
  }

  private static HighlightInfo getSameErasureMessage(final boolean sameClass, @NotNull PsiMethod method, @NotNull PsiMethod superMethod,
                                                     TextRange textRange) {
     @NonNls final String key = sameClass ? "generics.methods.have.same.erasure" :
                               method.hasModifierProperty(PsiModifier.STATIC) ?
                               "generics.methods.have.same.erasure.hide" :
                               "generics.methods.have.same.erasure.override";
    String description = JavaErrorMessages.message(key, HighlightMethodUtil.createClashMethodMessage(method, superMethod, !sameClass));
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
  }

  public static HighlightInfo checkTypeParameterInstantiation(PsiNewExpression expression) {
    PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
    if (classReference == null) return null;
    final JavaResolveResult result = classReference.advancedResolve(false);
    final PsiElement element = result.getElement();
    if (element instanceof PsiTypeParameter) {
      String description = JavaErrorMessages.message("generics.type.parameter.cannot.be.instantiated",
                                                     HighlightUtil.formatClass((PsiTypeParameter)element));
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(classReference).descriptionAndTooltip(description).create();
    }
    return null;
  }

  public static HighlightInfo checkWildcardUsage(PsiTypeElement typeElement) {
    PsiType type = typeElement.getType();
    if (type instanceof PsiWildcardType) {
      if (typeElement.getParent() instanceof PsiReferenceParameterList) {
        PsiElement parent = typeElement.getParent().getParent();
        LOG.assertTrue(parent instanceof PsiJavaCodeReferenceElement, parent);
        PsiElement refParent = parent.getParent();
        if (refParent instanceof PsiAnonymousClass) refParent = refParent.getParent();
        if (refParent instanceof PsiNewExpression) {
          PsiNewExpression newExpression = (PsiNewExpression)refParent;
          if (!(newExpression.getType() instanceof PsiArrayType)) {
            String description = JavaErrorMessages.message("wildcard.type.cannot.be.instantiated", JavaHighlightUtil.formatType(type));
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description).create();
          }
        }
        else if (refParent instanceof PsiReferenceList) {
          PsiElement refPParent = refParent.getParent();
          if (!(refPParent instanceof PsiTypeParameter) || refParent != ((PsiTypeParameter)refPParent).getExtendsList()) {
            String description = JavaErrorMessages.message("generics.wildcard.not.expected");
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description).create();
          }
        }
      }
      else {
        String description = JavaErrorMessages.message("generics.wildcards.may.be.used.only.as.reference.parameters");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description).create();
      }
    }

    return null;
  }

  public static HighlightInfo checkReferenceTypeUsedAsTypeArgument(final PsiTypeElement typeElement) {
    final PsiType type = typeElement.getType();
    if (type != PsiType.NULL && type instanceof PsiPrimitiveType ||
        type instanceof PsiWildcardType && ((PsiWildcardType)type).getBound() instanceof PsiPrimitiveType) {
      final PsiElement element = new PsiMatcherImpl(typeElement)
        .parent(PsiMatchers.hasClass(PsiReferenceParameterList.class))
        .parent(PsiMatchers.hasClass(PsiJavaCodeReferenceElement.class, PsiNewExpression.class))
        .getElement();
      if (element == null) return null;

      String description = JavaErrorMessages.message("generics.type.argument.cannot.be.of.primitive.type");
      final HighlightInfo highlightInfo =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description).create();
      PsiType toConvert = type;
      if (type instanceof PsiWildcardType) {
        toConvert = ((PsiWildcardType)type).getBound();
      }
      if (toConvert instanceof PsiPrimitiveType) {
        final PsiClassType boxedType = ((PsiPrimitiveType)toConvert).getBoxedType(typeElement);
        if (boxedType != null) {
          QuickFixAction.registerQuickFixAction(highlightInfo,
                                                QUICK_FIX_FACTORY.createReplacePrimitiveWithBoxedTypeAction(typeElement, toConvert.getPresentableText(), ((PsiPrimitiveType)toConvert).getBoxedTypeName()));
        }
      }
      return highlightInfo;
    }

    return null;
  }

  @Nullable
  public static HighlightInfo checkForeachLoopParameterType(PsiForeachStatement statement) {
    final PsiParameter parameter = statement.getIterationParameter();
    final PsiExpression expression = statement.getIteratedValue();
    if (expression == null || expression.getType() == null) return null;
    final PsiType itemType = JavaGenericsUtil.getCollectionItemType(expression);
    if (itemType == null) {
      String description = JavaErrorMessages.message("foreach.not.applicable",
                                                     JavaHighlightUtil.formatType(expression.getType()));
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create();
    }
    final int start = parameter.getTextRange().getStartOffset();
    final int end = expression.getTextRange().getEndOffset();
    final PsiType parameterType = parameter.getType();
    HighlightInfo highlightInfo = HighlightUtil.checkAssignability(parameterType, itemType, null, new TextRange(start, end), 0);
    if (highlightInfo != null) {
      HighlightUtil.registerChangeVariableTypeFixes(parameter, itemType, highlightInfo);
    }
    return highlightInfo;
  }

  //http://docs.oracle.com/javase/specs/jls/se7/html/jls-8.html#jls-8.9.2
  @Nullable
  public static HighlightInfo checkAccessStaticFieldFromEnumConstructor(@NotNull PsiReferenceExpression expr, @NotNull JavaResolveResult result) {
    final PsiElement resolved = result.getElement();

    if (!(resolved instanceof PsiField)) return null;
    if (!((PsiModifierListOwner)resolved).hasModifierProperty(PsiModifier.STATIC)) return null;
    if (expr.getParent() instanceof PsiSwitchLabelStatement) return null;
    final PsiMember constructorOrInitializer = PsiUtil.findEnclosingConstructorOrInitializer(expr);
    if (constructorOrInitializer == null) return null;
    if (constructorOrInitializer.hasModifierProperty(PsiModifier.STATIC)) return null;
    final PsiClass aClass = constructorOrInitializer.getContainingClass();
    if (aClass == null || !(aClass.isEnum() || aClass instanceof PsiEnumConstantInitializer)) return null;
    final PsiField field = (PsiField)resolved;
    if (aClass instanceof PsiEnumConstantInitializer) {
      if (field.getContainingClass() != aClass.getSuperClass()) return null;
    } else if (field.getContainingClass() != aClass) return null;


    if (!JavaVersionService.getInstance().isAtLeast(field, JavaSdkVersion.JDK_1_6)) {
      final PsiType type = field.getType();
      if (type instanceof PsiClassType && ((PsiClassType)type).resolve() == aClass) return null;
    }

    if (PsiUtil.isCompileTimeConstant(field)) return null;

    String description = JavaErrorMessages.message(
      "illegal.to.access.static.member.from.enum.constructor.or.instance.initializer",
      HighlightMessageUtil.getSymbolName(resolved, result.getSubstitutor())
    );

    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expr).descriptionAndTooltip(description).create();
  }

  @Nullable
  public static HighlightInfo checkEnumInstantiation(PsiNewExpression expression) {
    final PsiType type = expression.getType();
    if (type instanceof PsiClassType) {
      final PsiClass aClass = ((PsiClassType)type).resolve();
      if (aClass != null && aClass.isEnum()) {
        String description = JavaErrorMessages.message("enum.types.cannot.be.instantiated");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create();
      }
    }
    return null;
  }

  @Nullable
  public static HighlightInfo checkGenericArrayCreation(PsiElement element, PsiType type) {
    if (type instanceof PsiArrayType) {
      if (!JavaGenericsUtil.isReifiableType(((PsiArrayType)type).getComponentType())) {
        String description = JavaErrorMessages.message("generic.array.creation");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
      }
    }

    return null;
  }

  private static final MethodSignature ourValuesEnumSyntheticMethod = MethodSignatureUtil.createMethodSignature("values",
                                                                                                                PsiType.EMPTY_ARRAY,
                                                                                                                PsiTypeParameter.EMPTY_ARRAY,
                                                                                                                PsiSubstitutor.EMPTY);

  public static boolean isEnumSyntheticMethod(MethodSignature methodSignature, Project project) {
    if (methodSignature.equals(ourValuesEnumSyntheticMethod)) return true;
    final PsiType javaLangString = PsiType.getJavaLangString(PsiManager.getInstance(project), GlobalSearchScope.allScope(project));
    final MethodSignature valueOfMethod = MethodSignatureUtil.createMethodSignature("valueOf", new PsiType[]{javaLangString}, PsiTypeParameter.EMPTY_ARRAY,
                                                                                    PsiSubstitutor.EMPTY);
    return valueOfMethod.equals(methodSignature);
  }

  @Nullable
  public static HighlightInfo checkTypeParametersList(PsiTypeParameterList parameterList, @NotNull LanguageLevel languageLevel,@NotNull PsiFile containingFile) {
    PsiTypeParameter[] typeParameters = parameterList.getTypeParameters();
    if (typeParameters.length == 0) return null;
    HighlightInfo info = HighlightUtil.checkGenericsFeature(parameterList, typeParameters.length, languageLevel, containingFile);
    if (info != null) return info;

    final PsiElement parent = parameterList.getParent();
    if (parent instanceof PsiClass && ((PsiClass)parent).isEnum()) {
      String description = JavaErrorMessages.message("generics.enum.may.not.have.type.parameters");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameterList).descriptionAndTooltip(description).create();
    }
    if (PsiUtil.isAnnotationMethod(parent)) {
      String description = JavaErrorMessages.message("generics.annotation.members.may.not.have.type.parameters");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameterList).descriptionAndTooltip(description).create();
    }
    if (parent instanceof PsiClass && ((PsiClass)parent).isAnnotationType()) {
      String description = JavaErrorMessages.message("annotation.may.not.have.type.parameters");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameterList).descriptionAndTooltip(description).create();
    }

    for (int i = 0; i < typeParameters.length; i++) {
      final PsiTypeParameter typeParameter1 = typeParameters[i];
      final HighlightInfo cyclicInheritance = HighlightClassUtil.checkCyclicInheritance(typeParameter1);
      if (cyclicInheritance != null) return cyclicInheritance;
      String name1 = typeParameter1.getName();
      for (int j = i + 1; j < typeParameters.length; j++) {
        final PsiTypeParameter typeParameter2 = typeParameters[j];
        String name2 = typeParameter2.getName();
        if (Comparing.strEqual(name1, name2)) {
          String message = JavaErrorMessages.message("generics.duplicate.type.parameter", name1);
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeParameter2).descriptionAndTooltip(message).create();
        }
      }
      if (!languageLevel.isAtLeast(LanguageLevel.JDK_1_7)) {
        for (PsiJavaCodeReferenceElement referenceElement : typeParameter1.getExtendsList().getReferenceElements()) {
          final PsiElement resolve = referenceElement.resolve();
          if (resolve instanceof PsiTypeParameter && ArrayUtilRt.find(typeParameters, resolve) > i) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(referenceElement.getTextRange()).descriptionAndTooltip("Illegal forward reference").create();
          }
        }
      }
    }
    return null;
  }

  @Nullable
  public static Collection<HighlightInfo> checkCatchParameterIsClass(PsiParameter parameter) {
    if (!(parameter.getDeclarationScope() instanceof PsiCatchSection)) return null;
    final Collection<HighlightInfo> result = ContainerUtil.newArrayList();

    final List<PsiTypeElement> typeElements = PsiUtil.getParameterTypeElements(parameter);
    for (PsiTypeElement typeElement : typeElements) {
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(typeElement.getType());
      if (aClass instanceof PsiTypeParameter) {
        final String message = JavaErrorMessages.message("generics.cannot.catch.type.parameters");
        result.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message).create());
      }
    }

    return result;
  }

  public static HighlightInfo checkInstanceOfGenericType(PsiInstanceOfExpression expression) {
    final PsiTypeElement checkTypeElement = expression.getCheckType();
    if (checkTypeElement == null) return null;
    PsiElement ref = checkTypeElement.getInnermostComponentReferenceElement();
    while (ref instanceof PsiJavaCodeReferenceElement) {
      final HighlightInfo result = isIllegalForInstanceOf((PsiJavaCodeReferenceElement)ref, checkTypeElement);
      if (result != null) return result;
      ref = ((PsiQualifiedReference)ref).getQualifier();
    }
    return null;
  }

  private static HighlightInfo isIllegalForInstanceOf(PsiJavaCodeReferenceElement ref, final PsiTypeElement typeElement) {
    final PsiElement resolved = ref.resolve();
    if (resolved instanceof PsiTypeParameter) {
      String description = JavaErrorMessages.message("generics.cannot.instanceof.type.parameters");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(ref).descriptionAndTooltip(description).create();
    }

    if (resolved instanceof PsiClass) {
      final PsiClass containingClass = ((PsiClass)resolved).getContainingClass();
      if (containingClass != null &&
          ref.getQualifier() == null &&
          containingClass.getTypeParameters().length > 0 &&
          !((PsiClass)resolved).hasModifierProperty(PsiModifier.STATIC) &&
          ((PsiClass)resolved).getTypeParameters().length == 0) {
        String description = JavaErrorMessages.message("illegal.generic.type.for.instanceof");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description).create();
      }
    }

    final PsiType[] parameters = ref.getTypeParameters();
    for (PsiType parameterType : parameters) {
      if (parameterType != null &&
          !(parameterType instanceof PsiWildcardType && ((PsiWildcardType)parameterType).getBound() == null)) {
        String description = JavaErrorMessages.message("illegal.generic.type.for.instanceof");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description).create();
      }
    }

    return null;
  }

  public static HighlightInfo checkClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
    PsiType type = expression.getOperand().getType();
    if (type instanceof PsiClassType) {
      return canSelectFrom((PsiClassType)type, expression.getOperand());
    }
    if (type instanceof PsiArrayType) {
      final PsiType arrayComponentType = type.getDeepComponentType();
      if (arrayComponentType instanceof PsiClassType) {
        return canSelectFrom((PsiClassType)arrayComponentType, expression.getOperand());
      }
    }

    return null;
  }

  @Nullable
  private static HighlightInfo canSelectFrom(PsiClassType type, PsiTypeElement operand) {
    PsiClass aClass = type.resolve();
    if (aClass instanceof PsiTypeParameter) {
      String description = JavaErrorMessages.message("cannot.select.dot.class.from.type.variable");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(operand).descriptionAndTooltip(description).create();
    }
    if (type.getParameters().length > 0) {
      return HighlightInfo
        .newHighlightInfo(HighlightInfoType.ERROR).range(operand).descriptionAndTooltip("Cannot select from parameterized type").create();
    }
    return null;
  }

  @Nullable
  public static HighlightInfo checkOverrideAnnotation(PsiMethod method, final LanguageLevel languageLevel) {
    PsiModifierList list = method.getModifierList();
    final PsiAnnotation overrideAnnotation = list.findAnnotation("java.lang.Override");
    if (overrideAnnotation == null) {
      return null;
    }
    try {
      MethodSignatureBackedByPsiMethod superMethod = SuperMethodsSearch.search(method, null, true, false).findFirst();
      if (superMethod != null && method.getContainingClass().isInterface() && "clone".equals(superMethod.getName())) {
        final PsiClass containingClass = superMethod.getMethod().getContainingClass();
        if (containingClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
          superMethod = null;
        }
      }
      if (superMethod == null) {
        String description = JavaErrorMessages.message("method.does.not.override.super");
        HighlightInfo highlightInfo =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(overrideAnnotation).descriptionAndTooltip(description).create();
        QUICK_FIX_FACTORY.registerPullAsAbstractUpFixes(method, new QuickFixActionRegistrarImpl(highlightInfo));
        return highlightInfo;
      }
      PsiClass superClass = superMethod.getMethod().getContainingClass();
      if (languageLevel.equals(LanguageLevel.JDK_1_5) &&
          superClass != null &&
          superClass.isInterface()) {
        String description = JavaErrorMessages.message("override.not.allowed.in.interfaces");
        HighlightInfo info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(overrideAnnotation).descriptionAndTooltip(description).create();
        QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createIncreaseLanguageLevelFix(LanguageLevel.JDK_1_6));
        return info;
      }
      return null;
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }

  @Nullable
  public static HighlightInfo checkSafeVarargsAnnotation(PsiMethod method) {
    PsiModifierList list = method.getModifierList();
    final PsiAnnotation safeVarargsAnnotation = list.findAnnotation("java.lang.SafeVarargs");
    if (safeVarargsAnnotation == null) {
      return null;
    }
    try {
      if (!method.isVarArgs()) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(safeVarargsAnnotation).descriptionAndTooltip(
          "@SafeVarargs is not allowed on methods with fixed arity").create();
      }
      if (!method.hasModifierProperty(PsiModifier.STATIC) && !method.hasModifierProperty(PsiModifier.FINAL) && !method.isConstructor()) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(safeVarargsAnnotation).descriptionAndTooltip(
          "@SafeVarargs is not allowed on non-final instance methods").create();
      }

      final PsiParameter varParameter = method.getParameterList().getParameters()[method.getParameterList().getParametersCount() - 1];

      for (PsiReference reference : ReferencesSearch.search(varParameter)) {
        final PsiElement element = reference.getElement();
        if (element instanceof PsiExpression && !PsiUtil.isAccessedForReading((PsiExpression)element)) {
          return HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(element).descriptionAndTooltip(
            "@SafeVarargs do not suppress potentially unsafe operations").create();
        }
      }


      LOG.assertTrue(varParameter.isVarArgs());
      final PsiEllipsisType ellipsisType = (PsiEllipsisType)varParameter.getType();
      final PsiType componentType = ellipsisType.getComponentType();
      if (JavaGenericsUtil.isReifiableType(componentType)) {
        PsiElement element = varParameter.getTypeElement();
        return HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(element).descriptionAndTooltip(
          "@SafeVarargs is not applicable for reifiable types").create();
      }
      return null;
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }

  static void checkEnumConstantForConstructorProblems(PsiEnumConstant enumConstant,
                                                      final HighlightInfoHolder holder,
                                                      @NotNull JavaSdkVersion javaSdkVersion) {
    PsiClass containingClass = enumConstant.getContainingClass();
    if (enumConstant.getInitializingClass() == null) {
      HighlightInfo highlightInfo = HighlightClassUtil.checkInstantiationOfAbstractClass(containingClass, enumConstant.getNameIdentifier());
      if (highlightInfo != null) {
        QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createImplementMethodsFix(enumConstant));
        holder.add(highlightInfo);
        return;
      }
      highlightInfo = HighlightClassUtil.checkClassWithAbstractMethods(enumConstant.getContainingClass(), enumConstant, enumConstant.getNameIdentifier().getTextRange());
      if (highlightInfo != null) {
        holder.add(highlightInfo);
        return;
      }
    }
    PsiClassType type = JavaPsiFacade.getInstance(holder.getProject()).getElementFactory().createType(containingClass);

    HighlightMethodUtil.checkConstructorCall(type.resolveGenerics(), enumConstant, type, null, holder, javaSdkVersion);
  }

  @Nullable
  public static HighlightInfo checkEnumSuperConstructorCall(PsiMethodCallExpression expr) {
    PsiReferenceExpression methodExpression = expr.getMethodExpression();
    final PsiElement refNameElement = methodExpression.getReferenceNameElement();
    if (refNameElement != null && PsiKeyword.SUPER.equals(refNameElement.getText())) {
      final PsiMember constructor = PsiUtil.findEnclosingConstructorOrInitializer(expr);
      if (constructor instanceof PsiMethod) {
        final PsiClass aClass = constructor.getContainingClass();
        if (aClass != null && aClass.isEnum()) {
          final String message = JavaErrorMessages.message("call.to.super.is.not.allowed.in.enum.constructor");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expr).descriptionAndTooltip(message).create();
        }
      }
    }
    return null;
  }

  @Nullable
  public static HighlightInfo checkVarArgParameterIsLast(@NotNull PsiParameter parameter, @NotNull LanguageLevel languageLevel,@NotNull PsiFile containingFile) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (declarationScope instanceof PsiMethod) {
      PsiParameter[] params = ((PsiMethod)declarationScope).getParameterList().getParameters();
      if (parameter.isVarArgs()) {
        HighlightInfo info = HighlightUtil.checkVarargFeature(parameter, languageLevel,containingFile);
        if (info != null) return info;

        if (params[params.length - 1] != parameter) {
          String description = JavaErrorMessages.message("vararg.not.last.parameter");
          info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameter).descriptionAndTooltip(description).create();
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createMakeVarargParameterLastFix(parameter));
          return info;
        }
      }
    }
    return null;
  }

  @Nullable
  public static List<HighlightInfo> checkEnumConstantModifierList(PsiModifierList modifierList) {
    List<HighlightInfo> list = null;
    PsiElement[] children = modifierList.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiKeyword) {
        if (list == null) {
          list = new ArrayList<HighlightInfo>();
        }
        String description = JavaErrorMessages.message("modifiers.for.enum.constants");
        list.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(child).descriptionAndTooltip(description).create());
      }
    }
    return list;
  }

  @Nullable
  public static HighlightInfo checkParametersAllowed(PsiReferenceParameterList refParamList, @NotNull LanguageLevel languageLevel,@NotNull PsiFile containingFile) {
    HighlightInfo info = HighlightUtil.checkGenericsFeature(refParamList, refParamList.getTypeParameterElements().length,
                                                            languageLevel, containingFile);
    if (info != null) return info;

    if (refParamList.getTextLength() != 0) {
      final PsiElement parent = refParamList.getParent();
      if (parent instanceof PsiReferenceExpression) {
        final PsiElement grandParent = parent.getParent();
        if (!(grandParent instanceof PsiMethodCallExpression) && !(parent instanceof PsiMethodReferenceExpression)) {
          final String message = JavaErrorMessages.message("generics.reference.parameters.not.allowed");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refParamList).descriptionAndTooltip(message).create();
        }
      }
    }

    return null;
  }

  @Nullable
  public static HighlightInfo checkParametersOnRaw(PsiReferenceParameterList refParamList) {
    if (refParamList.getTypeArguments().length == 0) return null;
    JavaResolveResult resolveResult = null;
    PsiElement parent = refParamList.getParent();
    PsiElement qualifier = null;
    if (parent instanceof PsiJavaCodeReferenceElement) {
      resolveResult = ((PsiJavaCodeReferenceElement)parent).advancedResolve(false);
      qualifier = ((PsiJavaCodeReferenceElement)parent).getQualifier();
    }
    else if (parent instanceof PsiCallExpression) {
      resolveResult = ((PsiCallExpression)parent).resolveMethodGenerics();
      if (parent instanceof PsiMethodCallExpression) {
        final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)parent).getMethodExpression();
        qualifier = methodExpression.getQualifier();
      }
    }
    if (resolveResult != null) {
      PsiElement element = resolveResult.getElement();
      if (!(element instanceof PsiTypeParameterListOwner)) return null;
      if (((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC)) return null;
      if (qualifier instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)qualifier).resolve() instanceof PsiTypeParameter) return null;
      PsiClass containingClass = ((PsiMember)element).getContainingClass();
      if (containingClass != null && PsiUtil.isRawSubstitutor(containingClass, resolveResult.getSubstitutor())) {
        if ((parent instanceof PsiCallExpression || parent instanceof PsiMethodReferenceExpression) && PsiUtil.isLanguageLevel7OrHigher(parent)) {
          return null;
        }

        if (element instanceof PsiMethod) {
          if (((PsiMethod)element).findSuperMethods().length > 0) return null;
          if (qualifier instanceof PsiReferenceExpression){
            final PsiType type = ((PsiReferenceExpression)qualifier).getType();
            final boolean isJavac7 = JavaVersionService.getInstance().isAtLeast(containingClass, JavaSdkVersion.JDK_1_7);
            if (type instanceof PsiClassType && isJavac7 && ((PsiClassType)type).isRaw()) return null;
            final PsiClass typeParameter = PsiUtil.resolveClassInType(type);
            if (typeParameter instanceof PsiTypeParameter) {
              if (isJavac7) return null;
              for (PsiClassType classType : typeParameter.getExtendsListTypes()) {
                final PsiClass resolve = classType.resolve();
                if (resolve != null) {
                  final PsiMethod[] superMethods = resolve.findMethodsBySignature((PsiMethod)element, true);
                  for (PsiMethod superMethod : superMethods) {
                    if (!PsiUtil.isRawSubstitutor(superMethod, resolveResult.getSubstitutor())) {
                      return null;
                    }
                  }
                }
              }
            }
          }
        }
        final String message = element instanceof PsiClass
                               ? JavaErrorMessages.message("generics.type.arguments.on.raw.type")
                               : JavaErrorMessages.message("generics.type.arguments.on.raw.method");

        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refParamList).descriptionAndTooltip(message).create();
      }
    }
    return null;
  }

  public static HighlightInfo checkCannotInheritFromEnum(PsiClass superClass, PsiElement elementToHighlight) {
    HighlightInfo errorResult = null;
    if (Comparing.strEqual("java.lang.Enum", superClass.getQualifiedName())) {
      String message = JavaErrorMessages.message("classes.extends.enum");
      errorResult =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(message).create();
    }
    return errorResult;
  }

  public static HighlightInfo checkGenericCannotExtendException(PsiReferenceList list) {
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiClass)) return null;
    PsiClass aClass = (PsiClass)parent;

    if (!aClass.hasTypeParameters() || aClass.getExtendsList() != list) return null;
    PsiJavaCodeReferenceElement[] referenceElements = list.getReferenceElements();
    PsiClass throwableClass = null;
    for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
      PsiElement resolved = referenceElement.resolve();
      if (!(resolved instanceof PsiClass)) continue;
      if (throwableClass == null) {
        throwableClass = JavaPsiFacade.getInstance(aClass.getProject()).findClass("java.lang.Throwable", aClass.getResolveScope());
      }
      if (InheritanceUtil.isInheritorOrSelf((PsiClass)resolved, throwableClass, true)) {
        String message = JavaErrorMessages.message("generic.extend.exception");
        HighlightInfo highlightInfo =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(referenceElement).descriptionAndTooltip(message).create();
        PsiClassType classType = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createType((PsiClass)resolved);
        QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createExtendsListFix(aClass, classType, false));
        return highlightInfo;
      }
    }
    return null;
  }

  public static HighlightInfo checkEnumMustNotBeLocal(final PsiClass aClass) {
    if (!aClass.isEnum()) return null;
    PsiElement parent = aClass.getParent();
    if (!(parent instanceof PsiClass || parent instanceof PsiFile)) {
      String description = JavaErrorMessages.message("local.enum");
      TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
    }
    return null;
  }

  public static HighlightInfo checkSelectStaticClassFromParameterizedType(final PsiElement resolved, final PsiJavaCodeReferenceElement ref) {
    if (resolved instanceof PsiClass && ((PsiClass)resolved).hasModifierProperty(PsiModifier.STATIC)) {
      final PsiElement qualifier = ref.getQualifier();
      if (qualifier instanceof PsiJavaCodeReferenceElement) {
        final PsiReferenceParameterList parameterList = ((PsiJavaCodeReferenceElement)qualifier).getParameterList();
        if (parameterList != null && parameterList.getTypeArguments().length > 0) {
          final String message = JavaErrorMessages.message("generics.select.static.class.from.parameterized.type",
                                                           HighlightUtil.formatClass((PsiClass)resolved));
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameterList).descriptionAndTooltip(message).create();
        }
      }
    }
    return null;
  }

  public static HighlightInfo checkCannotInheritFromTypeParameter(final PsiClass superClass, final PsiJavaCodeReferenceElement toHighlight) {
    if (superClass instanceof PsiTypeParameter) {
      String description = JavaErrorMessages.message("class.cannot.inherit.from.its.type.parameter");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(toHighlight).descriptionAndTooltip(description).create();
    }
    return null;
  }

  /**
   * http://docs.oracle.com/javase/specs/jls/se7/html/jls-4.html#jls-4.8
   */
  @Nullable
  public static HighlightInfo checkRawOnParameterizedType(PsiReferenceParameterList list) {
    if (list.getTypeArguments().length > 0) return null;
    final PsiElement parent = list.getParent();
    if (parent instanceof PsiJavaCodeReferenceElement) {
      final PsiElement qualifier = ((PsiJavaCodeReferenceElement)parent).getQualifier();
      if (qualifier instanceof PsiJavaCodeReferenceElement) {
        if (((PsiJavaCodeReferenceElement)qualifier).getTypeParameters().length > 0) {
          final PsiElement resolve = ((PsiJavaCodeReferenceElement)parent).resolve();
          if (resolve instanceof PsiTypeParameterListOwner
              && ((PsiTypeParameterListOwner)resolve).hasTypeParameters()
              && !((PsiTypeParameterListOwner)resolve).hasModifierProperty(PsiModifier.STATIC)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parent).descriptionAndTooltip(
              "Improper formed type; some type parameters are missing").create();
          }
        }
      }
    }
    return null;
  }

  public static HighlightInfo checkCannotPassInner(PsiJavaCodeReferenceElement ref) {
    if (ref.getParent() instanceof PsiTypeElement) {
      final PsiClass psiClass = PsiTreeUtil.getParentOfType(ref, PsiClass.class);
      if (psiClass == null) return null;
      if (PsiTreeUtil.isAncestor(psiClass.getExtendsList(), ref, false) ||
          PsiTreeUtil.isAncestor(psiClass.getImplementsList(), ref, false)) {
        final PsiElement qualifier = ref.getQualifier();
        if (qualifier instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)qualifier).resolve() == psiClass) {
          final PsiJavaCodeReferenceElement referenceElement = PsiTreeUtil.getParentOfType(ref, PsiJavaCodeReferenceElement.class);
          if (referenceElement == null) return null;
          final PsiElement typeClass = referenceElement.resolve();
          if (!(typeClass instanceof PsiClass)) return null;
          final PsiElement resolve = ref.resolve();
          final PsiClass containingClass = resolve != null ? ((PsiClass)resolve).getContainingClass() : null;
          if (containingClass == null) return null;
          if (psiClass.isInheritor(containingClass, true) ||
              unqualifiedNestedClassReferenceAccessedViaContainingClassInheritance((PsiClass)typeClass, ((PsiClass)resolve).getExtendsList()) ||
              unqualifiedNestedClassReferenceAccessedViaContainingClassInheritance((PsiClass)typeClass, ((PsiClass)resolve).getImplementsList())) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip(((PsiClass)resolve).getName() + " is not accessible in current context").range(ref).create();
          }
        }
      }
    }
    return null;
  }

  private static boolean unqualifiedNestedClassReferenceAccessedViaContainingClassInheritance(PsiClass containingClass,
                                                                                              PsiReferenceList referenceList) {
    if (referenceList != null) {
      for (PsiJavaCodeReferenceElement referenceElement : referenceList.getReferenceElements()) {
        if (!referenceElement.isQualified()) {
          final PsiElement superClass = referenceElement.resolve();
          if (superClass instanceof PsiClass) {
            final PsiClass superContainingClass = ((PsiClass)superClass).getContainingClass();
            if (superContainingClass != null && InheritanceUtil.isInheritorOrSelf(containingClass, superContainingClass, true)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  public static void registerVariableParameterizedTypeFixes(HighlightInfo highlightInfo,
                                                            @NotNull PsiVariable variable,
                                                            @NotNull PsiReferenceParameterList parameterList, 
                                                            @NotNull JavaSdkVersion version) {
    PsiType type = variable.getType();
    if (!(type instanceof PsiClassType)) return;

    if (DumbService.getInstance(variable.getProject()).isDumb()) return;

    String shortName = ((PsiClassType)type).getClassName();
    PsiManager manager = parameterList.getManager();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    PsiShortNamesCache shortNamesCache = PsiShortNamesCache.getInstance(parameterList.getProject());
    PsiClass[] classes = shortNamesCache.getClassesByName(shortName, GlobalSearchScope.allScope(manager.getProject()));
    PsiElementFactory factory = facade.getElementFactory();
    for (PsiClass aClass : classes) {
      if (checkReferenceTypeArgumentList(aClass, parameterList, PsiSubstitutor.EMPTY, false, version) == null) {
        PsiType[] actualTypeParameters = parameterList.getTypeArguments();
        PsiTypeParameter[] classTypeParameters = aClass.getTypeParameters();
        Map<PsiTypeParameter, PsiType> map = new java.util.HashMap<PsiTypeParameter, PsiType>();
        for (int j = 0; j < classTypeParameters.length; j++) {
          PsiTypeParameter classTypeParameter = classTypeParameters[j];
          PsiType actualTypeParameter = actualTypeParameters[j];
          map.put(classTypeParameter, actualTypeParameter);
        }
        PsiSubstitutor substitutor = factory.createSubstitutor(map);
        PsiType suggestedType = factory.createType(aClass, substitutor);
        HighlightUtil.registerChangeVariableTypeFixes(variable, suggestedType, highlightInfo);
      }
    }
  }
}


/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
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
import gnu.trove.THashSet;
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
  static HighlightInfo checkInferredTypeArguments(PsiTypeParameterListOwner listOwner,
                                                  PsiElement call,
                                                  PsiSubstitutor substitutor) {
    return checkInferredTypeArguments(listOwner.getTypeParameters(), call, substitutor);
  }

  @Nullable
  private static HighlightInfo checkInferredTypeArguments(PsiTypeParameter[] typeParameters,
                                                          PsiElement call,
                                                          PsiSubstitutor substitutor) {
    final Pair<PsiTypeParameter, PsiType> inferredTypeArgument = GenericsUtil.findTypeParameterWithBoundError(typeParameters, substitutor,
                                                                                                              call, false);
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
  static HighlightInfo checkParameterizedReferenceTypeArguments(final PsiElement resolved,
                                                                final PsiJavaCodeReferenceElement referenceElement,
                                                                final PsiSubstitutor substitutor,
                                                                @NotNull JavaSdkVersion javaSdkVersion) {
    if (!(resolved instanceof PsiTypeParameterListOwner)) return null;
    final PsiTypeParameterListOwner typeParameterListOwner = (PsiTypeParameterListOwner)resolved;
    return checkReferenceTypeArgumentList(typeParameterListOwner, referenceElement.getParameterList(), substitutor, true, javaSdkVersion);
  }

  @Nullable
  static HighlightInfo checkReferenceTypeArgumentList(final PsiTypeParameterListOwner typeParameterListOwner,
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
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(referenceParameterList).descriptionAndTooltip(description).create();
        }
        inferenceResult = ((PsiDiamondType)referenceElements[0].getType()).resolveInferredTypes();
        final String errorMessage = inferenceResult.getErrorMessage();
        if (errorMessage != null) {
          final PsiType expectedType = detectExpectedType(referenceParameterList);
          if (!(inferenceResult.failedToInfer() && expectedType instanceof PsiClassType && ((PsiClassType)expectedType).isRaw())) {
            HighlightInfo highlightInfo = HighlightInfo
              .newHighlightInfo(HighlightInfoType.ERROR).range(referenceParameterList).descriptionAndTooltip(errorMessage).create();
            if (inferenceResult == PsiDiamondType.DiamondInferenceResult.ANONYMOUS_INNER_RESULT &&
                !PsiUtil.isLanguageLevel9OrHigher(referenceParameterList)) {
              QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createIncreaseLanguageLevelFix(LanguageLevel.JDK_1_9));
            }
            return highlightInfo;
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
            (javaSdkVersion.isAtLeast(JavaSdkVersion.JDK_1_7) || hasSuperMethodsWithTypeParams((PsiMethod)typeParameterListOwner))) {
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
          if (typeParameterListOwner instanceof PsiClass) {
            QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createChangeClassSignatureFromUsageFix((PsiClass)typeParameterListOwner, referenceParameterList));
          }

          PsiElement grandParent = referenceParameterList.getParent().getParent();
          if (grandParent instanceof PsiTypeElement) {
            PsiElement variable = grandParent.getParent();
            if (variable instanceof PsiVariable) {
              if (targetParametersNum == 0) {
                QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createRemoveTypeArgumentsFix(variable));
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

  private static boolean hasSuperMethodsWithTypeParams(PsiMethod method) {
    for (PsiMethod superMethod : method.findDeepestSuperMethods()) {
      if (superMethod.hasTypeParameters()) return true;
    }
    return false;
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
      PsiElement method = PsiTreeUtil.getParentOfType(parent, PsiMethod.class, PsiLambdaExpression.class);
      if (method instanceof PsiMethod) {
        expectedType = ((PsiMethod)method).getReturnType();
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
      if (GenericsUtil.checkNotInBounds(type, psiType, referenceParameterList)) {
        final String description = "Actual type argument and inferred type contradict each other";
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement2Highlight).descriptionAndTooltip(description).create();
      }
    }

    final PsiClassType[] bounds = classParameter.getSuperTypes();
    for (PsiType bound : bounds) {
      bound = substitutor.substitute(bound);
      if (!bound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) && GenericsUtil.checkNotInBounds(type, bound, referenceParameterList)) {
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
  static HighlightInfo checkElementInTypeParameterExtendsList(@NotNull PsiReferenceList referenceList,
                                                              @NotNull PsiClass aClass,
                                                              @NotNull JavaResolveResult resolveResult,
                                                              @NotNull PsiElement element) {
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
    return errorResult;
  }

  static HighlightInfo checkInterfaceMultipleInheritance(PsiClass aClass) {
    final PsiClassType[] types = aClass.getSuperTypes();
    if (types.length < 2) return null;
    Map<PsiClass, PsiSubstitutor> inheritedClasses = new HashMap<>();
    final TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
    return checkInterfaceMultipleInheritance(aClass,
                                             aClass,
                                             PsiSubstitutor.EMPTY, inheritedClasses,
                                             new HashSet<>(), textRange);
  }

  private static HighlightInfo checkInterfaceMultipleInheritance(PsiClass aClass,
                                                                 PsiElement place,
                                                                 PsiSubstitutor derivedSubstitutor,
                                                                 Map<PsiClass, PsiSubstitutor> inheritedClasses,
                                                                 Set<PsiClass> visited,
                                                                 TextRange textRange) {
    final List<PsiClassType.ClassResolveResult> superTypes = PsiClassImplUtil.getScopeCorrectedSuperTypes(aClass, place.getResolveScope());
    for (PsiClassType.ClassResolveResult result : superTypes) {
      final PsiClass superClass = result.getElement();
      if (superClass == null || visited.contains(superClass)) continue;
      PsiSubstitutor superTypeSubstitutor = result.getSubstitutor();
      superTypeSubstitutor = MethodSignatureUtil.combineSubstitutors(superTypeSubstitutor, derivedSubstitutor);

      final PsiSubstitutor inheritedSubstitutor = inheritedClasses.get(superClass);
      if (inheritedSubstitutor != null) {
        final PsiTypeParameter[] typeParameters = superClass.getTypeParameters();
        for (PsiTypeParameter typeParameter : typeParameters) {
          PsiType type1 = inheritedSubstitutor.substitute(typeParameter);
          PsiType type2 = superTypeSubstitutor.substitute(typeParameter);

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
      final HighlightInfo highlightInfo = checkInterfaceMultipleInheritance(superClass, place, superTypeSubstitutor, inheritedClasses, visited, textRange);
      visited.remove(superClass);

      if (highlightInfo != null) return highlightInfo;
    }
    return null;
  }

  static Collection<HighlightInfo> checkOverrideEquivalentMethods(@NotNull PsiClass aClass) {
    List<HighlightInfo> result = new ArrayList<>();
    final Collection<HierarchicalMethodSignature> signaturesWithSupers = aClass.getVisibleSignatures();
    PsiManager manager = aClass.getManager();
    Map<MethodSignature, MethodSignatureBackedByPsiMethod> sameErasureMethods =
      new THashMap<>(MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY);

    final Set<MethodSignature> foundProblems = new THashSet<>(MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY);
    for (HierarchicalMethodSignature signature : signaturesWithSupers) {
      HighlightInfo info = checkSameErasureNotSubSignatureInner(signature, manager, aClass, sameErasureMethods);
      if (info != null && foundProblems.add(signature)) {
        result.add(info);
      }
      if (aClass instanceof PsiTypeParameter) {
        info = HighlightMethodUtil.checkMethodIncompatibleReturnType(signature, signature.getSuperSignatures(), true, HighlightNamesUtil.getClassDeclarationTextRange(aClass));
        if (info != null) {
          result.add(info);
        }
      }
    }

    return result.isEmpty() ? null : result;
  }

  static HighlightInfo checkDefaultMethodOverrideEquivalentToObjectNonPrivate(@NotNull LanguageLevel languageLevel,
                                                                              @NotNull PsiClass aClass,
                                                                              @NotNull PsiMethod method,
                                                                              @NotNull PsiElement methodIdentifier) {
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8) && aClass.isInterface() && method.hasModifierProperty(PsiModifier.DEFAULT)) {
      HierarchicalMethodSignature sig = method.getHierarchicalMethodSignature();
      for (HierarchicalMethodSignature methodSignature : sig.getSuperSignatures()) {
        final PsiMethod objectMethod = methodSignature.getMethod();
        final PsiClass containingClass = objectMethod.getContainingClass();
        if (containingClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName()) && objectMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .descriptionAndTooltip("Default method '" + sig.getName() + "' overrides a member of 'java.lang.Object'")
            .range(methodIdentifier)
            .create();
        }
      }
    }
    return null;
  }

  static HighlightInfo checkUnrelatedDefaultMethods(@NotNull PsiClass aClass,
                                                    @NotNull PsiIdentifier classIdentifier) {
    final Map<? extends MethodSignature, Set<PsiMethod>> overrideEquivalent = PsiSuperMethodUtil.collectOverrideEquivalents(aClass);

    final boolean isInterface = aClass.isInterface();
    for (Set<PsiMethod> overrideEquivalentMethods : overrideEquivalent.values()) {
      if (overrideEquivalentMethods.size() <= 1) continue;
      List<PsiMethod> defaults = null;
      List<PsiMethod> abstracts = null;
      boolean hasConcrete = false;
      for (PsiMethod method : overrideEquivalentMethods) {
        final boolean isDefault = method.hasModifierProperty(PsiModifier.DEFAULT);
        final boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
        if (isDefault) {
          if (defaults == null) defaults = new ArrayList<>(2);
          defaults.add(method);
        }
        if (isAbstract) {
          if (abstracts == null) abstracts = new ArrayList<>(2);
          abstracts.add(method);
        }
        hasConcrete |= !isDefault && !isAbstract;
      }

      if (!hasConcrete && defaults != null) {
        final PsiMethod defaultMethod = defaults.get(0);
        if (MethodSignatureUtil.findMethodBySuperMethod(aClass, defaultMethod, false) != null) continue;
        final PsiClass defaultMethodContainingClass = defaultMethod.getContainingClass();
        if (defaultMethodContainingClass == null) continue;
        final PsiMethod unrelatedMethod = abstracts != null ? abstracts.get(0) : defaults.get(1);
        final PsiClass unrelatedMethodContainingClass = unrelatedMethod.getContainingClass();
        if (unrelatedMethodContainingClass == null) continue;
        if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT) && !(aClass instanceof PsiTypeParameter)
            && abstracts != null && unrelatedMethodContainingClass.isInterface()) {
          if (defaultMethodContainingClass.isInheritor(unrelatedMethodContainingClass, true) &&
              MethodSignatureUtil.isSubsignature(unrelatedMethod.getSignature(TypeConversionUtil.getSuperClassSubstitutor(unrelatedMethodContainingClass, defaultMethodContainingClass, PsiSubstitutor.EMPTY)),
                                                 defaultMethod.getSignature(PsiSubstitutor.EMPTY))) {
            continue;
          }
          final String key = aClass instanceof PsiEnumConstantInitializer ? "enum.constant.should.implement.method" : "class.must.be.abstract";
          final String message = JavaErrorMessages.message(key, HighlightUtil.formatClass(aClass, false), JavaHighlightUtil.formatMethod(abstracts.get(0)),
                                                           HighlightUtil.formatClass(unrelatedMethodContainingClass, false));
          final HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(classIdentifier).descriptionAndTooltip(message).create();
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createImplementMethodsFix(aClass));
          return info;
        }
        if (isInterface || abstracts == null || unrelatedMethodContainingClass.isInterface()) {
          final List<PsiClass> defaultContainingClasses = ContainerUtil.mapNotNull(defaults, PsiMethod::getContainingClass);
          final String unrelatedDefaults = hasUnrelatedDefaults(defaultContainingClasses);
          if (unrelatedDefaults == null &&
              (abstracts == null || !hasNotOverriddenAbstract(defaultContainingClasses, unrelatedMethodContainingClass))) {
            continue;
          }

          final String message = unrelatedDefaults != null ? " inherits unrelated defaults for " : " inherits abstract and default for ";
          final HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(classIdentifier).descriptionAndTooltip(
            HighlightUtil.formatClass(aClass) +
            message +
            JavaHighlightUtil.formatMethod(defaultMethod) + " from types " +
            (unrelatedDefaults != null ? unrelatedDefaults
                                       : HighlightUtil.formatClass(defaultMethodContainingClass) + " and " + HighlightUtil.formatClass(unrelatedMethodContainingClass)))
            .create();
          QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createImplementMethodsFix(aClass));
          return info;
        }
      }
    }
    return null;
  }

  private static boolean belongToOneHierarchy(@NotNull PsiClass defaultMethodContainingClass, @NotNull PsiClass unrelatedMethodContainingClass) {
    return defaultMethodContainingClass.isInheritor(unrelatedMethodContainingClass, true) ||
           unrelatedMethodContainingClass.isInheritor(defaultMethodContainingClass, true);
  }

  private static boolean hasNotOverriddenAbstract(List<PsiClass> defaultContainingClasses, @NotNull PsiClass abstractMethodContainingClass) {
    return defaultContainingClasses.stream().noneMatch(containingClass -> belongToOneHierarchy(containingClass, abstractMethodContainingClass));
  }

  private static String hasUnrelatedDefaults(List<PsiClass> defaults) {
    if (defaults.size() > 1) {
      PsiClass[] defaultClasses = defaults.toArray(PsiClass.EMPTY_ARRAY);
      ArrayList<PsiClass> classes = new ArrayList<>(defaults);
      for (final PsiClass aClass1 : defaultClasses) {
        classes.removeIf(aClass2 -> aClass1.isInheritor(aClass2, true));
      }

      if (classes.size() > 1) {
        return HighlightUtil.formatClass(classes.get(0)) + " and " + HighlightUtil.formatClass(classes.get(1));
      }
    }

    return null;
  }

  static HighlightInfo checkUnrelatedConcrete(@NotNull PsiClass psiClass,
                                              @NotNull PsiIdentifier classIdentifier) {
    final PsiClass superClass = psiClass.getSuperClass();
    if (superClass != null && superClass.hasTypeParameters()) {
      final Collection<HierarchicalMethodSignature> visibleSignatures = superClass.getVisibleSignatures();
      final Map<MethodSignature, PsiMethod> overrideEquivalent = new THashMap<>(MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY);
      for (HierarchicalMethodSignature hms : visibleSignatures) {
        final PsiMethod method = hms.getMethod();
        if (method.isConstructor()) continue;
        if (method.hasModifierProperty(PsiModifier.ABSTRACT) ||
            method.hasModifierProperty(PsiModifier.DEFAULT) ||
            method.hasModifierProperty(PsiModifier.STATIC)) continue;
        if (psiClass.findMethodsBySignature(method, false).length > 0) continue;
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) continue;
        final PsiSubstitutor containingClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(containingClass, psiClass, PsiSubstitutor.EMPTY);
        final PsiSubstitutor finalSubstitutor = PsiSuperMethodUtil
          .obtainFinalSubstitutor(containingClass, containingClassSubstitutor, hms.getSubstitutor(), false);
        final MethodSignatureBackedByPsiMethod signature = MethodSignatureBackedByPsiMethod.create(method, finalSubstitutor, false);
        final PsiMethod foundMethod = overrideEquivalent.get(signature);
        PsiClass foundMethodContainingClass;
        if (foundMethod != null &&
            !foundMethod.hasModifierProperty(PsiModifier.ABSTRACT) &&
            !foundMethod.hasModifierProperty(PsiModifier.DEFAULT) &&
            (foundMethodContainingClass = foundMethod.getContainingClass()) != null) {
          final String description =
            "Methods " +
            JavaHighlightUtil.formatMethod(foundMethod) + " from " + HighlightUtil.formatClass(foundMethodContainingClass) +
            " and " +
            JavaHighlightUtil.formatMethod(method) + " from " + HighlightUtil.formatClass(containingClass) +
            " are inherited with the same signature";

          final HighlightInfo info = HighlightInfo
            .newHighlightInfo(HighlightInfoType.ERROR).range(classIdentifier).descriptionAndTooltip(
              description)
            .create();
          //todo override fix
          return info;
        }
        overrideEquivalent.put(signature, method);
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

    JavaVersionService javaVersionService = JavaVersionService.getInstance();
    final boolean atLeast17 = javaVersionService.isAtLeast(aClass, JavaSdkVersion.JDK_1_7);
    if (checkMethod.hasModifierProperty(PsiModifier.STATIC) && !checkEqualsSuper && !atLeast17) {
      return null;
    }

    if (superMethod.hasModifierProperty(PsiModifier.STATIC) && superContainingClass != null &&
        superContainingClass.isInterface() && !checkEqualsSuper && PsiUtil.isLanguageLevel8OrHigher(superContainingClass)) {
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
    if (!javaVersionService.isAtLeast(aClass, JavaSdkVersion.JDK_1_8)) {
      //javac <= 1.7 didn't check transitive overriding rules for interfaces
      if (superContainingClass != null && !superContainingClass.isInterface() && checkContainingClass.isInterface() && !aClass.equals(superContainingClass)) return null;
    }
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

  static HighlightInfo checkTypeParameterInstantiation(PsiNewExpression expression) {
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

  static HighlightInfo checkWildcardUsage(PsiTypeElement typeElement) {
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

  static HighlightInfo checkReferenceTypeUsedAsTypeArgument(PsiTypeElement typeElement, LanguageLevel level) {
    final PsiType type = typeElement.getType();
    if (type != PsiType.NULL && type instanceof PsiPrimitiveType ||
        type instanceof PsiWildcardType && ((PsiWildcardType)type).getBound() instanceof PsiPrimitiveType) {
      final PsiElement element = new PsiMatcherImpl(typeElement)
        .parent(PsiMatchers.hasClass(PsiReferenceParameterList.class))
        .parent(PsiMatchers.hasClass(PsiJavaCodeReferenceElement.class, PsiNewExpression.class))
        .getElement();
      if (element == null) return null;

      if (level.isAtLeast(LanguageLevel.JDK_X)) return null;

      String text = JavaErrorMessages.message("generics.type.argument.cannot.be.of.primitive.type");
      HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(text).create();

      PsiType toConvert = type;
      if (type instanceof PsiWildcardType) {
        toConvert = ((PsiWildcardType)type).getBound();
      }
      if (toConvert instanceof PsiPrimitiveType) {
        final PsiClassType boxedType = ((PsiPrimitiveType)toConvert).getBoxedType(typeElement);
        if (boxedType != null) {
          QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createReplacePrimitiveWithBoxedTypeAction(
            typeElement, toConvert.getPresentableText(), ((PsiPrimitiveType)toConvert).getBoxedTypeName()));
        }
      }
      return highlightInfo;
    }

    return null;
  }

  static HighlightInfo checkForeachExpressionTypeIsIterable(PsiExpression expression) {
    if (expression == null || expression.getType() == null) return null;
    final PsiType itemType = JavaGenericsUtil.getCollectionItemType(expression);
    if (itemType == null) {
      String description = JavaErrorMessages.message("foreach.not.applicable",
                                                     JavaHighlightUtil.formatType(expression.getType()));
      final HighlightInfo highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create();
      QuickFixAction.registerQuickFixAction(highlightInfo, QUICK_FIX_FACTORY.createNotIterableForEachLoopFix(expression));
      return highlightInfo;
    }
    return null;
  }

  static HighlightInfo checkForEachParameterType(@NotNull PsiForeachStatement statement, @NotNull PsiParameter parameter) {
    final PsiExpression expression = statement.getIteratedValue();
    final PsiType itemType = expression == null ? null : JavaGenericsUtil.getCollectionItemType(expression);
    if (itemType == null) return null;

    final PsiType parameterType = parameter.getType();
    if (TypeConversionUtil.isAssignable(parameterType, itemType)) {
      return null;
    }
    HighlightInfo highlightInfo = HighlightUtil.createIncompatibleTypeHighlightInfo(itemType, parameterType, parameter.getTextRange(), 0);
    HighlightUtil.registerChangeVariableTypeFixes(parameter, itemType, expression, highlightInfo);
    return highlightInfo;
  }

  //http://docs.oracle.com/javase/specs/jls/se7/html/jls-8.html#jls-8.9.2
  @Nullable
  static HighlightInfo checkAccessStaticFieldFromEnumConstructor(@NotNull PsiReferenceExpression expr, @NotNull JavaResolveResult result) {
    final PsiElement resolved = result.getElement();

    if (!(resolved instanceof PsiField)) return null;
    if (!((PsiModifierListOwner)resolved).hasModifierProperty(PsiModifier.STATIC)) return null;
    if (expr.getParent() instanceof PsiSwitchLabelStatement) return null;
    final PsiMember constructorOrInitializer = PsiUtil.findEnclosingConstructorOrInitializer(expr);
    if (constructorOrInitializer == null) return null;
    if (constructorOrInitializer.hasModifierProperty(PsiModifier.STATIC)) return null;
    final PsiClass aClass = constructorOrInitializer instanceof PsiEnumConstantInitializer ?
                            (PsiClass)constructorOrInitializer : constructorOrInitializer.getContainingClass();
    if (aClass == null || !(aClass.isEnum() || aClass instanceof PsiEnumConstantInitializer)) return null;
    final PsiField field = (PsiField)resolved;
    if (aClass instanceof PsiEnumConstantInitializer) {
      if (field.getContainingClass() != aClass.getSuperClass()) return null;
    } else if (field.getContainingClass() != aClass) return null;


    if (!JavaVersionService.getInstance().isAtLeast(field, JavaSdkVersion.JDK_1_6)) {
      final PsiType type = field.getType();
      if (type instanceof PsiClassType && ((PsiClassType)type).resolve() == aClass) return null;
    }

    if (PsiUtil.isCompileTimeConstant((PsiVariable)field)) return null;

    String description = JavaErrorMessages.message(
      "illegal.to.access.static.member.from.enum.constructor.or.instance.initializer",
      HighlightMessageUtil.getSymbolName(resolved, result.getSubstitutor())
    );

    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expr).descriptionAndTooltip(description).create();
  }

  @Nullable
  static HighlightInfo checkEnumInstantiation(PsiElement expression, PsiClass aClass) {
    if (aClass != null && aClass.isEnum() &&
        (!(expression instanceof PsiNewExpression) ||
         ((PsiNewExpression)expression).getArrayDimensions().length == 0 && ((PsiNewExpression)expression).getArrayInitializer() == null)) {
      String description = JavaErrorMessages.message("enum.types.cannot.be.instantiated");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create();
    }
    return null;
  }

  @Nullable
  static HighlightInfo checkGenericArrayCreation(PsiElement element, PsiType type) {
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

  static boolean isEnumSyntheticMethod(MethodSignature methodSignature, Project project) {
    if (methodSignature.equals(ourValuesEnumSyntheticMethod)) return true;
    final PsiType javaLangString = PsiType.getJavaLangString(PsiManager.getInstance(project), GlobalSearchScope.allScope(project));
    final MethodSignature valueOfMethod = MethodSignatureUtil.createMethodSignature("valueOf", new PsiType[]{javaLangString}, PsiTypeParameter.EMPTY_ARRAY,
                                                                                    PsiSubstitutor.EMPTY);
    return valueOfMethod.equals(methodSignature);
  }

  @Nullable
  static HighlightInfo checkTypeParametersList(PsiTypeParameterList list, PsiTypeParameter[] parameters, @NotNull LanguageLevel level) {
    final PsiElement parent = list.getParent();
    if (parent instanceof PsiClass && ((PsiClass)parent).isEnum()) {
      String description = JavaErrorMessages.message("generics.enum.may.not.have.type.parameters");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).descriptionAndTooltip(description).create();
    }
    if (PsiUtil.isAnnotationMethod(parent)) {
      String description = JavaErrorMessages.message("generics.annotation.members.may.not.have.type.parameters");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).descriptionAndTooltip(description).create();
    }
    if (parent instanceof PsiClass && ((PsiClass)parent).isAnnotationType()) {
      String description = JavaErrorMessages.message("annotation.may.not.have.type.parameters");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).descriptionAndTooltip(description).create();
    }

    for (int i = 0; i < parameters.length; i++) {
      final PsiTypeParameter typeParameter1 = parameters[i];
      final HighlightInfo cyclicInheritance = HighlightClassUtil.checkCyclicInheritance(typeParameter1);
      if (cyclicInheritance != null) return cyclicInheritance;
      String name1 = typeParameter1.getName();
      for (int j = i + 1; j < parameters.length; j++) {
        final PsiTypeParameter typeParameter2 = parameters[j];
        String name2 = typeParameter2.getName();
        if (Comparing.strEqual(name1, name2)) {
          String message = JavaErrorMessages.message("generics.duplicate.type.parameter", name1);
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeParameter2).descriptionAndTooltip(message).create();
        }
      }
      if (!level.isAtLeast(LanguageLevel.JDK_1_7)) {
        for (PsiJavaCodeReferenceElement referenceElement : typeParameter1.getExtendsList().getReferenceElements()) {
          final PsiElement resolve = referenceElement.resolve();
          if (resolve instanceof PsiTypeParameter && ArrayUtilRt.find(parameters, resolve) > i) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(referenceElement.getTextRange()).descriptionAndTooltip("Illegal forward reference").create();
          }
        }
      }
    }
    return null;
  }

  @Nullable
  static Collection<HighlightInfo> checkCatchParameterIsClass(PsiParameter parameter) {
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

  static HighlightInfo checkInstanceOfGenericType(PsiInstanceOfExpression expression) {
    final PsiTypeElement checkTypeElement = expression.getCheckType();
    if (checkTypeElement == null) return null;
    return isIllegalForInstanceOf(checkTypeElement.getType(), checkTypeElement);
  }

  /**
   * 15.20.2 Type Comparison Operator instanceof
   * ReferenceType mentioned after the instanceof operator is reifiable
   */
  private static HighlightInfo isIllegalForInstanceOf(PsiType type, final PsiTypeElement typeElement) {
    final PsiClass resolved = PsiUtil.resolveClassInClassTypeOnly(type);
    if (resolved instanceof PsiTypeParameter) {
      String description = JavaErrorMessages.message("generics.cannot.instanceof.type.parameters");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description).create();
    }

    if (!JavaGenericsUtil.isReifiableType(type)) {
      String description = JavaErrorMessages.message("illegal.generic.type.for.instanceof");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description).create();
    }

    return null;
  }

  static HighlightInfo checkClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
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
  static HighlightInfo checkOverrideAnnotation(@NotNull PsiMethod method,
                                               @NotNull PsiAnnotation overrideAnnotation,
                                               @NotNull LanguageLevel languageLevel) {
    try {
      MethodSignatureBackedByPsiMethod superMethod = SuperMethodsSearch.search(method, null, true, false).findFirst();
      if (superMethod != null && method.getContainingClass().isInterface()) {
        final PsiMethod psiMethod = superMethod.getMethod();
        final PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass != null &&
            CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName()) &&
            psiMethod.hasModifierProperty(PsiModifier.PROTECTED)) {
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
  static HighlightInfo checkSafeVarargsAnnotation(PsiMethod method, LanguageLevel languageLevel) {
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
      if (!isSafeVarargsNoOverridingCondition(method, languageLevel)) {
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

  public static boolean isSafeVarargsNoOverridingCondition(PsiMethod method, LanguageLevel languageLevel) {
    return method.hasModifierProperty(PsiModifier.FINAL) ||
           method.hasModifierProperty(PsiModifier.STATIC) ||
           method.isConstructor() ||
           method.hasModifierProperty(PsiModifier.PRIVATE) && languageLevel.isAtLeast(LanguageLevel.JDK_1_9);
  }

  static void checkEnumConstantForConstructorProblems(@NotNull PsiEnumConstant enumConstant,
                                                      @NotNull HighlightInfoHolder holder,
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
  static HighlightInfo checkEnumSuperConstructorCall(PsiMethodCallExpression expr) {
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
  static HighlightInfo checkVarArgParameterIsLast(@NotNull PsiParameter parameter) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (declarationScope instanceof PsiMethod) {
      PsiParameter[] params = ((PsiMethod)declarationScope).getParameterList().getParameters();
      if (params[params.length - 1] != parameter) {
        String description = JavaErrorMessages.message("vararg.not.last.parameter");
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameter).descriptionAndTooltip(description).create();
        QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createMakeVarargParameterLastFix(parameter));
        return info;
      }
    }
    return null;
  }

  @Nullable
  static List<HighlightInfo> checkEnumConstantModifierList(PsiModifierList modifierList) {
    List<HighlightInfo> list = null;
    PsiElement[] children = modifierList.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiKeyword) {
        if (list == null) {
          list = new ArrayList<>();
        }
        String description = JavaErrorMessages.message("modifiers.for.enum.constants");
        list.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(child).descriptionAndTooltip(description).create());
      }
    }
    return list;
  }

  @Nullable
  static HighlightInfo checkParametersAllowed(PsiReferenceParameterList refParamList) {
    final PsiElement parent = refParamList.getParent();
    if (parent instanceof PsiReferenceExpression) {
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression) && !(parent instanceof PsiMethodReferenceExpression)) {
        final String message = JavaErrorMessages.message("generics.reference.parameters.not.allowed");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refParamList).descriptionAndTooltip(message).create();
      }
    }

    return null;
  }

  @Nullable
  static HighlightInfo checkParametersOnRaw(PsiReferenceParameterList refParamList) {
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

  static HighlightInfo checkCannotInheritFromEnum(PsiClass superClass, PsiElement elementToHighlight) {
    HighlightInfo errorResult = null;
    if (Comparing.strEqual("java.lang.Enum", superClass.getQualifiedName())) {
      String message = JavaErrorMessages.message("classes.extends.enum");
      errorResult =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(elementToHighlight).descriptionAndTooltip(message).create();
    }
    return errorResult;
  }

  static HighlightInfo checkGenericCannotExtendException(PsiReferenceList list) {
    PsiElement parent = list.getParent();
    if (parent instanceof PsiClass) {
      PsiClass klass = (PsiClass)parent;
      if (PsiUtil.typeParametersIterator(klass).hasNext() && klass.getExtendsList() == list) {
        PsiClass throwableClass = null;
        for (PsiJavaCodeReferenceElement refElement : list.getReferenceElements()) {
          PsiElement resolved = refElement.resolve();
          if (!(resolved instanceof PsiClass)) continue;
          if (throwableClass == null) {
            throwableClass =
              JavaPsiFacade.getInstance(klass.getProject()).findClass(CommonClassNames.JAVA_LANG_THROWABLE, klass.getResolveScope());
          }
          if (InheritanceUtil.isInheritorOrSelf((PsiClass)resolved, throwableClass, true)) {
            String message = JavaErrorMessages.message("generic.extend.exception");
            HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refElement).descriptionAndTooltip(message).create();
            PsiClassType classType = JavaPsiFacade.getInstance(klass.getProject()).getElementFactory().createType((PsiClass)resolved);
            QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createExtendsListFix(klass, classType, false));
            return info;
          }
        }
      }
    }
    else if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      if (method.getThrowsList() == list) {
        for (PsiJavaCodeReferenceElement refElement : list.getReferenceElements()) {
          PsiReferenceParameterList parameterList = refElement.getParameterList();
          if (parameterList != null && parameterList.getTypeParameterElements().length != 0) {
            String message = JavaErrorMessages.message("generic.extend.exception");
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refElement).descriptionAndTooltip(message).create();
          }
        }
      }
    }

    return null;
  }

  static HighlightInfo checkEnumMustNotBeLocal(final PsiClass aClass) {
    if (!aClass.isEnum()) return null;
    PsiElement parent = aClass.getParent();
    if (!(parent instanceof PsiClass || parent instanceof PsiFile || parent instanceof PsiClassLevelDeclarationStatement)) {
      String description = JavaErrorMessages.message("local.enum");
      TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
    }
    return null;
  }

  static HighlightInfo checkEnumWithoutConstantsCantHaveAbstractMethods(final PsiClass aClass) {
    if (!aClass.isEnum()) return null;
    for (PsiField field : aClass.getFields()) {
      if (field instanceof PsiEnumConstant) {
        return null;
      }
    }
    for (PsiMethod method : aClass.getMethods()) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        final String description = "Enum declaration without enum constants cannot have abstract methods";
        final TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
      }
    }
    return null;
  }

  static HighlightInfo checkSelectStaticClassFromParameterizedType(final PsiElement resolved, final PsiJavaCodeReferenceElement ref) {
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

  static HighlightInfo checkCannotInheritFromTypeParameter(final PsiClass superClass, final PsiJavaCodeReferenceElement toHighlight) {
    if (superClass instanceof PsiTypeParameter) {
      String description = JavaErrorMessages.message("class.cannot.inherit.from.its.type.parameter");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(toHighlight).descriptionAndTooltip(description).create();
    }
    return null;
  }

  /**
   * http://docs.oracle.com/javase/specs/jls/se7/html/jls-4.html#jls-4.8
   */
  static HighlightInfo checkRawOnParameterizedType(@NotNull PsiJavaCodeReferenceElement parent, PsiElement resolved) {
    PsiReferenceParameterList list = parent.getParameterList();
    if (list == null || list.getTypeArguments().length > 0) return null;
    final PsiElement qualifier = parent.getQualifier();
    if (qualifier instanceof PsiJavaCodeReferenceElement &&
        ((PsiJavaCodeReferenceElement)qualifier).getTypeParameters().length > 0 &&
        resolved instanceof PsiTypeParameterListOwner &&
        ((PsiTypeParameterListOwner)resolved).hasTypeParameters() &&
        !((PsiTypeParameterListOwner)resolved).hasModifierProperty(PsiModifier.STATIC)) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parent).descriptionAndTooltip(
        "Improper formed type; some type parameters are missing").create();
    }
    return null;
  }

  static HighlightInfo checkCannotPassInner(PsiJavaCodeReferenceElement ref) {
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
          PsiClass hiddenClass = null;
          if (psiClass.isInheritor(containingClass, true)) {
            hiddenClass = (PsiClass)resolve;
          }
          else {
            hiddenClass = unqualifiedNestedClassReferenceAccessedViaContainingClassInheritance((PsiClass)typeClass, ((PsiClass)resolve).getExtendsList());
            if (hiddenClass == null) {
              hiddenClass = unqualifiedNestedClassReferenceAccessedViaContainingClassInheritance((PsiClass)typeClass, ((PsiClass)resolve).getImplementsList());
            }
          }
          if (hiddenClass != null) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).descriptionAndTooltip(hiddenClass.getName() + " is not accessible in current context").range(ref).create();
          }
        }
      }
    }
    return null;
  }

  private static PsiClass unqualifiedNestedClassReferenceAccessedViaContainingClassInheritance(PsiClass containingClass,
                                                                                               PsiReferenceList referenceList) {
    if (referenceList != null) {
      for (PsiJavaCodeReferenceElement referenceElement : referenceList.getReferenceElements()) {
        if (!referenceElement.isQualified()) {
          final PsiElement superClass = referenceElement.resolve();
          if (superClass instanceof PsiClass) {
            final PsiClass superContainingClass = ((PsiClass)superClass).getContainingClass();
            if (superContainingClass != null &&
                InheritanceUtil.isInheritorOrSelf(containingClass, superContainingClass, true) &&
                !PsiTreeUtil.isAncestor(superContainingClass, containingClass, true)) {
              return (PsiClass)superClass;
            }
          }
        }
      }
    }
    return null;
  }

  private static void registerVariableParameterizedTypeFixes(@Nullable HighlightInfo highlightInfo,
                                                             @NotNull PsiVariable variable,
                                                             @NotNull PsiReferenceParameterList parameterList,
                                                             @NotNull JavaSdkVersion version) {
    PsiType type = variable.getType();
    if (!(type instanceof PsiClassType) || highlightInfo == null) return;

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
        Map<PsiTypeParameter, PsiType> map = new java.util.HashMap<>();
        for (int j = 0; j < classTypeParameters.length; j++) {
          PsiTypeParameter classTypeParameter = classTypeParameters[j];
          PsiType actualTypeParameter = actualTypeParameters[j];
          map.put(classTypeParameter, actualTypeParameter);
        }
        PsiSubstitutor substitutor = factory.createSubstitutor(map);
        PsiType suggestedType = factory.createType(aClass, substitutor);
        HighlightUtil.registerChangeVariableTypeFixes(variable, suggestedType, variable.getInitializer(), highlightInfo);
      }
    }
  }

  static HighlightInfo checkInferredIntersections(PsiSubstitutor substitutor, TextRange ref) {
    for (Map.Entry<PsiTypeParameter, PsiType> typeEntry : substitutor.getSubstitutionMap().entrySet()) {
      final String parameterName = typeEntry.getKey().getName();
      final PsiType type = typeEntry.getValue();
      if (type instanceof PsiIntersectionType) {
        final String conflictingConjunctsMessage = ((PsiIntersectionType)type).getConflictingConjunctsMessage();
        if (conflictingConjunctsMessage != null) {
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .descriptionAndTooltip("Type parameter " + parameterName + " has incompatible upper bounds: " + conflictingConjunctsMessage)
            .range(ref).create();
        }
      }
    }
    return null;
  }

  static HighlightInfo checkClassSupersAccessibility(@NotNull PsiClass aClass) {
    return checkClassSupersAccessibility(aClass, aClass.getResolveScope(), HighlightNamesUtil.getClassDeclarationTextRange(aClass), true);
  }

  static HighlightInfo checkClassSupersAccessibility(@NotNull PsiClass aClass, @NotNull PsiReferenceExpression ref) {
    return checkClassSupersAccessibility(aClass, ref.getResolveScope(), ref.getTextRange(), false);
  }

  private static HighlightInfo checkClassSupersAccessibility(PsiClass aClass,
                                                             GlobalSearchScope resolveScope,
                                                             TextRange range,
                                                             boolean checkParameters) {
    final JavaPsiFacade factory = JavaPsiFacade.getInstance(aClass.getProject());
    for (PsiClassType superType : aClass.getSuperTypes()) {
      final String notAccessibleErrorMessage = isTypeAccessible(superType, new HashSet<>(), checkParameters, resolveScope, factory);
      if (notAccessibleErrorMessage != null) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .descriptionAndTooltip(notAccessibleErrorMessage)
          .range(range)
          .create();
      }
    }
    return null;
  }

  static HighlightInfo checkMemberSignatureTypesAccessibility(@NotNull PsiReferenceExpression ref) {
    String message = null;

    final PsiElement parent = ref.getParent();
    if (parent instanceof PsiMethodCallExpression) {
      final JavaResolveResult resolveResult = ((PsiMethodCallExpression)parent).resolveMethodGenerics();
      final PsiMethod method = (PsiMethod)resolveResult.getElement();
      if (method != null) {
        final Set<PsiClass> classes = new HashSet<>();
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(ref.getProject());
        final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
        final GlobalSearchScope resolveScope = ref.getResolveScope();

        message = isTypeAccessible(substitutor.substitute(method.getReturnType()), classes, false, resolveScope, facade);
        if (message == null) {
          for (PsiType type : method.getSignature(substitutor).getParameterTypes()) {
            message = isTypeAccessible(type, classes, false, resolveScope, facade);
            if (message != null) {
              break;
            }
          }
        }
      }
    }
    else {
      final PsiElement resolve = ref.resolve();
      if (resolve instanceof PsiField) {
        final GlobalSearchScope resolveScope = ref.getResolveScope();
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(ref.getProject());
        message = isTypeAccessible(((PsiField)resolve).getType(), new HashSet<>(), false, resolveScope, facade);
      }
    }

    if (message != null) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .descriptionAndTooltip(message)
        .range(ref.getTextRange())
        .create();
    }

    return null;
  }

  @Nullable
  private static String isTypeAccessible(PsiType type,
                                         Set<PsiClass> classes,
                                         boolean checkParameters,
                                         GlobalSearchScope resolveScope,
                                         JavaPsiFacade factory) {
    final PsiClass aClass = PsiUtil.resolveClassInType(type);
    if (aClass != null && classes.add(aClass)) {
      VirtualFile vFile = PsiUtilCore.getVirtualFile(aClass);
      if (vFile == null) {
        return null;
      }
      FileIndexFacade index = FileIndexFacade.getInstance(aClass.getProject());
      if (!index.isInSource(vFile) && !index.isInLibraryClasses(vFile)) {
        return null;
      }

      final String qualifiedName = aClass.getQualifiedName();
      if (qualifiedName != null && factory.findClass(qualifiedName, resolveScope) == null) {
        return "Cannot access " + HighlightUtil.formatClass(aClass);
      }

      if (checkParameters) {
        if (type instanceof PsiClassType) {
          for (PsiType parameterType : ((PsiClassType)type).getParameters()) {
            final String notAccessibleMessage = isTypeAccessible(parameterType, classes, true, resolveScope, factory);
            if (notAccessibleMessage != null) {
              return notAccessibleMessage;
            }
          }
        }

        boolean isInLibrary = !index.isInContent(vFile);
        for (PsiClassType superType : aClass.getSuperTypes()) {
          final String notAccessibleMessage = isTypeAccessible(superType, classes, !isInLibrary, resolveScope, factory);
          if (notAccessibleMessage != null) {
            return notAccessibleMessage;
          }
        }
      }
    }

    return null;
  }

  static HighlightInfo checkTypeParameterOverrideEquivalentMethods(PsiClass aClass, LanguageLevel level) {
    if (aClass instanceof PsiTypeParameter && level.isAtLeast(LanguageLevel.JDK_1_7)) {
      final PsiReferenceList extendsList = aClass.getExtendsList();
      if (extendsList != null && extendsList.getReferenceElements().length > 1) {
        //todo suppress erased methods which come from the same class
        final Collection<HighlightInfo> result = checkOverrideEquivalentMethods(aClass);
        if (result != null && !result.isEmpty()) {
          return result.iterator().next();
        }
      }
    }
    return null;
  }
}
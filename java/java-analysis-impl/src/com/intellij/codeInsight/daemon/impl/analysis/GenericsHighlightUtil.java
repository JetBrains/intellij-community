// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
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
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.VariableAccessUtils;
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
  private static final Logger LOG = Logger.getInstance(GenericsHighlightUtil.class);

  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  private GenericsHighlightUtil() { }

  static HighlightInfo checkInferredTypeArguments(@NotNull PsiTypeParameterListOwner listOwner,
                                                  @NotNull PsiElement call,
                                                  @NotNull PsiSubstitutor substitutor) {
    PsiTypeParameter[] typeParameters = listOwner.getTypeParameters();
    final Pair<PsiTypeParameter, PsiType> inferredTypeArgument = GenericsUtil.findTypeParameterWithBoundError(typeParameters, substitutor,
                                                                                                              call, false);
    if (inferredTypeArgument != null) {
      final PsiType extendsType = inferredTypeArgument.second;
      final PsiTypeParameter typeParameter = inferredTypeArgument.first;
      PsiClass boundClass = extendsType instanceof PsiClassType ? ((PsiClassType)extendsType).resolve() : null;

      @NonNls String messageKey = boundClass == null || typeParameter.isInterface() == boundClass.isInterface()
                                  ? "generics.inferred.type.for.type.parameter.is.not.within.its.bound.extend"
                                  : "generics.inferred.type.for.type.parameter.is.not.within.its.bound.implement";

      String description = JavaErrorBundle.message(
        messageKey,
        HighlightUtil.formatClass(typeParameter),
        JavaHighlightUtil.formatType(extendsType),
        JavaHighlightUtil.formatType(substitutor.substitute(typeParameter))
      );
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(call).descriptionAndTooltip(description).create();
    }

    return null;
  }

  static HighlightInfo checkParameterizedReferenceTypeArguments(@Nullable PsiElement resolved,
                                                                @NotNull PsiJavaCodeReferenceElement referenceElement,
                                                                @NotNull PsiSubstitutor substitutor,
                                                                @NotNull JavaSdkVersion javaSdkVersion) {
    if (!(resolved instanceof PsiTypeParameterListOwner)) return null;
    final PsiTypeParameterListOwner typeParameterListOwner = (PsiTypeParameterListOwner)resolved;
    return checkReferenceTypeArgumentList(typeParameterListOwner, referenceElement.getParameterList(), substitutor, true, javaSdkVersion);
  }

  static HighlightInfo checkReferenceTypeArgumentList(@NotNull PsiTypeParameterListOwner typeParameterListOwner,
                                                      @Nullable PsiReferenceParameterList referenceParameterList,
                                                      @NotNull PsiSubstitutor substitutor,
                                                      boolean registerIntentions,
                                                      @NotNull JavaSdkVersion javaSdkVersion) {
    PsiDiamondType.DiamondInferenceResult inferenceResult = null;
    PsiTypeElement[] referenceElements = null;
    if (referenceParameterList != null) {
      referenceElements = referenceParameterList.getTypeParameterElements();
      if (referenceElements.length == 1 && referenceElements[0].getType() instanceof PsiDiamondType) {
        if (!typeParameterListOwner.hasTypeParameters()) {
          final String description = JavaErrorBundle.message("generics.diamond.not.applicable");
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
          description = JavaErrorBundle.message(
            "generics.type.or.method.does.not.have.type.parameters",
            typeParameterListOwnerCategoryDescription(typeParameterListOwner),
            typeParameterListOwnerDescription(typeParameterListOwner)
          );
        }
      }
      else {
        description = JavaErrorBundle.message("generics.wrong.number.of.type.arguments", refParametersNum, targetParametersNum);
      }

      if (description != null) {
        HighlightInfo highlightInfo =
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

  private static boolean hasSuperMethodsWithTypeParams(@NotNull PsiMethod method) {
    for (PsiMethod superMethod : method.findDeepestSuperMethods()) {
      if (superMethod.hasTypeParameters()) return true;
    }
    return false;
  }

  private static PsiType detectExpectedType(@NotNull PsiReferenceParameterList referenceParameterList) {
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
      if (pParent instanceof PsiCallExpression) {
        PsiExpressionList argumentList = ((PsiCallExpression)pParent).getArgumentList();
        if (parent.equals(argumentList)) {
          final PsiMethod method = ((PsiCallExpression)pParent).resolveMethod();
          if (method != null) {
            final PsiExpression[] expressions = argumentList.getExpressions();
            final int idx = ArrayUtilRt.find(expressions, newExpression);
            if (idx > -1) {
              final PsiParameter parameter = method.getParameterList().getParameter(idx);
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

  private static HighlightInfo checkTypeParameterWithinItsBound(@NotNull PsiTypeParameter classParameter,
                                                                @NotNull PsiSubstitutor substitutor,
                                                                @NotNull PsiType type,
                                                                @NotNull PsiElement typeElement2Highlight,
                                                                @Nullable PsiReferenceParameterList referenceParameterList) {
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

        String description = JavaErrorBundle.message(messageKey,
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

  @NotNull
  private static String typeParameterListOwnerDescription(@NotNull PsiTypeParameterListOwner typeParameterListOwner) {
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

  @NotNull
  private static String typeParameterListOwnerCategoryDescription(@NotNull PsiTypeParameterListOwner typeParameterListOwner) {
    if (typeParameterListOwner instanceof PsiClass) {
      return JavaErrorBundle.message("generics.holder.type");
    }
    else if (typeParameterListOwner instanceof PsiMethod) {
      return JavaErrorBundle.message("generics.holder.method");
    }
    else {
      LOG.error("Unknown " + typeParameterListOwner);
      return "?";
    }
  }

  static HighlightInfo checkElementInTypeParameterExtendsList(@NotNull PsiReferenceList referenceList,
                                                              @NotNull PsiClass aClass,
                                                              @NotNull JavaResolveResult resolveResult,
                                                              @NotNull PsiElement element) {
    final PsiJavaCodeReferenceElement[] referenceElements = referenceList.getReferenceElements();
    PsiClass extendFrom = (PsiClass)resolveResult.getElement();
    if (extendFrom == null) return null;
    HighlightInfo errorResult = null;
    if (!extendFrom.isInterface() && referenceElements.length != 0 && element != referenceElements[0]) {
      String description = JavaErrorBundle.message("interface.expected");
      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
      PsiClassType type =
        JavaPsiFacade.getElementFactory(aClass.getProject()).createType(extendFrom, resolveResult.getSubstitutor());
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createMoveBoundClassToFrontFix(aClass, type), null);
    }
    else if (referenceElements.length != 0 && element != referenceElements[0] && referenceElements[0].resolve() instanceof PsiTypeParameter) {
      final String description = JavaErrorBundle.message("type.parameter.cannot.be.followed.by.other.bounds");
      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
      PsiClassType type =
        JavaPsiFacade.getElementFactory(aClass.getProject()).createType(extendFrom, resolveResult.getSubstitutor());
      QuickFixAction.registerQuickFixAction(errorResult, QUICK_FIX_FACTORY.createExtendsListFix(aClass, type, false), null);
    }
    return errorResult;
  }

  static HighlightInfo checkInterfaceMultipleInheritance(@NotNull PsiClass aClass) {
    final PsiClassType[] types = aClass.getSuperTypes();
    if (types.length < 2) return null;
    final TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
    return checkInterfaceMultipleInheritance(aClass,
                                             aClass,
                                             PsiSubstitutor.EMPTY, new HashMap<>(),
                                             new HashSet<>(), textRange);
  }

  private static HighlightInfo checkInterfaceMultipleInheritance(@NotNull PsiClass aClass,
                                                                 @NotNull PsiElement place,
                                                                 @NotNull PsiSubstitutor derivedSubstitutor,
                                                                 @NotNull Map<PsiClass, PsiSubstitutor> inheritedClasses,
                                                                 @NotNull Set<? super PsiClass> visited,
                                                                 @NotNull TextRange textRange) {
    final List<PsiClassType.ClassResolveResult> superTypes = PsiClassImplUtil.getScopeCorrectedSuperTypes(aClass, place.getResolveScope());
    for (PsiClassType.ClassResolveResult result : superTypes) {
      final PsiClass superClass = result.getElement();
      if (superClass == null || visited.contains(superClass)) continue;
      PsiSubstitutor superTypeSubstitutor = result.getSubstitutor();
      //JLS 4.8 The superclasses (respectively, superinterfaces) of a raw type are the erasures of the superclasses (superinterfaces) of any of the parameterizations of the generic type.
      superTypeSubstitutor = PsiUtil.isRawSubstitutor(aClass, derivedSubstitutor) ? JavaPsiFacade.getElementFactory(aClass.getProject()).createRawSubstitutor(superClass)
                                                                                  : MethodSignatureUtil.combineSubstitutors(superTypeSubstitutor, derivedSubstitutor);

      final PsiSubstitutor inheritedSubstitutor = inheritedClasses.get(superClass);
      if (inheritedSubstitutor != null) {
        final PsiTypeParameter[] typeParameters = superClass.getTypeParameters();
        for (PsiTypeParameter typeParameter : typeParameters) {
          PsiType type1 = inheritedSubstitutor.substitute(typeParameter);
          PsiType type2 = superTypeSubstitutor.substitute(typeParameter);

          if (!Comparing.equal(type1, type2)) {
            String description = JavaErrorBundle.message("generics.cannot.be.inherited.with.different.type.arguments",
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

  @NotNull
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

    return result;
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

  static HighlightInfo checkUnrelatedDefaultMethods(@NotNull PsiClass aClass, @NotNull PsiIdentifier classIdentifier) {
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
        hasConcrete |= !isDefault && !isAbstract && !method.hasModifierProperty(PsiModifier.STATIC);
      }

      if (!hasConcrete && defaults != null) {
        final PsiMethod defaultMethod = defaults.get(0);
        if (MethodSignatureUtil.findMethodBySuperMethod(aClass, defaultMethod, false) != null) continue;
        final PsiClass defaultMethodContainingClass = defaultMethod.getContainingClass();
        if (defaultMethodContainingClass == null) continue;
        final PsiMethod unrelatedMethod;
        if (abstracts != null) {
          unrelatedMethod = abstracts.get(0);
        }
        else if (defaults.size() > 1) {
          unrelatedMethod = defaults.get(1);
        }
        else {
          continue;
        }
        final PsiClass unrelatedMethodContainingClass = unrelatedMethod.getContainingClass();
        if (unrelatedMethodContainingClass == null) continue;
        if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT) && !(aClass instanceof PsiTypeParameter)
            && abstracts != null && unrelatedMethodContainingClass.isInterface()) {
          if (defaultMethodContainingClass.isInheritor(unrelatedMethodContainingClass, true) &&
              MethodSignatureUtil.isSubsignature(unrelatedMethod.getSignature(TypeConversionUtil.getSuperClassSubstitutor(unrelatedMethodContainingClass, defaultMethodContainingClass, PsiSubstitutor.EMPTY)),
                                                 defaultMethod.getSignature(PsiSubstitutor.EMPTY))) {
            continue;
          }
          final String key = aClass instanceof PsiEnumConstantInitializer || aClass.isRecord() ? 
                             "class.must.implement.method" : "class.must.be.abstract";
          final String message = JavaErrorBundle
            .message(key, HighlightUtil.formatClass(aClass, false), JavaHighlightUtil.formatMethod(abstracts.get(0)),
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

  private static boolean hasNotOverriddenAbstract(@NotNull List<? extends PsiClass> defaultContainingClasses, @NotNull PsiClass abstractMethodContainingClass) {
    return defaultContainingClasses.stream().noneMatch(containingClass -> belongToOneHierarchy(containingClass, abstractMethodContainingClass));
  }

  private static String hasUnrelatedDefaults(@NotNull List<? extends PsiClass> defaults) {
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

  private static HighlightInfo checkSameErasureNotSubSignatureOrSameClass(@NotNull MethodSignatureBackedByPsiMethod signatureToCheck,
                                                                          @NotNull HierarchicalMethodSignature superSignature,
                                                                          @NotNull PsiClass aClass,
                                                                          @NotNull PsiMethod superMethod) {
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
    if (checkEqualsSuper && atLeast17 && retErasure1 != null && retErasure2 != null) {
      differentReturnTypeErasure = !TypeConversionUtil.isAssignable(retErasure1, retErasure2);
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
    if (!javaVersionService.isCompilerVersionAtLeast(aClass, JavaSdkVersion.JDK_1_7)) {
      //javac <= 1.6 didn't check transitive overriding rules for interfaces
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
                                                     @NotNull TextRange textRange) {
     @NonNls final String key = sameClass ? "generics.methods.have.same.erasure" :
                               method.hasModifierProperty(PsiModifier.STATIC) ?
                               "generics.methods.have.same.erasure.hide" :
                               "generics.methods.have.same.erasure.override";
    String description = JavaErrorBundle.message(key, HighlightMethodUtil.createClashMethodMessage(method, superMethod, !sameClass));
    HighlightInfo info =
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
    QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createSameErasureButDifferentMethodsFix(method, superMethod));

    return info;
  }

  static HighlightInfo checkTypeParameterInstantiation(@NotNull PsiNewExpression expression) {
    PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
    if (classReference == null) return null;
    final JavaResolveResult result = classReference.advancedResolve(false);
    final PsiElement element = result.getElement();
    if (element instanceof PsiTypeParameter) {
      String description = JavaErrorBundle.message("generics.type.parameter.cannot.be.instantiated",
                                                   HighlightUtil.formatClass((PsiTypeParameter)element));
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(classReference).descriptionAndTooltip(description).create();
    }
    return null;
  }

  static HighlightInfo checkWildcardUsage(@NotNull PsiTypeElement typeElement) {
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
            String description = JavaErrorBundle.message("wildcard.type.cannot.be.instantiated", JavaHighlightUtil.formatType(type));
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description).create();
          }
        }
        else if (refParent instanceof PsiReferenceList) {
          PsiElement refPParent = refParent.getParent();
          if (!(refPParent instanceof PsiTypeParameter) || refParent != ((PsiTypeParameter)refPParent).getExtendsList()) {
            String description = JavaErrorBundle.message("generics.wildcard.not.expected");
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description).create();
          }
        }
      }
      else {
        String description = JavaErrorBundle.message("generics.wildcards.may.be.used.only.as.reference.parameters");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description).create();
      }
    }

    return null;
  }

  static HighlightInfo checkReferenceTypeUsedAsTypeArgument(@NotNull PsiTypeElement typeElement, @NotNull LanguageLevel level) {
    final PsiType type = typeElement.getType();
    if (type != PsiType.NULL && type instanceof PsiPrimitiveType ||
        type instanceof PsiWildcardType && ((PsiWildcardType)type).getBound() instanceof PsiPrimitiveType) {
      final PsiElement element = new PsiMatcherImpl(typeElement)
        .parent(PsiMatchers.hasClass(PsiReferenceParameterList.class))
        .parent(PsiMatchers.hasClass(PsiJavaCodeReferenceElement.class, PsiNewExpression.class))
        .getElement();
      if (element == null) return null;

      if (level.isAtLeast(LanguageLevel.JDK_X)) return null;

      String text = JavaErrorBundle.message("generics.type.argument.cannot.be.of.primitive.type");
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

  static HighlightInfo checkForeachExpressionTypeIsIterable(@NotNull PsiExpression expression) {
    if (expression.getType() == null) return null;
    final PsiType itemType = JavaGenericsUtil.getCollectionItemType(expression);
    if (itemType == null) {
      String description = JavaErrorBundle.message("foreach.not.applicable",
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
    HighlightFixUtil.registerChangeVariableTypeFixes(parameter, itemType, expression, highlightInfo);
    return highlightInfo;
  }

  //http://docs.oracle.com/javase/specs/jls/se7/html/jls-8.html#jls-8.9.2
  static HighlightInfo checkAccessStaticFieldFromEnumConstructor(@NotNull PsiReferenceExpression expr, @NotNull JavaResolveResult result) {
    final PsiElement resolved = result.getElement();

    if (!(resolved instanceof PsiField)) return null;
    if (!((PsiModifierListOwner)resolved).hasModifierProperty(PsiModifier.STATIC)) return null;
    if (PsiImplUtil.getSwitchLabel(expr) != null) return null;
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

    if (PsiUtil.isCompileTimeConstant(field)) return null;

    String description = JavaErrorBundle.message(
      "illegal.to.access.static.member.from.enum.constructor.or.instance.initializer",
      HighlightMessageUtil.getSymbolName(resolved, result.getSubstitutor())
    );

    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expr).descriptionAndTooltip(description).create();
  }

  static HighlightInfo checkEnumInstantiation(@NotNull PsiElement expression, @Nullable PsiClass aClass) {
    if (aClass != null && aClass.isEnum() &&
        !(expression instanceof PsiNewExpression && ((PsiNewExpression)expression).isArrayCreation())) {
      String description = JavaErrorBundle.message("enum.types.cannot.be.instantiated");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create();
    }
    return null;
  }

  static HighlightInfo checkGenericArrayCreation(@NotNull PsiElement element, @Nullable PsiType type) {
    if (type instanceof PsiArrayType) {
      if (!JavaGenericsUtil.isReifiableType(((PsiArrayType)type).getComponentType())) {
        String description = JavaErrorBundle.message("generic.array.creation");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
      }

      if (element instanceof PsiNewExpression && ((PsiNewExpression)element).getTypeArguments().length > 0) {
        String description = JavaErrorBundle.message("array.creation.with.type.arguments");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
      }
    }

    return null;
  }

  private static final MethodSignature ourValuesEnumSyntheticMethod = MethodSignatureUtil.createMethodSignature("values",
                                                                                                                PsiType.EMPTY_ARRAY,
                                                                                                                PsiTypeParameter.EMPTY_ARRAY,
                                                                                                                PsiSubstitutor.EMPTY);

  static boolean isEnumSyntheticMethod(@NotNull MethodSignature methodSignature, @NotNull Project project) {
    if (methodSignature.equals(ourValuesEnumSyntheticMethod)) return true;
    final PsiType javaLangString = PsiType.getJavaLangString(PsiManager.getInstance(project), GlobalSearchScope.allScope(project));
    final MethodSignature valueOfMethod = MethodSignatureUtil.createMethodSignature("valueOf", new PsiType[]{javaLangString}, PsiTypeParameter.EMPTY_ARRAY,
                                                                                    PsiSubstitutor.EMPTY);
    return MethodSignatureUtil.areSignaturesErasureEqual(valueOfMethod, methodSignature);
  }

  static HighlightInfo checkTypeParametersList(@NotNull PsiTypeParameterList list, PsiTypeParameter @NotNull [] parameters, @NotNull LanguageLevel level) {
    final PsiElement parent = list.getParent();
    if (parent instanceof PsiClass && ((PsiClass)parent).isEnum()) {
      String description = JavaErrorBundle.message("generics.enum.may.not.have.type.parameters");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).descriptionAndTooltip(description).create();
    }
    if (PsiUtil.isAnnotationMethod(parent)) {
      String description = JavaErrorBundle.message("generics.annotation.members.may.not.have.type.parameters");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).descriptionAndTooltip(description).create();
    }
    if (parent instanceof PsiClass && ((PsiClass)parent).isAnnotationType()) {
      String description = JavaErrorBundle.message("annotation.may.not.have.type.parameters");
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
          String message = JavaErrorBundle.message("generics.duplicate.type.parameter", name1);
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeParameter2).descriptionAndTooltip(message).create();
        }
      }
      if (!level.isAtLeast(LanguageLevel.JDK_1_7)) {
        for (PsiJavaCodeReferenceElement referenceElement : typeParameter1.getExtendsList().getReferenceElements()) {
          final PsiElement resolve = referenceElement.resolve();
          if (resolve instanceof PsiTypeParameter && ArrayUtilRt.find(parameters, resolve) > i) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(referenceElement).descriptionAndTooltip("Illegal forward reference").create();
          }
        }
      }
    }
    return null;
  }

  @NotNull
  static Collection<HighlightInfo> checkCatchParameterIsClass(@NotNull PsiParameter parameter) {
    if (!(parameter.getDeclarationScope() instanceof PsiCatchSection)) return Collections.emptyList();

    final List<PsiTypeElement> typeElements = PsiUtil.getParameterTypeElements(parameter);
    final Collection<HighlightInfo> result = new ArrayList<>(typeElements.size());
    for (PsiTypeElement typeElement : typeElements) {
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(typeElement.getType());
      if (aClass instanceof PsiTypeParameter) {
        final String message = JavaErrorBundle.message("generics.cannot.catch.type.parameters");
        result.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message).create());
      }
    }

    return result;
  }

  static HighlightInfo checkInstanceOfGenericType(@NotNull LanguageLevel languageLevel, @NotNull PsiInstanceOfExpression expression) {
    final PsiTypeElement checkTypeElement = expression.getCheckType();
    if (checkTypeElement == null) return null;
    PsiType checkType = checkTypeElement.getType();
    if (HighlightingFeature.PATTERNS.isSufficient(languageLevel)) {
      return isUnsafeCastInInstanceOf(checkTypeElement, checkType, expression.getOperand().getType());
    }
    return isIllegalForInstanceOf(checkType, checkTypeElement);
  }

  private static HighlightInfo isUnsafeCastInInstanceOf(@NotNull PsiTypeElement checkTypeElement, @NotNull PsiType checkType, @Nullable PsiType expressionType) {
    if (expressionType != null && JavaGenericsUtil.isUncheckedCast(checkType, expressionType)) {
      String description = JavaErrorBundle.message("unsafe.cast.in.instanceof",
                                                   expressionType.getPresentableText(), checkType.getPresentableText());
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(checkTypeElement).descriptionAndTooltip(description).create();
    }
    return null;
  }

  /**
   * 15.20.2 Type Comparison Operator instanceof
   * ReferenceType mentioned after the instanceof operator is reifiable
   */
  private static HighlightInfo isIllegalForInstanceOf(@Nullable PsiType type, @NotNull PsiTypeElement typeElement) {
    final PsiClass resolved = PsiUtil.resolveClassInClassTypeOnly(type);
    if (resolved instanceof PsiTypeParameter) {
      String description = JavaErrorBundle.message("generics.cannot.instanceof.type.parameters");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description).create();
    }

    if (!JavaGenericsUtil.isReifiableType(type)) {
      String description = JavaErrorBundle.message("illegal.generic.type.for.instanceof");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description).create();
    }

    return null;
  }

  static HighlightInfo checkClassObjectAccessExpression(@NotNull PsiClassObjectAccessExpression expression) {
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

  private static HighlightInfo canSelectFrom(@NotNull PsiClassType type, @NotNull PsiTypeElement operand) {
    PsiClass aClass = type.resolve();
    if (aClass instanceof PsiTypeParameter) {
      String description = JavaErrorBundle.message("cannot.select.dot.class.from.type.variable");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(operand).descriptionAndTooltip(description).create();
    }
    if (type.getParameters().length > 0) {
      return HighlightInfo
        .newHighlightInfo(HighlightInfoType.ERROR).range(operand).descriptionAndTooltip("Cannot select from parameterized type").create();
    }
    return null;
  }

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
        String description = JavaErrorBundle.message("method.does.not.override.super");
        HighlightInfo highlightInfo =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(overrideAnnotation).descriptionAndTooltip(description).create();
        QUICK_FIX_FACTORY.registerPullAsAbstractUpFixes(method, new QuickFixActionRegistrarImpl(highlightInfo));
        return highlightInfo;
      }
      PsiClass superClass = superMethod.getMethod().getContainingClass();
      if (languageLevel == LanguageLevel.JDK_1_5 &&
          superClass != null &&
          superClass.isInterface()) {
        String description = JavaErrorBundle.message("override.not.allowed.in.interfaces");
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

  static HighlightInfo checkSafeVarargsAnnotation(@NotNull PsiMethod method, @NotNull LanguageLevel languageLevel) {
    PsiModifierList list = method.getModifierList();
    final PsiAnnotation safeVarargsAnnotation = list.findAnnotation(CommonClassNames.JAVA_LANG_SAFE_VARARGS);
    if (safeVarargsAnnotation == null) {
      return null;
    }
    try {
      if (!method.isVarArgs()) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(safeVarargsAnnotation).descriptionAndTooltip(
          "@SafeVarargs is not allowed on methods with fixed arity").create();
      }
      if (!isSafeVarargsNoOverridingCondition(method, languageLevel)) {
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(safeVarargsAnnotation).descriptionAndTooltip(
          "@SafeVarargs is not allowed on non-final instance methods").create();
        QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createModifierListFix(method, PsiModifier.FINAL, true, true));
        return info;
      }

      PsiParameterList parameterList = method.getParameterList();
      final PsiParameter varParameter = Objects.requireNonNull(parameterList.getParameter(parameterList.getParametersCount() - 1));

      for (PsiReferenceExpression element : VariableAccessUtils.getVariableReferences(varParameter, method.getBody())) {
        if (!PsiUtil.isAccessedForReading(element)) {
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

  public static boolean isSafeVarargsNoOverridingCondition(@NotNull PsiMethod method, @NotNull LanguageLevel languageLevel) {
    return method.hasModifierProperty(PsiModifier.FINAL) ||
           method.hasModifierProperty(PsiModifier.STATIC) ||
           method.isConstructor() ||
           method.hasModifierProperty(PsiModifier.PRIVATE) && languageLevel.isAtLeast(LanguageLevel.JDK_1_9);
  }

  static void checkEnumConstantForConstructorProblems(@NotNull PsiEnumConstant enumConstant,
                                                      @NotNull HighlightInfoHolder holder,
                                                      @NotNull JavaSdkVersion javaSdkVersion) {
    PsiClass containingClass = enumConstant.getContainingClass();
    LOG.assertTrue(containingClass != null);
    if (enumConstant.getInitializingClass() == null && HighlightClassUtil.hasEnumConstantsWithInitializer(containingClass)) {
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
    PsiClassType type = JavaPsiFacade.getElementFactory(holder.getProject()).createType(containingClass);

    HighlightMethodUtil.checkConstructorCall(type.resolveGenerics(), enumConstant, type, null, holder, javaSdkVersion);
  }

  static HighlightInfo checkEnumSuperConstructorCall(@NotNull PsiMethodCallExpression expr) {
    PsiReferenceExpression methodExpression = expr.getMethodExpression();
    final PsiElement refNameElement = methodExpression.getReferenceNameElement();
    if (refNameElement != null && PsiKeyword.SUPER.equals(refNameElement.getText())) {
      final PsiMember constructor = PsiUtil.findEnclosingConstructorOrInitializer(expr);
      if (constructor instanceof PsiMethod) {
        final PsiClass aClass = constructor.getContainingClass();
        if (aClass != null && aClass.isEnum()) {
          final String message = JavaErrorBundle.message("call.to.super.is.not.allowed.in.enum.constructor");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expr).descriptionAndTooltip(message).create();
        }
      }
    }
    return null;
  }

  static HighlightInfo checkVarArgParameterIsLast(@NotNull PsiParameter parameter) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (declarationScope instanceof PsiMethod) {
      PsiParameter[] params = ((PsiMethod)declarationScope).getParameterList().getParameters();
      if (params[params.length - 1] != parameter) {
        String description = JavaErrorBundle.message("vararg.not.last.parameter");
        HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameter).descriptionAndTooltip(description).create();
        QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createMakeVarargParameterLastFix(parameter));
        return info;
      }
    }
    return null;
  }

  @NotNull
  static List<HighlightInfo> checkEnumConstantModifierList(@NotNull PsiModifierList modifierList) {
    List<HighlightInfo> list = null;
    PsiElement[] children = modifierList.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiKeyword) {
        if (list == null) {
          list = new ArrayList<>();
        }
        String description = JavaErrorBundle.message("modifiers.for.enum.constants");
        list.add(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(child).descriptionAndTooltip(description).create());
      }
    }
    return ObjectUtils.notNull(list, Collections.emptyList());
  }

  static HighlightInfo checkParametersAllowed(@NotNull PsiReferenceParameterList refParamList) {
    final PsiElement parent = refParamList.getParent();
    if (parent instanceof PsiReferenceExpression) {
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression) && !(parent instanceof PsiMethodReferenceExpression)) {
        final String message = JavaErrorBundle.message("generics.reference.parameters.not.allowed");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refParamList).descriptionAndTooltip(message).create();
      }
    }

    return null;
  }

  static HighlightInfo checkParametersOnRaw(@NotNull PsiReferenceParameterList refParamList) {
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
                               ? JavaErrorBundle.message("generics.type.arguments.on.raw.type")
                               : JavaErrorBundle.message("generics.type.arguments.on.raw.method");

        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refParamList).descriptionAndTooltip(message).create();
      }
    }
    return null;
  }

  static HighlightInfo checkGenericCannotExtendException(@NotNull PsiReferenceList list) {
    PsiElement parent = list.getParent();
    if (parent instanceof PsiClass) {
      PsiClass klass = (PsiClass)parent;
      if (hasGenericSignature(klass) && klass.getExtendsList() == list) {
        PsiClass throwableClass = null;
        for (PsiJavaCodeReferenceElement refElement : list.getReferenceElements()) {
          PsiElement resolved = refElement.resolve();
          if (!(resolved instanceof PsiClass)) continue;
          if (throwableClass == null) {
            throwableClass =
              JavaPsiFacade.getInstance(klass.getProject()).findClass(CommonClassNames.JAVA_LANG_THROWABLE, klass.getResolveScope());
          }
          if (InheritanceUtil.isInheritorOrSelf((PsiClass)resolved, throwableClass, true)) {
            String message = JavaErrorBundle.message("generic.extend.exception");
            HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refElement).descriptionAndTooltip(message).create();
            PsiClassType classType = JavaPsiFacade.getElementFactory(klass.getProject()).createType((PsiClass)resolved);
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
            String message = JavaErrorBundle.message("generic.extend.exception");
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refElement).descriptionAndTooltip(message).create();
          }
        }
      }
    }

    return null;
  }

  static HighlightInfo checkGenericCannotExtendException(@NotNull PsiAnonymousClass anonymousClass) {
    if (hasGenericSignature(anonymousClass) &&
        InheritanceUtil.isInheritor(anonymousClass, true, CommonClassNames.JAVA_LANG_THROWABLE)) {
      String message = JavaErrorBundle.message("generic.extend.exception");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(anonymousClass.getBaseClassReference()).descriptionAndTooltip(message).create();
    }
    return null;
  }

  private static boolean hasGenericSignature(@NotNull PsiClass klass) {
    PsiClass containingClass = klass;
    while (containingClass != null && PsiUtil.isLocalOrAnonymousClass(containingClass)) {
      if (containingClass.hasTypeParameters()) return true;
      containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class);
    }
    return containingClass != null && PsiUtil.typeParametersIterator(containingClass).hasNext();
  }

  static HighlightInfo checkEnumMustNotBeLocal(@NotNull PsiClass aClass) {
    if (!aClass.isEnum()) return null;
    PsiElement parent = aClass.getParent();
    if (!(parent instanceof PsiClass || parent instanceof PsiFile || parent instanceof PsiClassLevelDeclarationStatement)) {
      String description = JavaErrorBundle.message("local.enum");
      TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description).create();
    }
    return null;
  }

  static HighlightInfo checkSelectStaticClassFromParameterizedType(@Nullable PsiElement resolved, @NotNull PsiJavaCodeReferenceElement ref) {
    if (resolved instanceof PsiClass && ((PsiClass)resolved).hasModifierProperty(PsiModifier.STATIC)) {
      final PsiElement qualifier = ref.getQualifier();
      if (qualifier instanceof PsiJavaCodeReferenceElement) {
        final PsiReferenceParameterList parameterList = ((PsiJavaCodeReferenceElement)qualifier).getParameterList();
        if (parameterList != null && parameterList.getTypeArguments().length > 0) {
          final String message = JavaErrorBundle.message("generics.select.static.class.from.parameterized.type",
                                                         HighlightUtil.formatClass((PsiClass)resolved));
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameterList).descriptionAndTooltip(message).create();
        }
      }
    }
    return null;
  }

  static HighlightInfo checkCannotInheritFromTypeParameter(@Nullable PsiClass superClass, @NotNull PsiJavaCodeReferenceElement toHighlight) {
    if (superClass instanceof PsiTypeParameter) {
      String description = JavaErrorBundle.message("class.cannot.inherit.from.its.type.parameter");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(toHighlight).descriptionAndTooltip(description).create();
    }
    return null;
  }

  /**
   * http://docs.oracle.com/javase/specs/jls/se7/html/jls-4.html#jls-4.8
   */
  static HighlightInfo checkRawOnParameterizedType(@NotNull PsiJavaCodeReferenceElement parent, @Nullable PsiElement resolved) {
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

  static HighlightInfo checkCannotPassInner(@NotNull PsiJavaCodeReferenceElement ref) {
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
          PsiClass hiddenClass;
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

  private static PsiClass unqualifiedNestedClassReferenceAccessedViaContainingClassInheritance(@NotNull PsiClass containingClass,
                                                                                               @Nullable PsiReferenceList referenceList) {
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
        Map<PsiTypeParameter, PsiType> map = new HashMap<>();
        for (int j = 0; j < Math.min(classTypeParameters.length, actualTypeParameters.length); j++) {
          PsiTypeParameter classTypeParameter = classTypeParameters[j];
          PsiType actualTypeParameter = actualTypeParameters[j];
          map.put(classTypeParameter, actualTypeParameter);
        }
        PsiSubstitutor substitutor = factory.createSubstitutor(map);
        PsiType suggestedType = factory.createType(aClass, substitutor);
        HighlightFixUtil.registerChangeVariableTypeFixes(variable, suggestedType, variable.getInitializer(), highlightInfo);
      }
    }
  }

  static HighlightInfo checkInferredIntersections(@NotNull PsiSubstitutor substitutor, @NotNull TextRange ref) {
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

  static HighlightInfo checkClassSupersAccessibility(@NotNull PsiClass aClass, @NotNull PsiElement ref) {
    return checkClassSupersAccessibility(aClass, ref.getResolveScope(), ref.getTextRange(), false);
  }

  private static HighlightInfo checkClassSupersAccessibility(@NotNull PsiClass aClass,
                                                             @NotNull GlobalSearchScope resolveScope,
                                                             @NotNull TextRange range,
                                                             boolean checkParameters) {
    final JavaPsiFacade factory = JavaPsiFacade.getInstance(aClass.getProject());
    for (PsiClassType superType : aClass.getSuperTypes()) {
      HashSet<PsiClass> checked = new HashSet<>();
      checked.add(aClass);
      final String notAccessibleErrorMessage = isTypeAccessible(superType, checked, checkParameters, resolveScope, factory);
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
        .range(ref)
        .create();
    }

    return null;
  }

  @Nullable
  private static String isTypeAccessible(@Nullable PsiType type,
                                         @NotNull Set<? super PsiClass> classes,
                                         boolean checkParameters,
                                         @NotNull GlobalSearchScope resolveScope,
                                         @NotNull JavaPsiFacade factory) {
    type = PsiClassImplUtil.correctType(type, resolveScope);

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

      if (!checkParameters){
        return null;
      }

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

    return null;
  }

  static HighlightInfo checkTypeParameterOverrideEquivalentMethods(@NotNull PsiClass aClass, @NotNull LanguageLevel level) {
    if (aClass instanceof PsiTypeParameter && level.isAtLeast(LanguageLevel.JDK_1_7)) {
      final PsiReferenceList extendsList = aClass.getExtendsList();
      if (extendsList != null && extendsList.getReferenceElements().length > 1) {
        //todo suppress erased methods which come from the same class
        final Collection<HighlightInfo> result = checkOverrideEquivalentMethods(aClass);
        if (!result.isEmpty()) {
          return result.iterator().next();
        }
      }
    }
    return null;
  }
}
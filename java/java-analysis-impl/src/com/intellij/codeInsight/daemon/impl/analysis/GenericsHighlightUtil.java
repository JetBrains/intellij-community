// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.core.JavaPsiBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.InstanceOfUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public final class GenericsHighlightUtil {
  private static final Logger LOG = Logger.getInstance(GenericsHighlightUtil.class);

  private GenericsHighlightUtil() { }

  static HighlightInfo.Builder checkInferredTypeArguments(@NotNull PsiTypeParameterListOwner listOwner,
                                                  @NotNull PsiElement call,
                                                  @NotNull PsiSubstitutor substitutor) {
    PsiTypeParameter[] typeParameters = listOwner.getTypeParameters();
    Pair<PsiTypeParameter, PsiType> inferredTypeArgument = GenericsUtil.findTypeParameterWithBoundError(typeParameters, substitutor,
                                                                                                              call, false);
    if (inferredTypeArgument != null) {
      PsiType extendsType = inferredTypeArgument.second;
      PsiTypeParameter typeParameter = inferredTypeArgument.first;
      PsiClass boundClass = extendsType instanceof PsiClassType classType ? classType.resolve() : null;

      @NonNls String messageKey = boundClass == null || typeParameter.isInterface() == boundClass.isInterface()
                                  ? "generics.inferred.type.for.type.parameter.is.not.within.its.bound.extend"
                                  : "generics.inferred.type.for.type.parameter.is.not.within.its.bound.implement";

      String description = JavaErrorBundle.message(
        messageKey,
        HighlightUtil.formatClass(typeParameter),
        JavaHighlightUtil.formatType(extendsType),
        JavaHighlightUtil.formatType(substitutor.substitute(typeParameter))
      );
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(call).descriptionAndTooltip(description);
    }

    return null;
  }

  static HighlightInfo.Builder checkParameterizedReferenceTypeArguments(@Nullable PsiElement resolved,
                                                                @NotNull PsiJavaCodeReferenceElement referenceElement,
                                                                @NotNull PsiSubstitutor substitutor,
                                                                @NotNull JavaSdkVersion javaSdkVersion) {
    if (!(resolved instanceof PsiTypeParameterListOwner typeParameterListOwner)) return null;
    return checkReferenceTypeArgumentList(typeParameterListOwner, referenceElement.getParameterList(), substitutor, true, javaSdkVersion);
  }

  static HighlightInfo.Builder checkReferenceTypeArgumentList(@NotNull PsiTypeParameterListOwner typeParameterListOwner,
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
          String description = JavaErrorBundle.message("generics.diamond.not.applicable");
          HighlightInfo.Builder builder =
            HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(referenceParameterList).descriptionAndTooltip(description);
          IntentionAction action = QuickFixFactory.getInstance().createDeleteFix(referenceParameterList);
          builder.registerFix(action, null, null, null, null);
          return builder;
        }
        inferenceResult = ((PsiDiamondType)referenceElements[0].getType()).resolveInferredTypes();
        String errorMessage = inferenceResult.getErrorMessage();
        if (errorMessage != null) {
          PsiType expectedType = detectExpectedType(referenceParameterList);
          if (!(inferenceResult.failedToInfer() && expectedType instanceof PsiClassType classType && classType.isRaw())) {
            HighlightInfo.Builder builder = HighlightInfo
              .newHighlightInfo(HighlightInfoType.ERROR).range(referenceParameterList).descriptionAndTooltip(errorMessage);
            if (inferenceResult == PsiDiamondType.DiamondInferenceResult.ANONYMOUS_INNER_RESULT) {
              if (!PsiUtil.isLanguageLevel9OrHigher(referenceParameterList)) {
                IntentionAction action = QuickFixFactory.getInstance().createIncreaseLanguageLevelFix(LanguageLevel.JDK_1_9);
                builder.registerFix(action, null, null, null, null);
              }
              return builder;
            }
            if (inferenceResult == PsiDiamondType.DiamondInferenceResult.EXPLICIT_CONSTRUCTOR_TYPE_ARGS) {
              return builder;
            }
          }
        }

        PsiElement parent = referenceParameterList.getParent().getParent();
        if (parent instanceof PsiAnonymousClass anonymousClass &&
            ContainerUtil.exists(anonymousClass.getMethods(),
                                 method -> !method.hasModifierProperty(PsiModifier.PRIVATE) && method.findSuperMethods().length == 0)) {
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(referenceParameterList)
            .descriptionAndTooltip(JavaPsiBundle.message("diamond.error.anonymous.inner.classes.non.private"));
        }
      }
    }

    PsiTypeParameter[] typeParameters = typeParameterListOwner.getTypeParameters();
    int targetParametersNum = typeParameters.length;
    int refParametersNum = referenceParameterList == null ? 0 : referenceParameterList.getTypeArguments().length;
    if (targetParametersNum != refParametersNum && refParametersNum != 0) {
      String description;
      if (targetParametersNum == 0) {
        if (PsiTreeUtil.getParentOfType(referenceParameterList, PsiCall.class) != null &&
            typeParameterListOwner instanceof PsiMethod psiMethod &&
            (javaSdkVersion.isAtLeast(JavaSdkVersion.JDK_1_7) || hasSuperMethodsWithTypeParams(psiMethod))) {
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
        HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(referenceParameterList).descriptionAndTooltip(description);
        if (registerIntentions) {
          if (typeParameterListOwner instanceof PsiClass psiClass) {
            IntentionAction action =
              QuickFixFactory.getInstance().createChangeClassSignatureFromUsageFix(psiClass, referenceParameterList);
            builder.registerFix(action, null, null, null, null);
          }

          PsiElement grandParent = referenceParameterList.getParent().getParent();
          if (grandParent instanceof PsiTypeElement) {
            PsiElement variable = PsiTreeUtil.skipParentsOfType(grandParent, PsiTypeElement.class);
            if (variable instanceof PsiVariable) {
              if (targetParametersNum == 0) {
                IntentionAction action = PriorityIntentionActionWrapper
                  .highPriority(QuickFixFactory.getInstance().createDeleteFix(referenceParameterList));
                builder.registerFix(action, null, null, null, null);
              }
              registerVariableParameterizedTypeFixes(builder, (PsiVariable)variable, referenceParameterList, javaSdkVersion);
            }
          }
        }
        return builder;
      }
    }

    // bounds check
    if (targetParametersNum > 0 && refParametersNum != 0) {
      if (inferenceResult != null) {
        PsiType[] types = inferenceResult.getTypes();
        for (int i = 0; i < typeParameters.length; i++) {
          PsiType type = types[i];
          HighlightInfo.Builder builder = checkTypeParameterWithinItsBound(typeParameters[i], substitutor, type, referenceElements[0], referenceParameterList);
          if (builder != null) return builder;
        }
      }
      else {
        for (int i = 0; i < typeParameters.length; i++) {
          PsiTypeElement typeElement = referenceElements[i];
          HighlightInfo.Builder builder = checkTypeParameterWithinItsBound(typeParameters[i], substitutor, typeElement.getType(), typeElement, referenceParameterList);
          if (builder != null) return builder;
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
    PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(referenceParameterList, PsiNewExpression.class);
    LOG.assertTrue(newExpression != null);
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

  private static HighlightInfo.Builder checkTypeParameterWithinItsBound(@NotNull PsiTypeParameter classParameter,
                                                                @NotNull PsiSubstitutor substitutor,
                                                                @NotNull PsiType type,
                                                                @NotNull PsiElement typeElement2Highlight,
                                                                @Nullable PsiReferenceParameterList referenceParameterList) {
    PsiClass referenceClass = type instanceof PsiClassType classType ? classType.resolve() : null;
    PsiType psiType = substitutor.substitute(classParameter);
    if (psiType instanceof PsiClassType && !(PsiUtil.resolveClassInType(psiType) instanceof PsiTypeParameter)) {
      if (GenericsUtil.checkNotInBounds(type, psiType, referenceParameterList)) {
        String description = JavaErrorBundle.message("actual.type.argument.contradict.inferred.type");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement2Highlight).descriptionAndTooltip(description);
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
        String description = JavaErrorBundle.message(extend
                                                     ? "generics.type.parameter.is.not.within.its.bound.extend"
                                                     : "generics.type.parameter.is.not.within.its.bound.implement",
                                                     referenceClass != null
                                                     ? HighlightUtil.formatClass(referenceClass)
                                                     : type.getPresentableText(),
                                                     JavaHighlightUtil.formatType(bound));

        HighlightInfo.Builder builder =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement2Highlight).descriptionAndTooltip(description);
        if (bound instanceof PsiClassType && referenceClass != null) {
          IntentionAction fix = QuickFixFactory.getInstance().createExtendsListFix(referenceClass, (PsiClassType)bound, true);
          builder.registerFix(fix, null, HighlightDisplayKey.getDisplayNameByKey(null), null, null);
        }
        return builder;
      }
    }
    return null;
  }

  @NotNull
  private static String typeParameterListOwnerDescription(@NotNull PsiTypeParameterListOwner typeParameterListOwner) {
    if (typeParameterListOwner instanceof PsiClass psiClass) {
      return HighlightUtil.formatClass(psiClass);
    }
    else if (typeParameterListOwner instanceof PsiMethod psiMethod) {
      return JavaHighlightUtil.formatMethod(psiMethod);
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

  static HighlightInfo.Builder checkElementInTypeParameterExtendsList(@NotNull PsiReferenceList referenceList,
                                                              @NotNull PsiTypeParameter typeParameter,
                                                              @NotNull JavaResolveResult resolveResult,
                                                              @NotNull PsiElement element) {
    PsiJavaCodeReferenceElement[] referenceElements = referenceList.getReferenceElements();
    PsiClass extendFrom = (PsiClass)resolveResult.getElement();
    if (extendFrom == null) return null;
    HighlightInfo.Builder errorResult = null;
    if (!extendFrom.isInterface() && referenceElements.length != 0 && element != referenceElements[0]) {
      String description = JavaErrorBundle.message("interface.expected");
      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description);
      PsiClassType type =
        JavaPsiFacade.getElementFactory(typeParameter.getProject()).createType(extendFrom, resolveResult.getSubstitutor());
      IntentionAction action = QuickFixFactory.getInstance().createMoveBoundClassToFrontFix(typeParameter, type);
      errorResult.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(null), null, null);
    }
    else if (referenceElements.length != 0 && element != referenceElements[0] && referenceElements[0].resolve() instanceof PsiTypeParameter) {
      String description = JavaErrorBundle.message("type.parameter.cannot.be.followed.by.other.bounds");
      errorResult = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description);
      PsiClassType type =
        JavaPsiFacade.getElementFactory(typeParameter.getProject()).createType(extendFrom, resolveResult.getSubstitutor());
      IntentionAction action = QuickFixFactory.getInstance().createExtendsListFix(typeParameter, type, false);
      errorResult.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(null), null, null);
    }
    return errorResult;
  }

  static HighlightInfo.Builder checkInterfaceMultipleInheritance(@NotNull PsiClass aClass) {
    PsiClassType[] types = aClass.getSuperTypes();
    if (types.length < 2) return null;
    TextRange textRange = HighlightNamesUtil.getClassDeclarationTextRange(aClass);
    return checkInterfaceMultipleInheritance(aClass,
                                             aClass,
                                             PsiSubstitutor.EMPTY, new HashMap<>(),
                                             new HashSet<>(), textRange);
  }

  private static HighlightInfo.Builder checkInterfaceMultipleInheritance(@NotNull PsiClass aClass,
                                                                 @NotNull PsiElement place,
                                                                 @NotNull PsiSubstitutor derivedSubstitutor,
                                                                 @NotNull Map<PsiClass, PsiSubstitutor> inheritedClasses,
                                                                 @NotNull Set<? super PsiClass> visited,
                                                                 @NotNull TextRange textRange) {
    List<PsiClassType.ClassResolveResult> superTypes = PsiClassImplUtil.getScopeCorrectedSuperTypes(aClass, place.getResolveScope());
    for (PsiClassType.ClassResolveResult result : superTypes) {
      PsiClass superClass = result.getElement();
      if (superClass == null || visited.contains(superClass)) continue;
      PsiSubstitutor superTypeSubstitutor = result.getSubstitutor();
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(aClass.getProject());
      //JLS 4.8 The superclasses (respectively, superinterfaces) of a raw type are the erasures of the superclasses (superinterfaces) of any of the parameterizations of the generic type.
      superTypeSubstitutor = PsiUtil.isRawSubstitutor(aClass, derivedSubstitutor) ? elementFactory.createRawSubstitutor(superClass)
                                                                                  : MethodSignatureUtil.combineSubstitutors(superTypeSubstitutor, derivedSubstitutor);

      PsiSubstitutor inheritedSubstitutor = inheritedClasses.get(superClass);
      if (inheritedSubstitutor != null) {
        PsiTypeParameter[] typeParameters = superClass.getTypeParameters();
        for (PsiTypeParameter typeParameter : typeParameters) {
          PsiType type1 = inheritedSubstitutor.substitute(typeParameter);
          PsiType type2 = superTypeSubstitutor.substitute(typeParameter);

          if (!Comparing.equal(type1, type2)) {
            String description;
            if (type1 != null && type2 != null) {
              description = JavaErrorBundle.message("generics.cannot.be.inherited.with.different.type.arguments",
                                                    HighlightUtil.formatClass(superClass),
                                                    JavaHighlightUtil.formatType(type1),
                                                    JavaHighlightUtil.formatType(type2));
            }
            else {
              description = JavaErrorBundle.message("generics.cannot.be.inherited.as.raw.and.generic",
                                                    HighlightUtil.formatClass(superClass), 
                                                    JavaHighlightUtil.formatType(type1 != null ? type1 : type2));
            }
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description);
          }
        }
      }
      inheritedClasses.put(superClass, superTypeSubstitutor);
      visited.add(superClass);
      HighlightInfo.Builder builder = checkInterfaceMultipleInheritance(superClass, place, superTypeSubstitutor, inheritedClasses, visited, textRange);
      visited.remove(superClass);

      if (builder != null) return builder;
    }
    return null;
  }

  static void computeOverrideEquivalentMethodErrors(@NotNull PsiClass aClass,
                                                    @NotNull Set<? super PsiClass> overrideEquivalentMethodsVisitedClasses,
                                                    @NotNull Map<PsiMember, HighlightInfo.Builder> overrideEquivalentMethodsErrors) {
    if (overrideEquivalentMethodsVisitedClasses.add(aClass)) {
      Collection<HierarchicalMethodSignature> signaturesWithSupers = aClass.getVisibleSignatures();
      PsiManager manager = aClass.getManager();
      Map<MethodSignature, MethodSignatureBackedByPsiMethod> sameErasureMethods =
        MethodSignatureUtil.createErasedMethodSignatureMap();

      Set<MethodSignature> foundProblems = MethodSignatureUtil.createErasedMethodSignatureSet();
      for (HierarchicalMethodSignature signature : signaturesWithSupers) {
        Pair<PsiMember, HighlightInfo.Builder> pair = checkSameErasureNotSubSignatureInner(signature, manager, aClass, sameErasureMethods);
        if (pair != null && foundProblems.add(signature)) {
          overrideEquivalentMethodsErrors.put(pair.getFirst(), pair.getSecond());
        }
        if (aClass instanceof PsiTypeParameter) {
          HighlightInfo.Builder info =
            HighlightMethodUtil.checkMethodIncompatibleReturnType(signature, signature.getSuperSignatures(), true,
                                                                  HighlightNamesUtil.getClassDeclarationTextRange(aClass),
                                                                  null);
          if (info != null) {
            overrideEquivalentMethodsErrors.put(aClass, info);
          }
        }
      }
    }
  }

  static HighlightInfo.Builder checkDefaultMethodOverridesMemberOfJavaLangObject(@NotNull LanguageLevel languageLevel,
                                                                                 @NotNull PsiClass aClass,
                                                                                 @NotNull PsiMethod method,
                                                                                 @NotNull PsiElement methodIdentifier) {
    if (languageLevel.isLessThan(LanguageLevel.JDK_1_8) || !aClass.isInterface() || !method.hasModifierProperty(PsiModifier.DEFAULT)) {
      return null;
    }
    return doesMethodOverrideMemberOfJavaLangObject(method)
           ? HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
             .descriptionAndTooltip(JavaErrorBundle.message("default.method.overrides.object.member", method.getName()))
             .range(methodIdentifier)
           : null;
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

  /**
   * @param skipMethodInSelf pass false to check if method in {@code aClass} can be deleted
   *
   * @return error message if class inherits 2 unrelated default methods or abstract and default methods which do not belong to one hierarchy
   */
  @Nullable
  public static @NlsContexts.DetailedDescription String getUnrelatedDefaultsMessage(@NotNull PsiClass aClass,
                                                                                    @NotNull Collection<? extends PsiMethod> overrideEquivalentSuperMethods,
                                                                                    boolean skipMethodInSelf) {
    if (overrideEquivalentSuperMethods.size() <= 1) return null;
    boolean isInterface = aClass.isInterface();
    List<PsiMethod> defaults = null;
    List<PsiMethod> abstracts = null;
    boolean hasConcrete = false;
    for (PsiMethod method : overrideEquivalentSuperMethods) {
      boolean isDefault = method.hasModifierProperty(PsiModifier.DEFAULT);
      boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
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
      PsiMethod defaultMethod = defaults.get(0);
      if (!skipMethodInSelf && MethodSignatureUtil.findMethodBySuperMethod(aClass, defaultMethod, false) != null) return null;
      PsiClass defaultMethodContainingClass = defaultMethod.getContainingClass();
      if (defaultMethodContainingClass == null) return null;
      PsiMethod unrelatedMethod;
      if (abstracts != null) {
        unrelatedMethod = abstracts.get(0);
      }
      else if (defaults.size() > 1) {
        unrelatedMethod = defaults.get(1);
      }
      else {
        return null;
      }
      PsiClass unrelatedMethodContainingClass = unrelatedMethod.getContainingClass();
      if (unrelatedMethodContainingClass == null) return null;
      if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT) && !(aClass instanceof PsiTypeParameter)
          && abstracts != null && unrelatedMethodContainingClass.isInterface()) {
        if (defaultMethodContainingClass.isInheritor(unrelatedMethodContainingClass, true) &&
            MethodSignatureUtil.isSubsignature(unrelatedMethod.getSignature(TypeConversionUtil.getSuperClassSubstitutor(unrelatedMethodContainingClass, defaultMethodContainingClass, PsiSubstitutor.EMPTY)),
                                               defaultMethod.getSignature(PsiSubstitutor.EMPTY))) {
          return null;
        }
        String key = aClass instanceof PsiEnumConstantInitializer || aClass.isRecord() || aClass.isEnum() ?
                           "class.must.implement.method" : "class.must.be.abstract";
        return JavaErrorBundle.message(key,
                                       HighlightUtil.formatClass(aClass, false),
                                       JavaHighlightUtil.formatMethod(abstracts.get(0)),
                                       HighlightUtil.formatClass(unrelatedMethodContainingClass, false));
      }
      if (isInterface || abstracts == null || unrelatedMethodContainingClass.isInterface()) {
        List<PsiClass> defaultContainingClasses = ContainerUtil.mapNotNull(defaults, PsiMethod::getContainingClass);
        String unrelatedDefaults = hasUnrelatedDefaults(defaultContainingClasses);
        if (unrelatedDefaults == null &&
            (abstracts == null || !hasNotOverriddenAbstract(defaultContainingClasses, unrelatedMethodContainingClass))) {
          return null;
        }

        if (unrelatedDefaults == null) {
          return JavaErrorBundle.message("text.class.inherits.abstract.and.default", HighlightUtil.formatClass(aClass),
                                            JavaHighlightUtil.formatMethod(defaultMethod),
                                            HighlightUtil.formatClass(defaultMethodContainingClass),
                                            HighlightUtil.formatClass(unrelatedMethodContainingClass));
        }
        else {
          return JavaErrorBundle.message("text.class.inherits.unrelated.defaults",
                                         HighlightUtil.formatClass(aClass),
                                         JavaHighlightUtil.formatMethod(defaultMethod), 
                                         unrelatedDefaults);
        }
      }
    }
    return null;
  }
  
  static HighlightInfo.Builder checkUnrelatedDefaultMethods(@NotNull PsiClass aClass, @NotNull PsiIdentifier classIdentifier) {
    Map<? extends MethodSignature, Set<PsiMethod>> overrideEquivalent = PsiSuperMethodUtil.collectOverrideEquivalents(aClass);

    for (Set<PsiMethod> overrideEquivalentMethods : overrideEquivalent.values()) {
      String errorMessage = getUnrelatedDefaultsMessage(aClass, overrideEquivalentMethods, false);
      if (errorMessage != null) {
        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(classIdentifier)
          .descriptionAndTooltip(errorMessage);
        IntentionAction action = QuickFixFactory.getInstance().createImplementMethodsFix(aClass);
        info.registerFix(action, null, null, null, null);
        return info;
      }
    }
    return null;
  }

  private static boolean belongToOneHierarchy(@NotNull PsiClass defaultMethodContainingClass, @NotNull PsiClass unrelatedMethodContainingClass) {
    return defaultMethodContainingClass.isInheritor(unrelatedMethodContainingClass, true) ||
           unrelatedMethodContainingClass.isInheritor(defaultMethodContainingClass, true);
  }

  private static boolean hasNotOverriddenAbstract(@NotNull List<? extends PsiClass> defaultContainingClasses, @NotNull PsiClass abstractMethodContainingClass) {
    return !ContainerUtil.exists(defaultContainingClasses, containingClass -> belongToOneHierarchy(containingClass, abstractMethodContainingClass));
  }

  private static @Nls String hasUnrelatedDefaults(@NotNull List<? extends PsiClass> defaults) {
    if (defaults.size() > 1) {
      PsiClass[] defaultClasses = defaults.toArray(PsiClass.EMPTY_ARRAY);
      ArrayList<PsiClass> classes = new ArrayList<>(defaults);
      for (PsiClass aClass1 : defaultClasses) {
        classes.removeIf(aClass2 -> aClass1.isInheritor(aClass2, true));
      }

      if (classes.size() > 1) {
        return IdeBundle.message("x.and.y", HighlightUtil.formatClass(classes.get(0)),
                                 HighlightUtil.formatClass(classes.get(1)));
      }
    }

    return null;
  }

  static HighlightInfo.Builder checkUnrelatedConcrete(@NotNull PsiClass psiClass,
                                                      @NotNull PsiIdentifier classIdentifier) {
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
        PsiClass foundMethodContainingClass;
        if (foundMethod != null &&
            !foundMethod.hasModifierProperty(PsiModifier.ABSTRACT) &&
            !foundMethod.hasModifierProperty(PsiModifier.DEFAULT) &&
            (foundMethodContainingClass = foundMethod.getContainingClass()) != null) {
          String description =
            JavaErrorBundle.message("two.methods.are.inherited.with.same.signature",
                                    JavaHighlightUtil.formatMethod(foundMethod), HighlightUtil.formatClass(foundMethodContainingClass),
                                    JavaHighlightUtil.formatMethod(method), HighlightUtil.formatClass(containingClass));
          //todo override fix
          return HighlightInfo
            .newHighlightInfo(HighlightInfoType.ERROR).range(classIdentifier).descriptionAndTooltip(description)
            ;
        }
        overrideEquivalent.put(signature, method);
      }
    }
    return null;
  }

  private static Pair<PsiMember, HighlightInfo.Builder> checkSameErasureNotSubSignatureInner(@NotNull HierarchicalMethodSignature signature,
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
      Pair<PsiMember, HighlightInfo.Builder> pair = checkSameErasureNotSubSignatureOrSameClass(sameErasure, signature, aClass, method);
      if (pair != null) return pair;
    }
    List<HierarchicalMethodSignature> supers = signature.getSuperSignatures();
    for (HierarchicalMethodSignature superSignature : supers) {
      Pair<PsiMember, HighlightInfo.Builder> pair = checkSameErasureNotSubSignatureInner(superSignature, manager, aClass, sameErasureMethods);
      if (pair != null) return pair;

      if (superSignature.isRaw() && !signature.isRaw()) {
        PsiType[] parameterTypes = signature.getParameterTypes();
        PsiType[] erasedTypes = superSignature.getErasedParameterTypes();
        for (int i = 0; i < erasedTypes.length; i++) {
          if (!Comparing.equal(parameterTypes[i], erasedTypes[i])) {
            return Pair.create(aClass, getSameErasureMessage(false, method, superSignature.getMethod(),
                                         HighlightNamesUtil.getClassDeclarationTextRange(aClass)));
          }
        }
      }

    }
    return null;
  }

  private static Pair<PsiMember, HighlightInfo.Builder> checkSameErasureNotSubSignatureOrSameClass(@NotNull MethodSignatureBackedByPsiMethod signatureToCheck,
                                                                                  @NotNull HierarchicalMethodSignature superSignature,
                                                                                  @NotNull PsiClass aClass,
                                                                                  @NotNull PsiMethod superMethod) {
    PsiMethod checkMethod = signatureToCheck.getMethod();
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
      if (superContainingClass != null && !superContainingClass.isInterface() && checkContainingClass.isInterface() && !aClass.equals(superContainingClass)) return null;
    }
    if (aClass.equals(checkContainingClass)) {
      boolean sameClass = aClass.equals(superContainingClass);
      return Pair.create(checkMethod, getSameErasureMessage(sameClass, checkMethod, superMethod, HighlightNamesUtil.getMethodDeclarationTextRange(checkMethod)));
    }
    else {
      return Pair.create(aClass, getSameErasureMessage(false, checkMethod, superMethod, HighlightNamesUtil.getClassDeclarationTextRange(aClass)));
    }
  }

  private static HighlightInfo.Builder getSameErasureMessage(boolean sameClass, @NotNull PsiMethod method, @NotNull PsiMethod superMethod,
                                                             @NotNull TextRange textRange) {
    @NonNls String key = sameClass ? "generics.methods.have.same.erasure" :
                         method.hasModifierProperty(PsiModifier.STATIC) ?
                         "generics.methods.have.same.erasure.hide" :
                         "generics.methods.have.same.erasure.override";
    String description = JavaErrorBundle.message(key, HighlightMethodUtil.createClashMethodMessage(method, superMethod, !sameClass));
    HighlightInfo.Builder info =
      HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(textRange).descriptionAndTooltip(description);
    if (!(method instanceof SyntheticElement)) {
      IntentionAction action = QuickFixFactory.getInstance().createSameErasureButDifferentMethodsFix(method, superMethod);
      info.registerFix(action, null, null, null, null);
    }

    return info;
  }

  static HighlightInfo.Builder checkDiamondTypeNotAllowed(@NotNull PsiNewExpression expression) {
    PsiReferenceParameterList typeArgumentList = expression.getTypeArgumentList();
    PsiTypeElement[] typeParameterElements = typeArgumentList.getTypeParameterElements();
    if (typeParameterElements.length == 1 && typeParameterElements[0].getType() instanceof PsiDiamondType) {
      String description = JavaErrorBundle.message("diamond.operator.not.allowed.here");
      HighlightInfo.Builder info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeArgumentList).descriptionAndTooltip(description);
      info.registerFix(QuickFixFactory.getInstance().createDeleteFix(typeArgumentList), null, null, null, null);
      return info;
    }
    return null;
  }

  static HighlightInfo.Builder checkTypeParameterInstantiation(@NotNull PsiNewExpression expression) {
    PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
    if (classReference == null) return null;
    JavaResolveResult result = classReference.advancedResolve(false);
    PsiElement element = result.getElement();
    if (element instanceof PsiTypeParameter typeParameter) {
      String description = JavaErrorBundle.message("generics.type.parameter.cannot.be.instantiated",
                                                   HighlightUtil.formatClass(typeParameter));
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(classReference).descriptionAndTooltip(description);
    }
    return null;
  }

  static HighlightInfo.Builder checkWildcardUsage(@NotNull PsiTypeElement typeElement) {
    PsiType type = typeElement.getType();
    if (type instanceof PsiWildcardType) {
      if (typeElement.getParent() instanceof PsiReferenceParameterList) {
        PsiElement parent = typeElement.getParent().getParent();
        LOG.assertTrue(parent instanceof PsiJavaCodeReferenceElement, parent);
        PsiElement refParent = parent.getParent();
        if (refParent instanceof PsiAnonymousClass) refParent = refParent.getParent();
        if (refParent instanceof PsiNewExpression newExpression) {
          if (!(newExpression.getType() instanceof PsiArrayType)) {
            String description = JavaErrorBundle.message("wildcard.type.cannot.be.instantiated", JavaHighlightUtil.formatType(type));
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description);
          }
        }
        else if (refParent instanceof PsiReferenceList) {
          PsiElement refPParent = refParent.getParent();
          if (!(refPParent instanceof PsiTypeParameter typeParameter) || refParent != typeParameter.getExtendsList()) {
            String description = JavaErrorBundle.message("generics.wildcard.not.expected");
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description);
          }
        }
      }
      else if (!typeElement.isInferredType()){
        String description = JavaErrorBundle.message("generics.wildcards.may.be.used.only.as.reference.parameters");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description);
      }
    }

    return null;
  }

  static HighlightInfo.Builder checkReferenceTypeUsedAsTypeArgument(@NotNull PsiTypeElement typeElement) {
    PsiType type = typeElement.getType();
    PsiType wildCardBind = type instanceof PsiWildcardType wildcardType ? wildcardType.getBound() : null;
    if (type != PsiTypes.nullType() && type instanceof PsiPrimitiveType || wildCardBind instanceof PsiPrimitiveType) {
      PsiElement element = new PsiMatcherImpl(typeElement)
        .parent(PsiMatchers.hasClass(PsiReferenceParameterList.class))
        .parent(PsiMatchers.hasClass(PsiJavaCodeReferenceElement.class, PsiNewExpression.class))
        .getElement();
      if (element == null) return null;

      String text = JavaErrorBundle.message("generics.type.argument.cannot.be.of.primitive.type");
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(text);

      PsiPrimitiveType toConvert = (PsiPrimitiveType)(type instanceof PsiWildcardType ? wildCardBind : type);
      PsiClassType boxedType = toConvert.getBoxedType(typeElement);
      if (boxedType != null) {
        IntentionAction action = QuickFixFactory.getInstance().createReplacePrimitiveWithBoxedTypeAction(
          typeElement, toConvert.getPresentableText(), toConvert.getBoxedTypeName());
        builder.registerFix(action, null, null, null, null);
      }
      return builder;
    }

    return null;
  }

  static HighlightInfo.Builder checkForeachExpressionTypeIsIterable(@NotNull PsiExpression expression) {
    if (expression.getType() == null) return null;
    PsiType itemType = JavaGenericsUtil.getCollectionItemType(expression);
    if (itemType == null) {
      String description = JavaErrorBundle.message("foreach.not.applicable",
                                                   JavaHighlightUtil.formatType(expression.getType()));
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description);
      IntentionAction action = QuickFixFactory.getInstance().createNotIterableForEachLoopFix(expression);
      if (action != null) {
        builder.registerFix(action, null, null, null, null);
      }
      return builder;
    }
    return null;
  }

  static HighlightInfo.Builder checkForEachParameterType(@NotNull PsiForeachStatement statement, @NotNull PsiParameter parameter) {
    PsiExpression expression = statement.getIteratedValue();
    PsiType itemType = expression == null ? null : JavaGenericsUtil.getCollectionItemType(expression);
    if (itemType == null) return null;

    PsiType parameterType = parameter.getType();
    if (TypeConversionUtil.isAssignable(parameterType, itemType)) {
      return null;
    }
    if (IncompleteModelUtil.isIncompleteModel(statement) && IncompleteModelUtil.isPotentiallyConvertible(parameterType, itemType, expression)) {
      return null;
    }
    HighlightInfo.Builder builder = HighlightUtil.createIncompatibleTypeHighlightInfo(itemType, parameterType, parameter.getTextRange(), 0);
    HighlightFixUtil.registerChangeVariableTypeFixes(parameter, itemType, expression, builder);
    return builder;
  }

  //http://docs.oracle.com/javase/specs/jls/se7/html/jls-8.html#jls-8.9.2
  static HighlightInfo.Builder checkAccessStaticFieldFromEnumConstructor(@NotNull PsiReferenceExpression expr,
                                                                         @NotNull JavaResolveResult result) {
    PsiField field = ObjectUtils.tryCast(result.getElement(), PsiField.class);
    if (field == null) return null;

    PsiClass enumClass = getEnumClassForExpressionInInitializer(expr);
    if (enumClass == null || !isRestrictedStaticEnumField(field, enumClass)) return null;

    int fieldType = field instanceof PsiEnumConstant ? 2 : 1;
    PsiMember initializer = PsiUtil.findEnclosingConstructorOrInitializer(expr);
    int initializerType;
    if (initializer instanceof PsiMethod) {
      initializerType = 1;
    }
    else if (initializer instanceof PsiField) {
      initializerType = 2;
    }
    else {
      initializerType = 3;
    }
    String description = JavaErrorBundle.message("illegal.to.access.static.member.from.enum.constructor.or.instance.initializer",
                                                 fieldType, initializerType);
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expr).descriptionAndTooltip(description);
  }

  /**
   * @param field field to check
   * @param enumClass an enum class returned from {@link #getEnumClassForExpressionInInitializer(PsiExpression)}
   * @return true if given field cannot be referenced in constructors or instance initializers of given enum class.
   */
  public static boolean isRestrictedStaticEnumField(@NotNull PsiField field, @NotNull PsiClass enumClass) {
    if (!field.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (field.getContainingClass() != enumClass) return false;

    if (!JavaVersionService.getInstance().isAtLeast(field, JavaSdkVersion.JDK_1_6)) {
      PsiType type = field.getType();
      if (type instanceof PsiClassType classType && classType.resolve() == enumClass) return false;
    }

    return !PsiUtil.isCompileTimeConstant(field);
  }

  /**
   * @param expr expression to analyze
   * @return enum class, whose non-constant static fields cannot be used at given place,
   * null if there's no such restriction 
   */
  public static @Nullable PsiClass getEnumClassForExpressionInInitializer(@NotNull PsiExpression expr) {
    if (PsiImplUtil.getSwitchLabel(expr) != null) return null;
    PsiMember constructorOrInitializer = PsiUtil.findEnclosingConstructorOrInitializer(expr);
    if (constructorOrInitializer == null || constructorOrInitializer.hasModifierProperty(PsiModifier.STATIC)) return null;
    PsiClass enumClass = constructorOrInitializer instanceof PsiEnumConstantInitializer
                         ? (PsiClass)constructorOrInitializer
                         : constructorOrInitializer.getContainingClass();
    if (enumClass instanceof PsiEnumConstantInitializer) {
      enumClass = enumClass.getSuperClass();
    }
    return enumClass != null && enumClass.isEnum() ? enumClass : null;
  }

  static HighlightInfo.Builder checkEnumInstantiation(@NotNull PsiElement expression, @Nullable PsiClass aClass) {
    if (aClass != null && aClass.isEnum() &&
        !(expression instanceof PsiNewExpression newExpression && newExpression.isArrayCreation())) {
      String description = JavaErrorBundle.message("enum.types.cannot.be.instantiated");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description);
    }
    return null;
  }

  public static HighlightInfo.Builder checkGenericArrayCreation(@NotNull PsiElement element, @Nullable PsiType type) {
    if (type instanceof PsiArrayType arrayType) {
      if (element instanceof PsiNewExpression newExpression) {
        PsiReferenceParameterList typeArgumentList = newExpression.getTypeArgumentList();
        if (typeArgumentList.getTypeArgumentCount() > 0) {
          String description = JavaErrorBundle.message("array.creation.with.type.arguments");
          HighlightInfo.Builder info =
            HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeArgumentList).descriptionAndTooltip(description);
          info.registerFix(QuickFixFactory.getInstance().createDeleteFix(typeArgumentList), null, null, null, null);
          return info;
        }
        PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
        if (classReference != null) {
          PsiReferenceParameterList parameterList = classReference.getParameterList();
          if (parameterList != null) {
            PsiTypeElement[] typeParameterElements = parameterList.getTypeParameterElements();
            if (typeParameterElements.length == 1 && typeParameterElements[0].getType() instanceof PsiDiamondType) {
              String description = JavaErrorBundle.message("cannot.create.array.with.empty.diamond");
              HighlightInfo.Builder info =
                HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameterList).descriptionAndTooltip(description);
              info.registerFix(QuickFixFactory.getInstance().createDeleteFix(parameterList), null, null, null, null);
              return info;
            }
            if (typeParameterElements.length >= 1 && !JavaGenericsUtil.isReifiableType(arrayType.getComponentType())) {
              String description = JavaErrorBundle.message("generic.array.creation");
              HighlightInfo.Builder info =
                HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameterList).descriptionAndTooltip(description);
              info.registerFix(QuickFixFactory.getInstance().createDeleteFix(parameterList), null, null, null, null);
              return info;
            }
          }
        }
      }
      if (!JavaGenericsUtil.isReifiableType(arrayType.getComponentType())) {
        String description = JavaErrorBundle.message("generic.array.creation");
        if (element.getParent() instanceof PsiMethodReferenceExpression && element.getFirstChild() instanceof PsiTypeElement typeElement) {
          PsiJavaCodeReferenceElement referenceElement = PsiTreeUtil.findChildOfType(typeElement, PsiJavaCodeReferenceElement.class);
          if (referenceElement != null) {
            PsiReferenceParameterList parameterList = referenceElement.getParameterList();
            if (parameterList != null && parameterList.getTypeArgumentCount() > 0) {
              HighlightInfo.Builder info =
                HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameterList).descriptionAndTooltip(description);
              info.registerFix(QuickFixFactory.getInstance().createDeleteFix(parameterList), null, null, null, null);
              return info;
            }
          }
        }
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description);
      }
    }

    return null;
  }

  static HighlightInfo.Builder checkTypeParametersList(@NotNull PsiTypeParameterList list, PsiTypeParameter @NotNull [] parameters) {
    PsiElement parent = list.getParent();
    if (parent instanceof PsiClass psiClass && psiClass.isEnum()) {
      String description = JavaErrorBundle.message("generics.enum.may.not.have.type.parameters");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).descriptionAndTooltip(description);
    }
    if (PsiUtil.isAnnotationMethod(parent)) {
      String description = JavaErrorBundle.message("generics.annotation.members.may.not.have.type.parameters");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).descriptionAndTooltip(description);
    }
    if (parent instanceof PsiClass psiClass && psiClass.isAnnotationType()) {
      String description = JavaErrorBundle.message("annotation.may.not.have.type.parameters");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).descriptionAndTooltip(description);
    }

    for (int i = 0; i < parameters.length; i++) {
      PsiTypeParameter typeParameter1 = parameters[i];
      HighlightInfo.Builder cyclicInheritance = HighlightClassUtil.checkCyclicInheritance(typeParameter1);
      if (cyclicInheritance != null) return cyclicInheritance;
      String name1 = typeParameter1.getName();
      for (int j = i + 1; j < parameters.length; j++) {
        PsiTypeParameter typeParameter2 = parameters[j];
        String name2 = typeParameter2.getName();
        if (Comparing.strEqual(name1, name2)) {
          String message = JavaErrorBundle.message("generics.duplicate.type.parameter", name1);
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeParameter2).descriptionAndTooltip(message);
        }
      }
    }
    return null;
  }

  static void checkCatchParameterIsClass(@NotNull PsiParameter parameter, @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    if (!(parameter.getDeclarationScope() instanceof PsiCatchSection)) return;

    List<PsiTypeElement> typeElements = PsiUtil.getParameterTypeElements(parameter);
    for (PsiTypeElement typeElement : typeElements) {
      PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(typeElement.getType());
      if (aClass instanceof PsiTypeParameter) {
        String message = JavaErrorBundle.message("generics.cannot.catch.type.parameters");
        errorSink.accept(HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(message));
      }
    }
  }

  static HighlightInfo.Builder checkInstanceOfGenericType(@NotNull LanguageLevel languageLevel, @NotNull PsiInstanceOfExpression expression) {
    PsiTypeElement checkTypeElement = InstanceOfUtils.findCheckTypeElement(expression);
    if (checkTypeElement == null) return null;
    PsiType checkType = checkTypeElement.getType();
    if (JavaFeature.PATTERNS.isSufficient(languageLevel)) {
      PsiPrimaryPattern pattern = expression.getPattern();
      if (pattern != null) {
        return PatternHighlightingModel.getUncheckedPatternConversionError(pattern);
      }
      return isUnsafeCastInInstanceOf(checkTypeElement, checkType, expression.getOperand().getType());
    }
    return isIllegalForInstanceOf(checkType, checkTypeElement);
  }

  private static HighlightInfo.Builder isUnsafeCastInInstanceOf(@NotNull PsiTypeElement checkTypeElement, @NotNull PsiType checkType, @Nullable PsiType expressionType) {
    if (expressionType != null && JavaGenericsUtil.isUncheckedCast(checkType, expressionType)) {
      String description = JavaErrorBundle.message("unsafe.cast.in.instanceof",
                                                   expressionType.getPresentableText(), checkType.getPresentableText());
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(checkTypeElement).descriptionAndTooltip(description);
    }
    return null;
  }

  /**
   * 15.20.2 Type Comparison Operator instanceof
   * ReferenceType mentioned after the instanceof operator is reifiable
   */
  private static HighlightInfo.Builder isIllegalForInstanceOf(@Nullable PsiType type, @NotNull PsiTypeElement typeElement) {
    PsiClass resolved = PsiUtil.resolveClassInClassTypeOnly(type);
    if (resolved instanceof PsiTypeParameter) {
      String description = JavaErrorBundle.message("generics.cannot.instanceof.type.parameters");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description);
    }

    if (!JavaGenericsUtil.isReifiableType(type)) {
      String description = JavaErrorBundle.message("illegal.generic.type.for.instanceof");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description);
    }

    return null;
  }

  static HighlightInfo.Builder checkClassObjectAccessExpression(@NotNull PsiClassObjectAccessExpression expression) {
    PsiType type = expression.getOperand().getType();
    if (type instanceof PsiClassType classType) {
      return canSelectFrom(classType, expression.getOperand());
    }
    if (type instanceof PsiArrayType) {
      PsiType arrayComponentType = type.getDeepComponentType();
      if (arrayComponentType instanceof PsiClassType classType) {
        return canSelectFrom(classType, expression.getOperand());
      }
    }

    return null;
  }

  private static HighlightInfo.Builder canSelectFrom(@NotNull PsiClassType type, @NotNull PsiTypeElement operand) {
    PsiClass aClass = type.resolve();
    if (aClass instanceof PsiTypeParameter) {
      String description = JavaErrorBundle.message("cannot.select.dot.class.from.type.variable");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(operand).descriptionAndTooltip(description);
    }
    if (type.getParameters().length > 0) {
      final HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(operand).descriptionAndTooltip(
        JavaErrorBundle.message("cannot.select.from.parameterized.type"));
      final PsiJavaCodeReferenceElement referenceElement = operand.getInnermostComponentReferenceElement();
      if (referenceElement != null) {
        final PsiReferenceParameterList parameterList = referenceElement.getParameterList();
        if (parameterList != null) {
          info.registerFix(QuickFixFactory.getInstance().createDeleteFix(parameterList), null, null, null, null);
        }
      }
      return info;
    }
    return null;
  }

  static HighlightInfo.Builder checkOverrideAnnotation(@NotNull PsiMethod method,
                                               @NotNull PsiAnnotation overrideAnnotation,
                                               @NotNull LanguageLevel languageLevel) {
    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      QuickFixFactory factory = QuickFixFactory.getInstance();
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(overrideAnnotation)
        .registerFix(factory.createDeleteFix(overrideAnnotation), null, null, null, null)
        .descriptionAndTooltip(
          JavaErrorBundle.message("static.method.cannot.be.annotated.with.override"));
    }
    try {
      MethodSignatureBackedByPsiMethod superMethod = SuperMethodsSearch.search(method, null, true, false).findFirst();
      PsiClass psiClass = method.getContainingClass();
      if (psiClass != null) {
        if (superMethod != null && psiClass.isInterface()) {
          PsiMethod psiMethod = superMethod.getMethod();
          PsiClass superClass = psiMethod.getContainingClass();
          if (superClass != null &&
              CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName()) &&
              psiMethod.hasModifierProperty(PsiModifier.PROTECTED)) {
            superMethod = null;
          }
        } else if (superMethod == null) {
          if (IncompleteModelUtil.isIncompleteModel(psiClass)) {
            if (!IncompleteModelUtil.isHierarchyResolved(psiClass)) {
              return null;
            }
          } else {
            for (PsiClassType type : psiClass.getSuperTypes()) {
              // There's an unresolvable superclass: likely the error on @Override is induced.
              // Do not show an error on override, as it's reasonable to fix hierarchy first.
              if (type.resolve() == null) return null;
            }
          }
        }
      }
      if (superMethod == null) {
        if (JavaPsiRecordUtil.getRecordComponentForAccessor(method) != null) {
          return null;
        }
        String description = JavaErrorBundle.message("method.does.not.override.super");
        HighlightInfo.Builder builder =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(overrideAnnotation).descriptionAndTooltip(description);
        List<IntentionAction> registrar = new ArrayList<>();
        QuickFixFactory factory = QuickFixFactory.getInstance();
        factory.registerPullAsAbstractUpFixes(method, registrar);
        for (IntentionAction action : registrar) {
          builder.registerFix(action, null, null, null, null);
        }
        builder.registerFix(factory.createDeleteFix(overrideAnnotation), null, null, null, null);
        return builder;
      }
      PsiClass superClass = superMethod.getMethod().getContainingClass();
      if (superClass != null && superClass.isInterface()) {
        return HighlightUtil.checkFeature(overrideAnnotation, JavaFeature.OVERRIDE_INTERFACE, languageLevel,
                                   overrideAnnotation.getContainingFile());
      }
      return null;
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }

  static HighlightInfo.Builder checkSafeVarargsAnnotation(@NotNull PsiMethod method, @NotNull LanguageLevel languageLevel) {
    PsiModifierList list = method.getModifierList();
    PsiAnnotation safeVarargsAnnotation = list.findAnnotation(CommonClassNames.JAVA_LANG_SAFE_VARARGS);
    if (safeVarargsAnnotation == null) {
      return null;
    }
    try {
      if (!method.isVarArgs()) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(safeVarargsAnnotation).descriptionAndTooltip(
          JavaErrorBundle.message("safevarargs.not.allowed.on.methods.with.fixed.arity"));
      }
      if (!isSafeVarargsNoOverridingCondition(method, languageLevel)) {
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(safeVarargsAnnotation).descriptionAndTooltip(
            JavaErrorBundle.message("safevarargs.not.allowed.non.final.instance.methods"));
        IntentionAction action = QuickFixFactory.getInstance().createModifierListFix(method, PsiModifier.FINAL, true, true);
        info.registerFix(action, null, null, null, null);
        return info;
      }

      PsiParameterList parameterList = method.getParameterList();
      PsiParameter varParameter = Objects.requireNonNull(parameterList.getParameter(parameterList.getParametersCount() - 1));

      for (PsiReferenceExpression element : VariableAccessUtils.getVariableReferences(varParameter, method.getBody())) {
        if (!PsiUtil.isAccessedForReading(element)) {
          return HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(element).descriptionAndTooltip(
            JavaErrorBundle.message("safevarargs.not.suppress.potentially.unsafe.operations"));
        }
      }


      LOG.assertTrue(varParameter.isVarArgs());
      PsiEllipsisType ellipsisType = (PsiEllipsisType)varParameter.getType();
      PsiType componentType = ellipsisType.getComponentType();
      if (JavaGenericsUtil.isReifiableType(componentType)) {
        PsiElement element = varParameter.getTypeElement();
        return HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING).range(element).descriptionAndTooltip(
          JavaErrorBundle.message("safevarargs.not.applicable.for.reifiable.types"));
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

  static void checkEnumConstantForConstructorProblems(@NotNull Project project,
                                                      @NotNull PsiEnumConstant enumConstant,
                                                      @NotNull JavaSdkVersion javaSdkVersion, @NotNull Consumer<? super HighlightInfo.Builder> errorSink) {
    PsiClass containingClass = enumConstant.getContainingClass();
    LOG.assertTrue(containingClass != null);
    if (enumConstant.getInitializingClass() == null) {
      HighlightInfo.Builder highlightInfo = HighlightClassUtil.checkInstantiationOfAbstractClass(containingClass, enumConstant.getNameIdentifier());
      if (highlightInfo != null) {
        IntentionAction action = QuickFixFactory.getInstance().createImplementMethodsFix(enumConstant);
        highlightInfo.registerFix(action, null, null, null, null);
        errorSink.accept(highlightInfo);
        return;
      }
      highlightInfo = HighlightClassUtil.checkClassWithAbstractMethods(enumConstant.getContainingClass(), enumConstant, enumConstant.getNameIdentifier().getTextRange());
      if (highlightInfo != null) {
        errorSink.accept(highlightInfo);
        return;
      }
    }
    PsiClassType type = JavaPsiFacade.getElementFactory(project).createType(containingClass);

    HighlightMethodUtil.checkConstructorCall(project, type.resolveGenerics(), enumConstant, type, null, javaSdkVersion,
                                             enumConstant.getArgumentList(), errorSink);
  }

  static HighlightInfo.Builder checkEnumSuperConstructorCall(@NotNull PsiMethodCallExpression expr) {
    PsiReferenceExpression methodExpression = expr.getMethodExpression();
    PsiElement refNameElement = methodExpression.getReferenceNameElement();
    if (refNameElement != null && PsiKeyword.SUPER.equals(refNameElement.getText())) {
      PsiMember constructor = PsiUtil.findEnclosingConstructorOrInitializer(expr);
      if (constructor instanceof PsiMethod) {
        PsiClass aClass = constructor.getContainingClass();
        if (aClass != null && aClass.isEnum()) {
          String message = JavaErrorBundle.message("call.to.super.is.not.allowed.in.enum.constructor");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expr).descriptionAndTooltip(message);
        }
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkVarArgParameterIsLast(@NotNull PsiParameter parameter) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (declarationScope instanceof PsiMethod psiMethod) {
      PsiParameter[] params = psiMethod.getParameterList().getParameters();
      if (params[params.length - 1] != parameter) {
        String description = JavaErrorBundle.message("vararg.not.last.parameter");
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameter).descriptionAndTooltip(description);
        IntentionAction action = QuickFixFactory.getInstance().createMakeVarargParameterLastFix(parameter);
        info.registerFix(action, null, null, null, null);
        return info;
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkParametersAllowed(@NotNull PsiReferenceParameterList refParamList) {
    PsiElement parent = refParamList.getParent();
    if (parent instanceof PsiReferenceExpression) {
      PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression) && !(parent instanceof PsiMethodReferenceExpression)) {
        String message = JavaErrorBundle.message("generics.reference.parameters.not.allowed");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refParamList).descriptionAndTooltip(message);
      }
    }

    return null;
  }

  static HighlightInfo.Builder checkParametersOnRaw(@NotNull PsiReferenceParameterList refParamList, LanguageLevel languageLevel) {
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
      if (!(element instanceof PsiTypeParameterListOwner owner)) return null;
      if (owner.hasModifierProperty(PsiModifier.STATIC)) return null;
      if (qualifier instanceof PsiJavaCodeReferenceElement referenceElement && referenceElement.resolve() instanceof PsiTypeParameter) return null;
      PsiClass containingClass = owner.getContainingClass();
      if (containingClass != null && PsiUtil.isRawSubstitutor(containingClass, resolveResult.getSubstitutor())) {
        if (element instanceof PsiMethod psiMethod) {
          if (languageLevel.isAtLeast(LanguageLevel.JDK_1_7)) return null;
          if (psiMethod.findSuperMethods().length > 0) return null;
          if (qualifier instanceof PsiReferenceExpression expression) {
            PsiType type = expression.getType();
            boolean isJavac7 = JavaVersionService.getInstance().isAtLeast(containingClass, JavaSdkVersion.JDK_1_7);
            if (type instanceof PsiClassType psiClassType && isJavac7 && psiClassType.isRaw()) return null;
            PsiClass typeParameter = PsiUtil.resolveClassInType(type);
            if (typeParameter instanceof PsiTypeParameter) {
              if (isJavac7) return null;
              for (PsiClassType classType : typeParameter.getExtendsListTypes()) {
                PsiClass resolve = classType.resolve();
                if (resolve != null) {
                  PsiMethod[] superMethods = resolve.findMethodsBySignature(psiMethod, true);
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
        String message = element instanceof PsiClass
                               ? JavaErrorBundle.message("generics.type.arguments.on.raw.type")
                               : JavaErrorBundle.message("generics.type.arguments.on.raw.method");

        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refParamList).descriptionAndTooltip(message);
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkGenericCannotExtendException(@NotNull PsiReferenceList list) {
    PsiElement parent = list.getParent();
    if (parent instanceof PsiClass klass) {
      if (hasGenericSignature(klass) && klass.getExtendsList() == list) {
        PsiClass throwableClass = null;
        for (PsiJavaCodeReferenceElement refElement : list.getReferenceElements()) {
          PsiElement resolved = refElement.resolve();
          if (!(resolved instanceof PsiClass psiClass)) continue;
          if (throwableClass == null) {
            throwableClass =
              JavaPsiFacade.getInstance(klass.getProject()).findClass(CommonClassNames.JAVA_LANG_THROWABLE, klass.getResolveScope());
          }
          if (InheritanceUtil.isInheritorOrSelf(psiClass, throwableClass, true)) {
            String message = JavaErrorBundle.message("generic.extend.exception");
            HighlightInfo.Builder info =
              HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refElement).descriptionAndTooltip(message);
            PsiClassType classType = JavaPsiFacade.getElementFactory(klass.getProject()).createType(psiClass);
            IntentionAction action = QuickFixFactory.getInstance().createExtendsListFix(klass, classType, false);
            info.registerFix(action, null, null, null, null);
            return info;
          }
        }
      }
    }
    else if (parent instanceof PsiMethod method && method.getThrowsList() == list) {
      for (PsiJavaCodeReferenceElement refElement : list.getReferenceElements()) {
        PsiReferenceParameterList parameterList = refElement.getParameterList();
        if (parameterList != null && parameterList.getTypeParameterElements().length != 0) {
          String message = JavaErrorBundle.message("generic.extend.exception");
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(refElement).descriptionAndTooltip(message);
        }
      }
    }

    return null;
  }

  static HighlightInfo.Builder checkGenericCannotExtendException(@NotNull PsiAnonymousClass anonymousClass) {
    if (hasGenericSignature(anonymousClass) &&
        InheritanceUtil.isInheritor(anonymousClass, true, CommonClassNames.JAVA_LANG_THROWABLE)) {
      String message = JavaErrorBundle.message("generic.extend.exception");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(anonymousClass.getBaseClassReference()).descriptionAndTooltip(message);
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

  static HighlightInfo.Builder checkSelectStaticClassFromParameterizedType(@Nullable PsiElement resolved, @NotNull PsiJavaCodeReferenceElement ref) {
    if (resolved instanceof PsiClass psiClass && psiClass.hasModifierProperty(PsiModifier.STATIC)) {
      PsiElement qualifier = ref.getQualifier();
      if (qualifier instanceof PsiJavaCodeReferenceElement referenceElement) {
        PsiReferenceParameterList parameterList = referenceElement.getParameterList();
        if (parameterList != null && parameterList.getTypeArguments().length > 0) {
          String message = JavaErrorBundle.message("generics.select.static.class.from.parameterized.type",
                                                   HighlightUtil.formatClass(psiClass));
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(parameterList)
            .descriptionAndTooltip(message)
            .registerFix(QuickFixFactory.getInstance().createDeleteFix(parameterList), null, null, null, null);
        }
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkCannotInheritFromTypeParameter(@Nullable PsiClass superClass, @NotNull PsiJavaCodeReferenceElement toHighlight) {
    if (superClass instanceof PsiTypeParameter) {
      String description = JavaErrorBundle.message("class.cannot.inherit.from.its.type.parameter");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(toHighlight).descriptionAndTooltip(description);
    }
    return null;
  }

  /**
   * see <a href="http://docs.oracle.com/javase/specs/jls/se7/html/jls-4.html#jls-4.8">JLS 4.8 on raw types</a>
   */
  static HighlightInfo.Builder checkRawOnParameterizedType(@NotNull PsiJavaCodeReferenceElement parent, @Nullable PsiElement resolved) {
    PsiReferenceParameterList list = parent.getParameterList();
    if (list == null || list.getTypeArguments().length > 0) return null;
    if (parent.getQualifier() instanceof PsiJavaCodeReferenceElement ref &&
        ref.getTypeParameters().length > 0 &&
        resolved instanceof PsiTypeParameterListOwner typeParameterListOwner &&
        typeParameterListOwner.hasTypeParameters() &&
        !typeParameterListOwner.hasModifierProperty(PsiModifier.STATIC)) {
      PsiElement referenceNameElement = parent.getReferenceNameElement();
      if (referenceNameElement != null) {
        String message = JavaErrorBundle.message("text.improper.formed.type", referenceNameElement.getText());
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parent).descriptionAndTooltip(message);
      }
    }
    return null;
  }

  private static void registerVariableParameterizedTypeFixes(@Nullable HighlightInfo.Builder builder,
                                                             @NotNull PsiVariable variable,
                                                             @NotNull PsiReferenceParameterList parameterList,
                                                             @NotNull JavaSdkVersion version) {
    PsiType type = variable.getType();
    if (!(type instanceof PsiClassType classType) || builder == null) return;

    if (DumbService.getInstance(variable.getProject()).isDumb()) return;

    String shortName = classType.getClassName();
    PsiManager manager = parameterList.getManager();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    PsiShortNamesCache shortNamesCache = PsiShortNamesCache.getInstance(parameterList.getProject());
    PsiClass[] classes = shortNamesCache.getClassesByName(shortName, GlobalSearchScope.allScope(manager.getProject()));
    PsiElementFactory factory = facade.getElementFactory();
    for (PsiClass aClass : classes) {
      if (aClass == null) {
        LOG.error("null class returned for " + shortName);
        continue;
      }
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
        HighlightFixUtil.registerChangeVariableTypeFixes(variable, suggestedType, variable.getInitializer(), builder);
      }
    }
  }

  static HighlightInfo.Builder checkInferredIntersections(@NotNull PsiSubstitutor substitutor, @NotNull PsiMethodCallExpression call) {
    for (Map.Entry<PsiTypeParameter, PsiType> typeEntry : substitutor.getSubstitutionMap().entrySet()) {
      String parameterName = typeEntry.getKey().getName();
      PsiType type = typeEntry.getValue();
      if (type instanceof PsiIntersectionType intersectionType) {
        String conflictingConjunctsMessage = intersectionType.getConflictingConjunctsMessage();
        if (conflictingConjunctsMessage != null) {
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .descriptionAndTooltip(
              JavaErrorBundle.message("type.parameter.has.incompatible.upper.bounds", parameterName, conflictingConjunctsMessage))
            .range(HighlightMethodUtil.getFixRange(call));
        }
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkClassSupersAccessibility(@NotNull PsiClass aClass) {
    HighlightInfo.Builder builder = checkClassSupersAccessibility(aClass, aClass.getResolveScope(), true);
    return builder == null ? null : builder.range(HighlightNamesUtil.getClassDeclarationTextRange(aClass));
  }

  static HighlightInfo.Builder checkClassSupersAccessibility(@NotNull PsiClass aClass, @NotNull PsiElement ref, @NotNull GlobalSearchScope scope) {
    HighlightInfo.Builder builder = checkClassSupersAccessibility(aClass, scope, false);
    return builder == null ? null : builder.range(ref.getTextRange());
  }

  private static HighlightInfo.Builder checkClassSupersAccessibility(@NotNull PsiClass aClass,
                                                                     @NotNull GlobalSearchScope resolveScope,
                                                                     boolean checkParameters) {
    JavaPsiFacade factory = JavaPsiFacade.getInstance(aClass.getProject());
    for (PsiClassType superType : aClass.getSuperTypes()) {
      HashSet<PsiClass> checked = new HashSet<>();
      checked.add(aClass);
      String notAccessibleErrorMessage = isTypeAccessible(superType, checked, checkParameters, true, resolveScope, factory);
      if (notAccessibleErrorMessage != null) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .descriptionAndTooltip(notAccessibleErrorMessage);
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkMemberSignatureTypesAccessibility(@NotNull PsiReferenceExpression ref) {
    String message = null;

    PsiElement parent = ref.getParent();
    if (parent instanceof PsiMethodCallExpression expression) {
      JavaResolveResult resolveResult = expression.resolveMethodGenerics();
      PsiMethod method = (PsiMethod)resolveResult.getElement();
      if (method != null) {
        Set<PsiClass> classes = new HashSet<>();
        JavaPsiFacade facade = JavaPsiFacade.getInstance(ref.getProject());
        PsiSubstitutor substitutor = resolveResult.getSubstitutor();
        GlobalSearchScope resolveScope = ref.getResolveScope();

        message = isTypeAccessible(substitutor.substitute(method.getReturnType()), classes, false, true, resolveScope, facade);
        if (message == null) {
          for (PsiType type : method.getSignature(substitutor).getParameterTypes()) {
            message = isTypeAccessible(type, classes, false, true, resolveScope, facade);
            if (message != null) {
              break;
            }
          }
        }
      }
    }
    else {
      PsiElement resolve = ref.resolve();
      if (resolve instanceof PsiField psiField) {
        GlobalSearchScope resolveScope = ref.getResolveScope();
        JavaPsiFacade facade = JavaPsiFacade.getInstance(ref.getProject());
        message = isTypeAccessible(psiField.getType(), new HashSet<>(), false, true, resolveScope, facade);
      }
    }

    if (message != null) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .descriptionAndTooltip(message)
        .range(ref)
        ;
    }

    return null;
  }

  @Nullable
  private static @NlsContexts.DetailedDescription String isTypeAccessible(@Nullable PsiType type,
                                                                          @NotNull Set<? super PsiClass> classes,
                                                                          boolean checkParameters, 
                                                                          boolean checkSuperTypes,
                                                                          @NotNull GlobalSearchScope resolveScope,
                                                                          @NotNull JavaPsiFacade factory) {
    type = PsiClassImplUtil.correctType(type, resolveScope);

    PsiClass aClass = PsiUtil.resolveClassInType(type);
    if (aClass != null && classes.add(aClass)) {
      VirtualFile vFile = PsiUtilCore.getVirtualFile(aClass);
      if (vFile == null) {
        return null;
      }
      FileIndexFacade index = FileIndexFacade.getInstance(aClass.getProject());
      if (!index.isInSource(vFile) && !index.isInLibraryClasses(vFile)) {
        return null;
      }

      PsiImplicitClass parentImplicitClass = PsiTreeUtil.getParentOfType(aClass, PsiImplicitClass.class);
      String qualifiedName = aClass.getQualifiedName();
      if (parentImplicitClass == null && qualifiedName != null && factory.findClass(qualifiedName, resolveScope) == null) {
        return JavaErrorBundle.message("text.class.cannot.access", HighlightUtil.formatClass(aClass));
      }

      if (!checkParameters){
        return null;
      }

      if (type instanceof PsiClassType classType) {
        for (PsiType parameterType : classType.getParameters()) {
          String notAccessibleMessage = isTypeAccessible(parameterType, classes, true, false, resolveScope, factory);
          if (notAccessibleMessage != null) {
            return notAccessibleMessage;
          }
        }
      }

      if (!checkSuperTypes) {
        return null;
      }

      boolean isInLibrary = !index.isInContent(vFile);
      for (PsiClassType superType : aClass.getSuperTypes()) {
        String notAccessibleMessage = isTypeAccessible(superType, classes, !isInLibrary, true, resolveScope, factory);
        if (notAccessibleMessage != null) {
          return notAccessibleMessage;
        }
      }
    }

    return null;
  }

  static void checkTypeParameterOverrideEquivalentMethods(@NotNull PsiClass typeParameter, @NotNull LanguageLevel level,
                                                          @NotNull Consumer<? super HighlightInfo.Builder> errorSink,
                                                          @NotNull Set<? super PsiClass> overrideEquivalentMethodsVisitedClasses,
                                                          @NotNull Map<PsiMember, HighlightInfo.Builder> overrideEquivalentMethodsErrors) {
    if (typeParameter instanceof PsiTypeParameter && level.isAtLeast(LanguageLevel.JDK_1_7)) {
      PsiReferenceList extendsList = typeParameter.getExtendsList();
      if (extendsList.getReferenceElements().length > 1) {
        //todo suppress erased methods which come from the same class
        computeOverrideEquivalentMethodErrors(typeParameter, overrideEquivalentMethodsVisitedClasses, overrideEquivalentMethodsErrors);
        errorSink.accept(overrideEquivalentMethodsErrors.get(typeParameter));
      }
    }
  }
}
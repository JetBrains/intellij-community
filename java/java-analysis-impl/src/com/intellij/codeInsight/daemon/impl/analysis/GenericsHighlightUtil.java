// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class GenericsHighlightUtil {
  private static final Logger LOG = Logger.getInstance(GenericsHighlightUtil.class);

  private GenericsHighlightUtil() { }

  static boolean hasReferenceTypeProblem(@NotNull PsiTypeParameterListOwner typeParameterListOwner,
                                         @Nullable PsiReferenceParameterList referenceParameterList,
                                         @NotNull JavaSdkVersion javaSdkVersion) {
    PsiDiamondType.DiamondInferenceResult inferenceResult = null;
    PsiTypeElement[] referenceElements = null;
    if (referenceParameterList != null) {
      referenceElements = referenceParameterList.getTypeParameterElements();
      if (referenceElements.length == 1 && referenceElements[0].getType() instanceof PsiDiamondType) {
        if (!typeParameterListOwner.hasTypeParameters()) return true;
        inferenceResult = ((PsiDiamondType)referenceElements[0].getType()).resolveInferredTypes();
        if (inferenceResult.getErrorMessage() != null &&
            (inferenceResult == PsiDiamondType.DiamondInferenceResult.ANONYMOUS_INNER_RESULT ||
             inferenceResult == PsiDiamondType.DiamondInferenceResult.EXPLICIT_CONSTRUCTOR_TYPE_ARGS) &&
            !(inferenceResult.failedToInfer() &&
              detectExpectedType(referenceParameterList) instanceof PsiClassType classType &&
              classType.isRaw())) {
          return true;
        }

        PsiElement parent = referenceParameterList.getParent().getParent();
        if (parent instanceof PsiAnonymousClass anonymousClass &&
            ContainerUtil.exists(anonymousClass.getMethods(),
                                 method -> !method.hasModifierProperty(PsiModifier.PRIVATE) && method.findSuperMethods().length == 0)) {
          return true;
        }
      }
    }

    PsiTypeParameter[] typeParameters = typeParameterListOwner.getTypeParameters();
    int targetParametersNum = typeParameters.length;
    int refParametersNum = referenceParameterList == null ? 0 : referenceParameterList.getTypeArguments().length;
    if (targetParametersNum != refParametersNum && refParametersNum != 0) {
      if (targetParametersNum != 0 || PsiTreeUtil.getParentOfType(referenceParameterList, PsiCall.class) == null ||
          !(typeParameterListOwner instanceof PsiMethod psiMethod) ||
          (!javaSdkVersion.isAtLeast(JavaSdkVersion.JDK_1_7) &&
           !ContainerUtil.exists(psiMethod.findDeepestSuperMethods(), PsiTypeParameterListOwner::hasTypeParameters))) {
        return true;
      }
    }

    // bounds check
    if (targetParametersNum > 0 && refParametersNum != 0) {
      PsiType[] types = inferenceResult != null ? inferenceResult.getTypes() : null;
      for (int i = 0; i < typeParameters.length; i++) {
        PsiType type = types != null ? types[i] : referenceElements[i].getType();
        if (ContainerUtil.exists(
          typeParameters[i].getSuperTypes(),
          bound -> !bound.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) &&
                   GenericsUtil.checkNotInBounds(type, bound, referenceParameterList))) {
          return true;
        }
      }
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

  /**
   * @param skipMethodInSelf pass false to check if method in {@code aClass} can be deleted
   *
   * @return error message if class inherits 2 unrelated default methods or abstract and default methods which do not belong to one hierarchy
   */
  public static @Nullable @NlsContexts.DetailedDescription String getUnrelatedDefaultsMessage(@NotNull PsiClass aClass,
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

  private static @Nullable @NlsContexts.DetailedDescription String isTypeAccessible(@Nullable PsiType type,
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
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class GenericsHighlightUtil {

  private GenericsHighlightUtil() { }

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
}
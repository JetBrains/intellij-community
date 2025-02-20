// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.java.codeserver.core.JavaPsiMethodUtil;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiSuperMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public final class GenericsHighlightUtil {

  private GenericsHighlightUtil() { }

  static HighlightInfo.Builder checkUnrelatedDefaultMethods(@NotNull PsiClass aClass, @NotNull PsiIdentifier classIdentifier) {
    Map<? extends MethodSignature, Set<PsiMethod>> overrideEquivalent = PsiSuperMethodUtil.collectOverrideEquivalents(aClass);

    for (Set<PsiMethod> overrideEquivalentMethods : overrideEquivalent.values()) {
      String errorMessage = getUnrelatedDefaultsMessage(aClass, overrideEquivalentMethods);
      if (errorMessage != null &&
          MethodSignatureUtil.findMethodBySuperMethod(aClass, overrideEquivalentMethods.iterator().next(), false) == null) {
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

  /**
   * @return error message if class inherits 2 unrelated default methods or abstract and default methods which do not belong to one hierarchy
   */
  public static @Nullable @NlsContexts.DetailedDescription String getUnrelatedDefaultsMessage(
    @NotNull PsiClass aClass, @NotNull Collection<? extends PsiMethod> overrideEquivalentSuperMethods) {
    PsiMethod abstractMethod = JavaPsiMethodUtil.getAbstractMethodToImplementWhenDefaultPresent(aClass, overrideEquivalentSuperMethods);
    if (abstractMethod != null) {
      String key = aClass instanceof PsiEnumConstantInitializer || aClass.isRecord() || aClass.isEnum() ?
                   "class.must.implement.method" : "class.must.be.abstract";
      return JavaErrorBundle.message(key,
                                     HighlightUtil.formatClass(aClass, false),
                                     JavaHighlightUtil.formatMethod(abstractMethod),
                                     HighlightUtil.formatClass(requireNonNull(abstractMethod.getContainingClass()), false));
    }

    Couple<@NotNull PsiMethod> pair = JavaPsiMethodUtil.getUnrelatedSuperMethods(aClass, overrideEquivalentSuperMethods);
    if (pair == null) return null;
    String key = pair.getSecond().hasModifierProperty(PsiModifier.ABSTRACT) ?
                 "text.class.inherits.abstract.and.default" :
                 "text.class.inherits.unrelated.defaults";
    return JavaErrorBundle.message(key, HighlightUtil.formatClass(aClass),
                                   JavaHighlightUtil.formatMethod(pair.getFirst()),
                                   HighlightUtil.formatClass(requireNonNull(pair.getFirst().getContainingClass())),
                                   HighlightUtil.formatClass(requireNonNull(pair.getSecond().getContainingClass())));
  }
}
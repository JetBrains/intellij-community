// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiKeyword;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import static com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil.registerIncreaseLanguageLevelFixes;

public class SealedClassUnresolvedReferenceFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
  @Override
  public void registerFixes(@NotNull PsiJavaCodeReferenceElement ref, @NotNull QuickFixActionRegistrar registrar) {
    if (!HighlightingFeature.SEALED_CLASSES.isAvailable(ref) && ref.textMatches(PsiKeyword.SEALED)) {
      ArrayList<IntentionAction> intentions = new ArrayList<>();
      registerIncreaseLanguageLevelFixes(ref, HighlightingFeature.SEALED_CLASSES, intentions);
      for (IntentionAction intention : intentions) {
        registrar.register(intention);
      }
    }
  }

  @Override
  public @NotNull Class<PsiJavaCodeReferenceElement> getReferenceClass() {
    return PsiJavaCodeReferenceElement.class;
  }
}

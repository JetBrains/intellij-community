// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.codeInspection.ConvertRecordToClassFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

import static com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil.registerIncreaseLanguageLevelFixes;

public final class SealedClassUnresolvedReferenceFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
  @Override
  public void registerFixes(@NotNull PsiJavaCodeReferenceElement ref, @NotNull QuickFixActionRegistrar registrar) {
    if (!HighlightingFeature.SEALED_CLASSES.isAvailable(ref) && ref.textMatches(PsiKeyword.SEALED)) {
      ArrayList<IntentionAction> intentions = new ArrayList<>();
      registerIncreaseLanguageLevelFixes(ref, HighlightingFeature.SEALED_CLASSES, intentions);
      for (IntentionAction intention : intentions) {
        registrar.register(intention);
      }
    }
    if (ref.textMatches(PsiKeyword.RECORD)) {
      PsiElement parent = ref.getParent();
      if (parent != null) {
        if (parent.getParent() instanceof PsiMethod m) {
          if (ConvertRecordToClassFix.tryMakeRecord(m) != null) {
            registrar.register(m.getTextRange(), new ConvertRecordToClassFix(ref).asIntention(), null);
          }
        }
      }
    }
  }

  @Override
  public @NotNull Class<PsiJavaCodeReferenceElement> getReferenceClass() {
    return PsiJavaCodeReferenceElement.class;
  }
}

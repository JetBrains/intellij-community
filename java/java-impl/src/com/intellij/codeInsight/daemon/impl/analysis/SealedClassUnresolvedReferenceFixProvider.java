// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.codeInspection.ConvertRecordToClassFix;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public final class SealedClassUnresolvedReferenceFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
  @Override
  public void registerFixes(@NotNull PsiJavaCodeReferenceElement ref, @NotNull QuickFixActionRegistrar registrar) {
    if (!PsiUtil.isAvailable(JavaFeature.SEALED_CLASSES, ref) && ref.textMatches(JavaKeywords.SEALED)) {
      for (CommonIntentionAction intention : HighlightFixUtil.getIncreaseLanguageLevelFixes(ref, JavaFeature.SEALED_CLASSES)) {
        registrar.register(intention.asIntention());
      }
    }
    if (ref.textMatches(JavaKeywords.RECORD)) {
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

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.pom.java.AcceptedLanguageLevelsSettings;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public class LocalVariableTypeInferenceUnresolvedFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
  @Override
  public void registerFixes(@NotNull PsiJavaCodeReferenceElement ref, @NotNull QuickFixActionRegistrar registrar) {
    PsiElement typeElement = ref.getParent();
    PsiElement parent = typeElement instanceof PsiTypeElement ? typeElement.getParent() : null;
    boolean increaseLanguageLevel = true;
    LanguageLevel targetLanguageLevel;
    if (parent instanceof PsiParameter && ((PsiParameter)parent).getDeclarationScope() instanceof PsiLambdaExpression) {
      //early-draft specification support to be enabled after release
      if (LanguageLevel.HIGHEST.isAtLeast(LanguageLevel.JDK_11)) {
        targetLanguageLevel = LanguageLevel.JDK_11;
      }
      else if (AcceptedLanguageLevelsSettings.isLanguageLevelAccepted(LanguageLevel.JDK_11)) {
        targetLanguageLevel = LanguageLevel.JDK_11;

        //show module options with ability to explicitly agree with legal notice
        increaseLanguageLevel = false;
      }
      else {
        return;
      }
    }
    else {
      targetLanguageLevel = LanguageLevel.JDK_10;
    }

    if (PsiUtil.getLanguageLevel(ref).isAtLeast(targetLanguageLevel)) return;
    if (!PsiKeyword.VAR.equals(ref.getReferenceName())) return;

    if (increaseLanguageLevel) {
      registrar.register(QuickFixFactory.getInstance().createIncreaseLanguageLevelFix(targetLanguageLevel));
    }
    registrar.register(QuickFixFactory.getInstance().createShowModulePropertiesFix(ref));
  }

  @NotNull
  @Override
  public Class<PsiJavaCodeReferenceElement> getReferenceClass() {
    return PsiJavaCodeReferenceElement.class;
  }
}

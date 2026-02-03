// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.featuresTrainer.onboarding.tips

import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import training.onboarding.AbstractOnboardingTipsDocumentationProvider

class JavaOnboardingTipsDocumentationProvider: AbstractOnboardingTipsDocumentationProvider(JavaTokenType.END_OF_LINE_COMMENT) {
  override fun isLanguageFile(file: PsiFile): Boolean = file is PsiJavaFile
}
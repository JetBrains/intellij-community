// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.injection.general

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement

/**
 * Checks additional preconditions on IntelliLang injection configured via config XML.
 * Implement it when you need to insert a chameleon injection that uses different language depending on additional arguments or parameters.
 */
interface LanguageInjectionCondition {
  /**
   * @return ID that should be set to injections condition-id attribute
   */
  fun getId(): String

  /**
   * @return false to prevent injection to context
   */
  fun isApplicable(injection: Injection, context: PsiElement): Boolean

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<LanguageInjectionCondition> = ExtensionPointName.create("com.intellij.languageInjectionCondition")
  }
}
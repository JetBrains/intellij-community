// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

@file:JvmName("QuickEditHandlerUtils")

package com.intellij.codeInsight.intention.impl

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiLanguageInjectionHost


private val STORED_TRIM_LENGTH = Key.create<Int>("STORED_TRIM_LENGTH")

/**
 * Is needed to not change the indent of injected code if fragment editor is open to avoid conflict between host file and fragment
 * see KTIJ-3019 for instance
 */
fun reuseFragmentEditorIndent(literal: PsiLanguageInjectionHost, evaluatedIndent: () -> Int?): Int? {
  val storedTrimLength = STORED_TRIM_LENGTH.get(literal)
  // Dont change the indent if Fragment Editor is open
  if (QuickEditHandler.getFragmentEditors(literal).isNotEmpty() && storedTrimLength != null)
    return storedTrimLength
  
  return evaluatedIndent().also { STORED_TRIM_LENGTH[literal] = it }
}
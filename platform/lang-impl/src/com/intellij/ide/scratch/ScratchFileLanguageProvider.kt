/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.scratch

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension

abstract class ScratchFileLanguageProvider {
  abstract fun getDefaultExtension(): String?

  companion object {
    private val EXTENSION = LanguageExtension<ScratchFileLanguageProvider>("com.intellij.scratchFileLanguageProvider")

    fun getDefaultFileExtension(language: Language): String {
      return ScratchFileLanguageProvider.EXTENSION
               .forLanguage(language)
               .getDefaultExtension()
               ?: language.associatedFileType?.defaultExtension
               ?: ""
    }
  }
}
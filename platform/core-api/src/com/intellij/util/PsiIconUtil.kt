// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.ide.IconProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.Companion.isDumbAware
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.Iconable.IconFlags
import com.intellij.psi.PsiElement
import javax.swing.Icon

object PsiIconUtil {
  @JvmStatic
  @Suppress("IdentifierGrammar")
  fun getProvidersIcon(element: PsiElement, @IconFlags flags: Int): Icon? {
    val isDumb = DumbService.getInstance(element.getProject()).isDumb
    for (provider in IconProvider.EXTENSION_POINT_NAME.getIterable()) {
      if (provider == null || (isDumb && !isDumbAware(provider))) {
        continue
      }
      try {
        provider.getIcon(element, flags)?.let {
          return it
        }
      }
      catch (_: IndexNotReadyException) {

      }
    }
    return null
  }
}
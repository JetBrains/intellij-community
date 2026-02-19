// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.ide.IconProvider
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.Iconable.IconFlags
import com.intellij.psi.PsiElement
import javax.swing.Icon

object PsiIconUtil {

  @JvmStatic
  fun getIconFromProviders(element: PsiElement, @IconFlags flags: Int): Icon? {
    for (provider in IconProvider.EXTENSION_POINT_NAME.extensionList) {
      val icon = kotlin.runCatching {
        provider.getIcon(element, flags)
      }.getOrHandleException {
        if (it !is IndexNotReadyException) {
          LOG.warn("IconProvider $provider threw an exception", it)
        }
      }
      if (icon != null) {
        return icon
      }
    }
    return null
  }

  @JvmStatic
  @Suppress("IdentifierGrammar")
  @Deprecated("Use `getIconFromProviders` instead", ReplaceWith("getIconFromProviders(element, flags)"))
  fun getProvidersIcon(element: PsiElement, @IconFlags flags: Int): Icon? {
    return getIconFromProviders(element, flags)
  }
}

private val LOG = logger<PsiIconUtil>()

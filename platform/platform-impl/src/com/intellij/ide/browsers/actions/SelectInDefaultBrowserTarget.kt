// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers.actions

import com.intellij.ide.IdeBundle
import com.intellij.ide.SelectInContext
import com.intellij.ide.SelectInTarget
import com.intellij.ide.StandardTargetWeights
import com.intellij.ide.browsers.OpenInBrowserRequest
import com.intellij.ide.browsers.createOpenInBrowserRequest
import com.intellij.psi.PsiElement

internal class SelectInDefaultBrowserTarget : SelectInTarget {
  override fun canSelect(context: SelectInContext): Boolean {
    return doCreateOpenRequest(context)?.isPhysicalFile() ?: return false
  }

  override fun toString() = IdeBundle.message("browser.select.in.default.name")

  override fun selectIn(context: SelectInContext, requestFocus: Boolean) {
    doCreateOpenRequest(context)?.let { BaseOpenInBrowserAction.openInBrowser(it) }
  }

  override fun getWeight() = StandardTargetWeights.OS_FILE_MANAGER
}

private fun doCreateOpenRequest(context: SelectInContext): OpenInBrowserRequest? {
  val selectorInFile = context.selectorInFile as? PsiElement ?: return null
  return createOpenInBrowserRequest(selectorInFile, isForceFileUrlIfNoUrlProvider = true)
}
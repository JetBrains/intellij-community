// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers

import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class WebBrowserXmlService {
  companion object {
    @JvmStatic
    fun getInstance(): WebBrowserXmlService {
      return ApplicationManager.getApplication().getService(WebBrowserXmlService::class.java)
    }
  }

  open fun isHtmlFile(element: PsiElement) = false
  open fun isHtmlFile(file: VirtualFile) = false
  open fun isHtmlOrXmlFile(psiFile: PsiFile) = false
  open fun isXmlLanguage(language: Language) = false
  open fun isHtmlOrXmlLanguage(language: Language) = false
}
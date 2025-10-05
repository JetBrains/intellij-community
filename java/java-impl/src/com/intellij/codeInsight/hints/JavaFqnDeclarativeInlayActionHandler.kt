// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.InlayActionHandler
import com.intellij.codeInsight.hints.declarative.InlayActionPayload
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.AppExecutorUtil

public class JavaFqnDeclarativeInlayActionHandler : InlayActionHandler {
  public companion object {
    public const val HANDLER_NAME: String = "java.fqn.class"
  }

  override fun handleClick(editor: Editor, payload: InlayActionPayload) {
    val project = editor.project ?: return
    payload as StringInlayActionPayload
    val fqn = payload.text
    val facade = JavaPsiFacade.getInstance(project)
    AppExecutorUtil.getAppExecutorService().submit {
      runReadAction {
        val aClass = facade.findClass(fqn, GlobalSearchScope.allScope(project))
        if (aClass != null) {
          invokeLater(null) {
            aClass.navigate(true)
          }
        }
      }
    }
  }
}
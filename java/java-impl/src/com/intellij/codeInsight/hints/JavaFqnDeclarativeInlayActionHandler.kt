// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.InlayActionHandler
import com.intellij.codeInsight.hints.declarative.InlayActionPayload
import com.intellij.codeInsight.hints.declarative.StringInlayActionPayload
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

public class JavaFqnDeclarativeInlayActionHandler(private val cs: CoroutineScope) : InlayActionHandler {
  override fun handleClick(e: EditorMouseEvent, payload: InlayActionPayload) {
    val project = e.editor.project ?: return
    cs.launch {
      val aClass = readAction { findNavigationElement(project, payload) } ?: return@launch
      withContext(Dispatchers.EDT) {
        aClass.navigate(true)
      }
    }
  }

  public companion object {
    public const val HANDLER_NAME: String = "java.fqn.class"
  }
}

/**
 * Finds class to navigate to by [InlayActionPayload]
 */
public fun findNavigationElement(project: Project, payload: InlayActionPayload): PsiClass? {
  if (payload !is StringInlayActionPayload) return null
  val fqn = payload.text
  val facade = JavaPsiFacade.getInstance(project)
  return facade.findClass(fqn, GlobalSearchScope.allScope(project))
}
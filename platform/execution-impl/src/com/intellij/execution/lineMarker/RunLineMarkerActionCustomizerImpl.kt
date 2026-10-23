// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.lineMarker

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ExecutorGroupActionGroup
import com.intellij.execution.actions.RunContextAction
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.KeymapUtil.getShortcutText
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.JBUI
import com.intellij.xml.CommonXmlStrings
import com.intellij.xml.util.XmlStringUtil

internal class RunLineMarkerActionCustomizerImpl : RunLineMarkerActionCustomizer {
  override fun wrapChildren(origin: AnAction, element: SmartPsiElementPointer<PsiElement>): List<AnAction>? {
    if (origin is ExecutorAction) {
      if (origin.origin is ExecutorGroupActionGroup) {
        return origin.origin.children.map { action ->
          val wrappedAction = ExecutorAction.wrap(action, (action as RunContextAction).executor, origin.order)
          LineMarkerActionWrapper(element, wrappedAction)
        }
      }
    }
    return null
  }

  override fun customizeDataSnapshot(sink: DataSink, element: SmartPsiElementPointer<PsiElement>) {
    sink.lazy(Location.DATA_KEY) {
      val e = element.getElement()
      if (e != null && e.isValid()) PsiLocation(e) else null
    }
  }

  override fun appendShortcut(tooltip: String): String {
    if (ApplicationManager.getApplication().isUnitTestMode()) return tooltip

    val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID)
    if (executor != null) {
      val actionId = executor.getContextActionId()
      val shortcutText = getShortcutText(actionId)
      val shortcutColor = ColorUtil.toHex(JBUI.CurrentTheme.Tooltip.shortcutForeground())
      return XmlStringUtil.wrapInHtml(buildString {
        append(XmlStringUtil.escapeString(tooltip).replace("\n", "<br>"))
        append(CommonXmlStrings.NBSP)
        append(CommonXmlStrings.NBSP)
        append("<font color='#")
        append(shortcutColor)
        append("'>")
        append(XmlStringUtil.escapeString(shortcutText))
        append("</font>")
      })
    }
    else {
      return tooltip
    }
  }
}

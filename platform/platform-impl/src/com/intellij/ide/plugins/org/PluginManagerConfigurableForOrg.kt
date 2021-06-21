// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.org

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.lang.LangBundle
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.SwingConstants

/**
 * This is the common service to deal with organizational
 * restrictions in the UI for the plugin management.
 */
@Service(Service.Level.APP)
class PluginManagerConfigurableForOrg {
  companion object {
    @JvmStatic
    fun getInstance(): PluginManagerConfigurableForOrg = service()
  }

  fun isPluginAllowed(descriptor: IdeaPluginDescriptor) : Boolean {
    return descriptor.pluginId.idString.hashCode() % 2 == 0
  }


  fun foo() {
    BorderLayoutPanel().apply {
      val customLine = when {
        SystemInfo.isWindows -> JBUI.Borders.customLine(JBColor.border(), 1, 0, 1, 0)
        else -> JBUI.Borders.customLineBottom(JBColor.border())
      }
      border = JBUI.Borders.merge(JBUI.Borders.empty(10), customLine, true)
      background = JBUI.CurrentTheme.Notification.BACKGROUND
      foreground = JBUI.CurrentTheme.Notification.FOREGROUND

      addToCenter(JBLabel().apply {
        icon = AllIcons.General.Warning
        verticalTextPosition = SwingConstants.TOP
        text = HtmlChunk
          .html()
          .addText(LangBundle.message("dialog.label.choose.ide.runtime.warn", ApplicationInfo.getInstance().shortCompanyName))
          .toString()
      })

      withPreferredWidth(400)
    }

  }
}


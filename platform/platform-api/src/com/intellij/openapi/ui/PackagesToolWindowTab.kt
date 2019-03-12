// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.PackagesToolWindowTabFactory.Companion.EP_NAME
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.tabs.JBTabsFactory
import com.intellij.ui.tabs.TabInfo
import javax.swing.JComponent

interface PackagesToolWindowTabFactory {

  companion object {
    val EP_NAME = ExtensionPointName.create<PackagesToolWindowTabFactory>("com.intellij.packaging.packagesToolWindowTabFactory")
  }

  fun createContent(project: Project, parentDisposable: Disposable): PackagesToolWindowTab?
}

data class PackagesToolWindowTab(val name: String, val component: JComponent)

class PackagesToolWindowFactory : ToolWindowFactory, DumbAware {

  override fun shouldBeAvailable(project: Project): Boolean = EP_NAME.hasAnyExtensions()

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val contentManager = toolWindow.contentManager

    val tabsContent = EP_NAME.extensions.mapNotNull { it.createContent(project, contentManager) }
    if (tabsContent.isEmpty()) return

    contentManager.addContent(toToolWindowContent(project, tabsContent))
  }

  private fun toToolWindowContent(project: Project, tabsContent: List<PackagesToolWindowTab>): Content {
    val contentFactory = ContentFactory.SERVICE.getInstance()

    if (tabsContent.size == 1) {
      return tabsContent[0].let {
        contentFactory.createContent(it.component, it.name, false)
      }
    }
    else {
      val tabs = JBTabsFactory.createTabs(project)

      tabsContent.forEach { content ->
        tabs.addTab(TabInfo(content.component).apply { text = content.name })
      }

      return contentFactory.createContent(tabs.component, null, false)
    }
  }
}
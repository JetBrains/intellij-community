// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil.browse
import com.intellij.ide.IdeBundle.messagePointer
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.datatransfer.StringSelection
import javax.swing.Icon

class BrowserLink(icon: Icon?, text: @Nls String?, tooltip: @Nls String?, val url: @NonNls String) : ActionLink() {

  constructor(url: @NonNls String) : this(null, url, null, url) // NON-NLS

  constructor(text: @Nls String, url: @NonNls String) : this(AllIcons.Ide.External_link_arrow, text, null, url)

  constructor(icon: Icon, tooltip: @Nls String, url: @NonNls String) : this(icon, null, tooltip, url)

  init {
    addActionListener { browse(url) }
    icon?.let { setIcon(it, true) }
    text?.let { setText(it) }
    tooltip?.let { toolTipText = it }

    ActionManagerEx.withLazyActionManager(scope = null) {
      val group = DefaultActionGroup(OpenLinkInBrowser(url), CopyLinkAction(url))
      componentPopupMenu = it.createActionPopupMenu("popup@browser.link.context.menu", group).component
    }
  }
}

private class CopyLinkAction(val url: String)
  : DumbAwareAction(messagePointer("action.text.copy.link.address"), AllIcons.Actions.Copy) {

  override fun actionPerformed(event: AnActionEvent) {
    CopyPasteManager.getInstance().setContents(StringSelection(url))
  }
}

private class OpenLinkInBrowser(val url: String)
  : DumbAwareAction(messagePointer("action.text.open.link.in.browser"), AllIcons.Nodes.PpWeb) {

  override fun actionPerformed(event: AnActionEvent) {
    browse(url)
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.lsWidget

import com.intellij.icons.AllIcons
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import javax.swing.Icon

@Suppress("DEPRECATION")
@Deprecated(
  "Use LspClientWidgetItem",
  ReplaceWith("LspClientWidgetItem", "com.intellij.platform.lsp.api.lsWidget.LspClientWidgetItem"),
)
open class LspServerWidgetItem(
  lspServer: LspServer,
  currentFile: VirtualFile?,
  icon: Icon = AllIcons.Json.Object,
  settingsPageClass: Class<out Configurable>? = null,
) : LspClientWidgetItem(lspServer, currentFile, icon, settingsPageClass) {
  protected val lspServer: LspServer get() = lspClient as LspServer
}

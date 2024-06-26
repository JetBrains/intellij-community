// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.frontend.actions

import com.intellij.ide.CopyPasteDelegator
import com.intellij.ide.navigationToolbar.NavBarModelExtension
import com.intellij.openapi.actionSystem.*
import com.intellij.platform.navbar.NavBarVmItem
import javax.swing.JComponent

internal class NavBarUiDataRule : UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val project = snapshot[CommonDataKeys.PROJECT] ?: return
    val source = snapshot[PlatformCoreDataKeys.CONTEXT_COMPONENT] as? JComponent ?: return
    val selection = snapshot[NavBarVmItem.SELECTED_ITEMS] ?: return
    if (selection.isEmpty()) return

    val delegator = CopyPasteDelegator(project, source)
    sink[PlatformDataKeys.CUT_PROVIDER] = delegator.cutProvider
    sink[PlatformDataKeys.COPY_PROVIDER] = delegator.copyProvider
    sink[PlatformDataKeys.PASTE_PROVIDER] = delegator.pasteProvider

    NavBarModelExtension.EP_NAME.forEachExtensionSafe {
      it.uiDataSnapshot(sink, snapshot)
    }
  }
}

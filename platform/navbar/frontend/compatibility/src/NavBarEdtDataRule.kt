// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.frontend.compatibility

import com.intellij.ide.CopyPasteDelegator
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.EdtDataRule
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.platform.navbar.compatibility.extensionData
import javax.swing.JComponent

internal class NavBarEdtDataRule : EdtDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val project = snapshot[CommonDataKeys.PROJECT] ?: return
    val source = snapshot[PlatformCoreDataKeys.CONTEXT_COMPONENT] as? JComponent ?: return

    val delegator = CopyPasteDelegator(project, source)
    sink[PlatformDataKeys.CUT_PROVIDER] = delegator.cutProvider
    sink[PlatformDataKeys.COPY_PROVIDER] = delegator.copyProvider
    sink[PlatformDataKeys.PASTE_PROVIDER] = delegator.pasteProvider

    DataSink.uiDataSnapshot(sink) { dataId: String ->
      extensionData(dataId) { innerDataId ->
        snapshot[DataKey.create(innerDataId)]
      }
    }
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.frontend.compatibility

import com.intellij.ide.CopyPasteDelegator
import com.intellij.ide.CopyPasteSupport
import com.intellij.ide.impl.dataRules.GetDataRule
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.platform.navbar.compatibility.extensionData
import javax.swing.JComponent

internal class CutProviderDataRule : GetDataRule {
  override fun getData(dataProvider: DataProvider): Any? {
    return extensionData(PlatformDataKeys.CUT_PROVIDER.name)
           ?: getCopyPasteDelegator(dataProvider)?.cutProvider
  }
}

internal class CopyProviderDataRule : GetDataRule {
  override fun getData(dataProvider: DataProvider): Any? {
    return extensionData(PlatformDataKeys.COPY_PROVIDER.name)
           ?: getCopyPasteDelegator(dataProvider)?.copyProvider
  }
}

internal class PasteProviderDataRule : GetDataRule {
  override fun getData(dataProvider: DataProvider): Any? {
    return extensionData(PlatformDataKeys.PASTE_PROVIDER.name)
           ?: getCopyPasteDelegator(dataProvider)?.pasteProvider
  }
}

private fun getCopyPasteDelegator(dataProvider: DataProvider): CopyPasteSupport? {
  val project = CommonDataKeys.PROJECT.getData(dataProvider)
                ?: return null
  val source = PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataProvider) as? JComponent
               ?: return null
  val key = "NavBarPanel.copyPasteDelegator"
  val result = source.getClientProperty(key)
  if (result is CopyPasteSupport) {
    return result
  }
  else {
    return CopyPasteDelegator(project, source).also {
      source.putClientProperty(key, it)
    }
  }
}

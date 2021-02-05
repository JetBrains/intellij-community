// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
import com.intellij.openapi.roots.ui.configuration.SdkListPresenter.*
import com.intellij.ui.*
import javax.swing.JList

class RuntimeChooserPresenter(
  private val model: RuntimeChooserModel
) : ColoredListCellRenderer<RuntimeChooserItem>() {

  override fun customizeCellRenderer(list: JList<out RuntimeChooserItem>,
                                     value: RuntimeChooserItem?,
                                     index: Int,
                                     selected: Boolean,
                                     hasFocus: Boolean) {
    if (value is RuntimeChooserDownloadableItem) {
      return presentJbrItem(value)
    }

    if (value is RuntimeChooserCurrentItem) {
      presetCurrentRuntime(value)
      return
    }
  }

  private fun presetCurrentRuntime(value: RuntimeChooserCurrentItem) {
    if (value.isBundled) {
      append(LangBundle.message("dialog.item.choose.ide.runtime.bundled", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES))
      append(" ")
    }

    append(LangBundle.message("dialog.item.choose.ide.runtime.current", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES))
    append(" ")

    value.version?.let {
      append(it, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, true)
      append(" ")
    }

    value.displayName?.let {
      append(it, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      append(" ")
    }

    if (value.version == null && value.displayName == null) {
      append(LangBundle.message("dialog.item.choose.ide.runtime.unknown"))
    }
  }

  private fun presentJbrItem(value: RuntimeChooserDownloadableItem) {
    val item = value.item

    append(item.jdkVersion, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, true)
    append(" ")

    item.product.vendor.let {
      append(it, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      append(" ")
    }

    item.product.product?.let {
      append(it, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      append(" ")
    }

    item.product.flavour?.let {
      append(it, SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
  }
}

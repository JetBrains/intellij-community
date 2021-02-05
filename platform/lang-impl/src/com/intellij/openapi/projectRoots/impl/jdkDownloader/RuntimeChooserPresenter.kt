// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
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

    if (value is RuntimeChooserBundledItem) {
      append(LangBundle.message("dialog.item.choose.ide.runtime.bundled"))
      return
    }

    if (value is RuntimeChooserCurrentItem) {
      append(LangBundle.message("dialog.item.choose.ide.runtime.current"))
      return
    }
  }

  private fun presentJbrItem(value: RuntimeChooserDownloadableItem) {
    val item = value.item
    item.product.vendor.let {
      append(it)
      append(" ")
    }

    item.product.product?.let {
      append(it)
      append(" ")
    }

    append(item.jdkVersion, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, true)
    append(" ")

    item.product.flavour?.let {
      append(it, SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
  }
}

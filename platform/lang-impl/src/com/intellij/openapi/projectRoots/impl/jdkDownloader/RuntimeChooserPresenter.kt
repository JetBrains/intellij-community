// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.ui.*
import java.awt.Component
import javax.swing.JList

class RuntimeChooserPresenter : ColoredListCellRenderer<RuntimeChooseItem>() {
  override fun getListCellRendererComponent(list: JList<out RuntimeChooseItem>?,
                                            value: RuntimeChooseItem?,
                                            index: Int,
                                            selected: Boolean,
                                            hasFocus: Boolean): Component {
    if (value is RuntimeChooseSeparator) {
      return SeparatorWithText()
    }
    return super.getListCellRendererComponent(list, value, index, selected, hasFocus)
  }

  override fun customizeCellRenderer(list: JList<out RuntimeChooseItem>,
                                     value: RuntimeChooseItem?,
                                     index: Int,
                                     selected: Boolean,
                                     hasFocus: Boolean) {
    if (value is RuntimeChooserDownloadableItem) {
      return presentJbrItem(value)
    }

    if (value is RuntimeChooseCurrentItem) {
      append("Current Runtime")
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

// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SeparatorWithText
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import java.awt.Component
import javax.swing.JList

class RuntimeChooserPresenter: ColoredListCellRenderer<RuntimeChooserItem>() {

  override fun getListCellRendererComponent(list: JList<out RuntimeChooserItem>?,
                                            value: RuntimeChooserItem?,
                                            index: Int,
                                            selected: Boolean,
                                            hasFocus: Boolean): Component {

    val message = when (value) {
      is RuntimeChooserAdvancedSectionSeparator -> LangBundle.message("dialog.separator.choose.ide.runtime.advanced")
      is RuntimeChooserAdvancedJbrSelectedSectionSeparator -> LangBundle.message("dialog.separator.choose.ide.runtime.advancedJbrs")
      is RuntimeChooserCustomSelectedSectionSeparator -> LangBundle.message("dialog.separator.choose.ide.runtime.customSelected")
      else -> null
    }

    if (message == null) {
      return super.getListCellRendererComponent(list, value, index, selected, hasFocus)
    }

    val sep = SeparatorWithText()
    sep.caption = message
    return sep
  }

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

    if (value is RuntimeChooserSelectRuntimeItem) {
      append(LangBundle.message("dialog.item.choose.ide.runtime.select.runtime"), SimpleTextAttributes.GRAYED_ATTRIBUTES)
      return
    }

    if (value is RuntimeChooserAddCustomItem) {
      append(LangBundle.message("dialog.item.choose.ide.runtime.add.custom", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES))
      return
    }

    if (value is RuntimeChooserItemWithFixedLocation) {
      presetRuntime(value)
      return
    }
  }

  companion object {
    private const val SEPARATOR: @NlsSafe String = " ";

    fun SimpleColoredComponent.presetRuntime(value: RuntimeChooserItemWithFixedLocation) {
      value.version?.let {
        append(it, SimpleTextAttributes.REGULAR_ATTRIBUTES, true)
        append(SEPARATOR)
      }

      value.displayName?.let {
        append(it, SimpleTextAttributes.GRAYED_ATTRIBUTES)
        append(SEPARATOR)
      }

      if (value.version == null && value.displayName == null) {
        append(LangBundle.message("dialog.item.choose.ide.runtime.unknown"))
        append(SEPARATOR)
      }
    }

    fun SimpleColoredComponent.presetCurrentRuntime(value: RuntimeChooserCurrentItem) {
      presetRuntime(value)

      if (value.isBundled) {
        append(LangBundle.message("dialog.item.choose.ide.runtime.bundled"), SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES)
      }
    }

    fun SimpleColoredComponent.presentJbrItem(value: RuntimeChooserDownloadableItem) {
      val item = value.item

      append(item.jdkVersion, SimpleTextAttributes.REGULAR_ATTRIBUTES, true)
      append(SEPARATOR)

      item.product.vendor.let {
        append(it, SimpleTextAttributes.GRAYED_ATTRIBUTES)
        append(SEPARATOR)
      }

      item.product.product?.let {
        append(it, SimpleTextAttributes.GRAYED_ATTRIBUTES)
        append(SEPARATOR)
      }

      item.product.flavour?.let {
        append(it, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }
  }
}

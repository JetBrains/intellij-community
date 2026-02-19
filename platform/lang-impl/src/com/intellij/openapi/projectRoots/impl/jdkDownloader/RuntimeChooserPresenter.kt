// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.GroupedComboBoxRenderer
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes

internal abstract class RuntimeChooserPresenter: GroupedComboBoxRenderer<RuntimeChooserItem?>() {
  override fun customize(item: SimpleColoredComponent, value: RuntimeChooserItem?, index: Int, isSelected: Boolean, cellHasFocus: Boolean) {
    when (value) {
      is RuntimeChooserDownloadableItem -> item.presentJbrItem(value)
      is RuntimeChooserCurrentItem -> item.presetCurrentRuntime(value)
      is RuntimeChooserSelectRuntimeItem -> item.append(LangBundle.message("dialog.item.choose.ide.runtime.select.runtime"), SimpleTextAttributes.GRAYED_ATTRIBUTES)
      is RuntimeChooserAddCustomItem -> item.append(LangBundle.message("dialog.item.choose.ide.runtime.add.custom", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES))
      is RuntimeChooserItemWithFixedLocation -> item.presetRuntime(value)
      else -> {}
    }
  }

  companion object {
    private const val SEPARATOR: @NlsSafe String = " "

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

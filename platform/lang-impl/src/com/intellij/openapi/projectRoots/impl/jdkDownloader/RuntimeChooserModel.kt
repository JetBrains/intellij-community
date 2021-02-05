// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel


abstract class RuntimeChooserItem

object RuntimeChooserCurrentItem : RuntimeChooserItem()
object RuntimeChooserBundledItem : RuntimeChooserItem()

class RuntimeChooserModel {
  private var showAdvancedOptions: Boolean = false
  private var downloadableJbs: List<JdkItem> = listOf()

  private val myMainComboModel = DefaultComboBoxModel<RuntimeChooserItem>()

  val mainComboBoxModel: ComboBoxModel<RuntimeChooserItem>
    get() = myMainComboModel

  fun getDefaultInstallPathFor(item: JdkItem): String {
    val path = service<RuntimeChooserJbrInstaller>().defaultInstallDir(item)
    return FileUtil.getLocationRelativeToUserHome(path.toAbsolutePath().toString(), false)
  }

  private fun updateMainCombobox() {
    val selection = myMainComboModel.selectedItem

    myMainComboModel.removeAllElements()

    val downloadJbrItems = downloadableJbs
      .filter { showAdvancedOptions || it.isDefaultItem }
      .map { RuntimeChooserDownloadableItem(it) }

    val newList = listOf(
      RuntimeChooserBundledItem,
      RuntimeChooserCurrentItem,
    ) + downloadJbrItems

    myMainComboModel.addAll(newList)
    myMainComboModel.selectedItem = selection
                                    ?: newList.firstOrNull { it is RuntimeChooserCurrentItem }
                                    ?: newList.firstOrNull { it is RuntimeChooserBundledItem }
  }

  fun onUpdateDownloadJbrListScheduled() {
    //show progress
  }

  fun showAdvancedOptions() {
    if (showAdvancedOptions) return
    showAdvancedOptions = true
    updateMainCombobox()
  }

  fun updateDownloadJbrList(items: List<JdkItem>) {
    downloadableJbs = items.toList()
    updateMainCombobox()
  }
}


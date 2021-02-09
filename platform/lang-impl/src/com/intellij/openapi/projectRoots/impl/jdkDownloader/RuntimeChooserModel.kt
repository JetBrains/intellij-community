// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel


abstract class RuntimeChooserItem

class RuntimeChooserModel {
  private var showAdvancedOptions: Boolean = false

  private var currentRuntime : RuntimeChooserCurrentItem? = null
  private var downloadableJbs: List<JdkItem> = listOf()

  private val myMainComboModel = DefaultComboBoxModel<RuntimeChooserItem>()

  val mainComboBoxModel: ComboBoxModel<RuntimeChooserItem>
    get() = myMainComboModel

  fun getDefaultInstallPathFor(item: JdkItem): String {
    val path = getInstallPathFromText(item, null)
    return FileUtil.getLocationRelativeToUserHome(path.toAbsolutePath().toString(), false)
  }

  fun getInstallPathFromText(item: JdkItem, text: String?) : Path {
    val path = text?.trim()?.takeIf { it.isNotBlank() }?.let { FileUtil.expandUserHome(it) }
    if (path != null) {
      var file = Paths.get(path)
      repeat(1000) {
        if (!Files.exists(file)) return file
        file = Paths.get(path + "-" + (it + 1))
      }
    }
    return service<RuntimeChooserJbrInstaller>().defaultInstallDir(item)
  }

  private fun updateMainCombobox() {
    val selection = myMainComboModel.selectedItem

    myMainComboModel.removeAllElements()

    val newList = mutableListOf<RuntimeChooserItem>()

    currentRuntime?.let {
      newList += it
    }

    newList += downloadableJbs
      .filter { showAdvancedOptions || it.isDefaultItem }
      .map { RuntimeChooserDownloadableItem(it) }

    myMainComboModel.addAll(newList)
    myMainComboModel.selectedItem = selection ?: newList.firstOrNull { it is RuntimeChooserCurrentItem }
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

  fun updateCurrentRuntime(runtime: RuntimeChooserCurrentItem) {
    currentRuntime = runtime
    updateMainCombobox()
  }
}


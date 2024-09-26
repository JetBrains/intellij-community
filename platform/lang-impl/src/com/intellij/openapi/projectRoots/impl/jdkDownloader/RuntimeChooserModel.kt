// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel

internal sealed class RuntimeChooserItem

internal object RuntimeChooserSelectRuntimeItem: RuntimeChooserItem()

internal fun <Y> GraphProperty<Y>.getAndSubscribe(lifetime: Disposable, action: (Y) -> Unit) {
  action(get())
  afterChange(lifetime, action)
}

internal class RuntimeChooserModel {
  private val graph = PropertyGraph()

  val currentRuntime: GraphProperty<RuntimeChooserCurrentItem?> = graph.property(null)

  var downloadableJbs: List<JdkItem> = listOf()
  val customJdks = mutableListOf<RuntimeChooserCustomItem>()
  val advancedDownloadItems = mutableListOf<RuntimeChooserDownloadableItem>()

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
    return RuntimeChooserJbrInstaller.defaultInstallDir(item)
  }

  private fun updateMainCombobox(newSelection: RuntimeChooserItem? = null) {
    val selection = newSelection ?: myMainComboModel.selectedItem ?: RuntimeChooserSelectRuntimeItem

    myMainComboModel.removeAllElements()

    val newList = mutableListOf<RuntimeChooserItem>()

    val defaultDownloadableSdks = downloadableJbs
      .filter { it.isDefaultItem }
      .map { RuntimeChooserDownloadableItem(it) }

    advancedDownloadItems.clear()
    advancedDownloadItems.addAll(
      downloadableJbs
        .filterNot { it.isDefaultItem }
        .map { RuntimeChooserDownloadableItem(it) }
    )

    newList += defaultDownloadableSdks

    if (RuntimeChooserCustom.isActionAvailable) {
      if (customJdks.isNotEmpty()) {
        newList += customJdks
      }
      newList += RuntimeChooserAddCustomItem
    }

    if (advancedDownloadItems.isNotEmpty()) {
      newList += advancedDownloadItems
    }

    myMainComboModel.addAll(newList)
    myMainComboModel.selectedItem = selection
  }

  fun updateDownloadJbrList(items: List<JdkItem>) {
    downloadableJbs = items.toList()
    updateMainCombobox()
  }

  fun updateCurrentRuntime(runtime: RuntimeChooserCurrentItem) {
    currentRuntime.set(runtime)
    updateMainCombobox()
  }

  fun addExistingSdkItem(newItem : RuntimeChooserCustomItem) {
    customJdks += newItem
    updateMainCombobox(newItem)
  }
}


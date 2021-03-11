// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel


abstract class RuntimeChooserItem

object RuntimeChooserSelectRuntimeItem: RuntimeChooserItem()
object RuntimeChooserAdvancedSectionSeparator : RuntimeChooserItem()
object RuntimeChooserCustomSelectedSectionSeparator : RuntimeChooserItem()
object RuntimeChooserAdvancedJbrSelectedSectionSeparator : RuntimeChooserItem()

internal fun <Y> GraphProperty<Y>.getAndSubscribe(lifetime: Disposable, action: (Y) -> Unit) {
  action(get())
  afterChange(action, lifetime)
}

class RuntimeChooserModel {
  private val graph = PropertyGraph()

  val currentRuntime : GraphProperty<RuntimeChooserCurrentItem?> = graph.graphProperty { null }

  private var downloadableJbs: List<JdkItem> = listOf()
  private val customJdks = mutableListOf<RuntimeChooserCustomItem>()

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

  private fun updateMainCombobox(newSelection: RuntimeChooserItem? = null) {
    val selection = newSelection ?: myMainComboModel.selectedItem ?: RuntimeChooserSelectRuntimeItem

    myMainComboModel.removeAllElements()

    val newList = mutableListOf<RuntimeChooserItem>()

    val defaultDownloadableSdks = downloadableJbs
      .filter { it.isDefaultItem }
      .map { RuntimeChooserDownloadableItem(it) }

    val advancedDownloadItems = downloadableJbs
      .filterNot { it.isDefaultItem }
      .map { RuntimeChooserDownloadableItem(it) }

    newList += defaultDownloadableSdks
    newList += RuntimeChooserAdvancedSectionSeparator

    if (RuntimeChooserCustom.isActionAvailable) {
      if (customJdks.isNotEmpty()) {
        newList += RuntimeChooserCustomSelectedSectionSeparator
        newList += customJdks
      }
      newList += RuntimeChooserAddCustomItem
    }

    if (advancedDownloadItems.isNotEmpty()) {
      newList += RuntimeChooserAdvancedJbrSelectedSectionSeparator
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


// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel


abstract class RuntimeChooseItem

object RuntimeChooseSeparator : RuntimeChooseItem()

object RuntimeChooseCurrentItem: RuntimeChooseItem()
object RuntimeChooseBundledItem: RuntimeChooseItem()

class RuntimeChooserModel {
  private var downloadableJbs : List<RuntimeChooserDownloadableItem> = listOf()


  private val myMainComboModel = DefaultComboBoxModel<RuntimeChooseItem>()

  val mainComboBoxModel : ComboBoxModel<RuntimeChooseItem>
    get() = myMainComboModel

  fun updateDownloadJbrList(items: List<JdkItem>) {
    invokeLater(modalityState = ModalityState.any()) {
      downloadableJbs = items.map { RuntimeChooserDownloadableItem(it) }
      updateMainCombobox()
    }
  }

  private fun updateMainCombobox() {
    val selection = myMainComboModel.selectedItem

    myMainComboModel.removeAllElements()

    val newList = listOf(
      RuntimeChooseBundledItem,
      RuntimeChooseSeparator,
      RuntimeChooseCurrentItem,
      RuntimeChooseSeparator
    ) + downloadableJbs

    myMainComboModel.addAll(newList)
    myMainComboModel.selectedItem = selection
  }

  fun onUpdateDownloadJbrListScheduled() {
    //show progress
  }
}


// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.popup.list.ComboBoxPopup
import com.intellij.util.indexing.FileBasedIndexTumbler
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProviderBean
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.function.Consumer
import javax.swing.ListCellRenderer
import javax.swing.ListModel

@ApiStatus.Internal
class SwitchFileBasedIndexStorageAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val supportedIndexStorages = supportedIndexStorageDescriptors()
    val activeIndexStorage = supportedIndexStorages.find { it.bean == IndexLayoutPersistentSettings.getCustomLayout() }
    val popupContext = IndexStorageDescriptorPopupContext(project, supportedIndexStorages)
    ComboBoxPopup(popupContext, activeIndexStorage, Consumer {
      restartIndexesWithStorage(it)
    }).showInBestPositionFor(e.dataContext)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  private fun restartIndexesWithStorage(indexStorage: IndexStorageDescriptor) {
    val usedLayout = IndexLayoutPersistentSettings.getCustomLayout()
    if (usedLayout != indexStorage.bean) {
      val switcher = FileBasedIndexTumbler("Index Storage Switching")
      switcher.turnOff()
      try {
        IndexLayoutPersistentSettings.setCustomLayout(indexStorage.bean)
      }
      finally {
        switcher.turnOn(null)
      }
    }
  }
}

private data class IndexStorageDescriptor(val presentableName: @Nls String,
                                          val id: String,
                                          val version: Int,
                                          val bean: FileBasedIndexLayoutProviderBean?)

private fun supportedIndexStorageDescriptors(): List<IndexStorageDescriptor> =
  IndexStorageLayoutLocator.supportedLayoutProviders.map {
    IndexStorageDescriptor(it.localizedPresentableName, it.id, it.version, it)
  }

private class IndexStorageDescriptorPopupContext(private val project: Project,
                                                 indexStorages: List<IndexStorageDescriptor>) : ComboBoxPopup.Context<IndexStorageDescriptor> {
  private val model = CollectionComboBoxModel(indexStorages)

  override fun getProject(): Project = project

  override fun getModel(): ListModel<IndexStorageDescriptor> {
    return model
  }

  override fun getRenderer(): ListCellRenderer<IndexStorageDescriptor> {
    return SimpleListCellRenderer.create { label, desc, _ -> label.text = "${desc.presentableName} (${desc.id}: v${desc.version})" }
  }
}
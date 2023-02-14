// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.popup.list.ComboBoxPopup
import com.intellij.util.indexing.FileBasedIndexTumbler
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.indexing.storage.FileBasedIndexLayoutProviderBean
import org.jetbrains.annotations.Nls
import java.util.function.Consumer
import javax.swing.ListCellRenderer
import javax.swing.ListModel

class SwitchFileBasedIndexStorageAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val allStorages = customIndexStorageDescriptors() + defaultIndexStorageDescriptor()
    val activeStorage = allStorages.find { it.bean == FileBasedIndexLayoutSettings.getUsedLayout()}
    val popupContext = IndexStorageDescriptorPopupContext(project, allStorages)
    ComboBoxPopup(popupContext, activeStorage, Consumer {
      restartIndexesWithStorage(it)
    }).showInBestPositionFor(e.dataContext)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  private fun restartIndexesWithStorage(indexStorage: IndexStorageDescriptor) {
    val usedLayout = FileBasedIndexLayoutSettings.getUsedLayout()
    if (usedLayout != indexStorage.bean) {
      val switcher = FileBasedIndexTumbler("Index Storage Switching")
      switcher.turnOff()
      try {
        FileBasedIndexLayoutSettings.setUsedLayout(indexStorage.bean)
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

private fun defaultIndexStorageDescriptor(): IndexStorageDescriptor {
  return IndexStorageDescriptor(IndexingBundle.message("default.index.storage.presentable.name"),
                                "default",
                                0,
                                null)
}

private fun customIndexStorageDescriptors(): List<IndexStorageDescriptor> =
  DefaultIndexStorageLayout.availableLayouts.map {
    IndexStorageDescriptor(it.localizedPresentableName, it.id, it.version, it)
  }

private class IndexStorageDescriptorPopupContext(private val project: Project,
                                       val indexStorages: List<IndexStorageDescriptor>) : ComboBoxPopup.Context<IndexStorageDescriptor> {
  private val model = CollectionComboBoxModel(indexStorages)

  override fun getProject(): Project = project

  override fun getModel(): ListModel<IndexStorageDescriptor> {
    return model
  }

  override fun getRenderer(): ListCellRenderer<IndexStorageDescriptor> {
    return SimpleListCellRenderer.create { label, value, _ -> label.text = value.presentableName }
  }
}
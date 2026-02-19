// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration.ui

import com.intellij.history.core.tree.RootEntry
import com.intellij.history.integration.IntegrationTestCase
import com.intellij.history.integration.ui.models.DirectoryHistoryDialogModel
import com.intellij.history.integration.ui.models.EntireFileHistoryDialogModel
import com.intellij.history.integration.ui.models.SelectionHistoryDialogModel
import com.intellij.openapi.vfs.VirtualFile

fun IntegrationTestCase.createFileModel(file: VirtualFile): EntireFileHistoryDialogModel {
  return object : EntireFileHistoryDialogModel(project, gateway, vcs, file) {
    override fun createRootEntry(): RootEntry {
      return this@createFileModel.rootEntry
    }
  }
}

fun IntegrationTestCase.createDirectoryModel(directory: VirtualFile): DirectoryHistoryDialogModel {
  return object : DirectoryHistoryDialogModel(project, gateway, vcs, directory) {
    override fun createRootEntry(): RootEntry {
      return this@createDirectoryModel.rootEntry
    }
  }
}

fun IntegrationTestCase.createSelectionModel(file: VirtualFile, from: Int, to: Int): SelectionHistoryDialogModel {
  return object : SelectionHistoryDialogModel(project, gateway, vcs, file, from, to) {
    override fun createRootEntry(): RootEntry {
      return this@createSelectionModel.rootEntry
    }
  }
}

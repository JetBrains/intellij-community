// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.local

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@State(name = "FileTypeUsageLocalSummary", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)], reportStatistic = false)
private class FileTypeUsageLocalSummary : PersistentStateComponent<FileTypeUsageLocalSummaryState>,
                                          FileTypeUsageSummaryProvider,
                                          SimpleModificationTracker() {
  @Volatile
  private var state = FileTypeUsageLocalSummaryState()

  override fun getState() = state

  override fun loadState(state: FileTypeUsageLocalSummaryState) {
    this.state = state
  }

  override fun getFileTypeStats(): Map<String, FileTypeUsageSummary> {
    return if (state.data.isEmpty()) {
      emptyMap()
    }
    else {
      HashMap(state.data)
    }
  }

  @Synchronized
  override fun updateFileTypeSummary(fileTypeName: String) {
    val summary = state.data.computeIfAbsent(fileTypeName) { FileTypeUsageSummary() }
    summary.usageCount++
    summary.lastUsed = System.currentTimeMillis()

    incModificationCount()
  }
}

@ApiStatus.Internal
@Serializable
data class FileTypeUsageLocalSummaryState(
  @JvmField
  internal val data: MutableMap<String, FileTypeUsageSummary> = HashMap()
)

private class FileTypeSummaryListener : FileEditorManagerListener {
  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    val service = source.project.getService(FileTypeUsageSummaryProvider::class.java)
    val fileTypeName = file.fileType.name
    service.updateFileTypeSummary(fileTypeName)
  }
}

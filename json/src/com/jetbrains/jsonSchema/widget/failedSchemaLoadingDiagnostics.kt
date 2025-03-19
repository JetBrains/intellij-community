// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.widget

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import com.jetbrains.jsonSchema.fus.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun logSchemaDownloadFailureDiagnostics(schemaFile: HttpVirtualFile, project: Project) {
  val schemaPath = schemaFile.getPath()
  val nioSchemaFile = schemaFile.toNioPath().toFile()
  val nioFileExists = nioSchemaFile.exists()
  val isFile = nioSchemaFile.isFile
  val nioFileLength = nioSchemaFile.length()
  val canRead = nioSchemaFile.canRead()
  val fileInfo = schemaFile.getFileInfo()
  val errorMessageOrNull = fileInfo?.getErrorMessage()
  val stateOrNull = fileInfo?.getState()
  val instantData = "Schema loading failure report. SchemaPath: $schemaPath, " +
                    "File exists: $nioFileExists, isFile: $isFile, File length: $nioFileLength bytes, " +
                    "Can read: $canRead, Error message: ${errorMessageOrNull ?: "none"}, State: ${stateOrNull ?: "unknown"}"

  DiagnosticsScopeProvider.getInstance(project).coroutineScope.launch {
    val anotherFileLookupAttempt = VfsUtil.findFile(schemaFile.toNioPath(), true)
    val isNull = anotherFileLookupAttempt == null
    val isValid = anotherFileLookupAttempt?.isValid
    JsonHttpFileLoadingUsageCollector.jsonSchemaHighlightingSessionData.log(
      JsonHttpFileNioFile.with(nioFileExists),
      JsonHttpFileNioFileCanBeRead.with(canRead),
      JsonHttpFileNioFileLength.with(nioFileLength),
      JsonHttpFileVfsFile.with(schemaFile.fileInfo?.localFile != null),
      JsonHttpFileSyncRefreshVfsFile.with(anotherFileLookupAttempt != null),
      JsonHttpFileVfsFileValidity.with(isValid ?: false),
      JsonHttpFileDownloadState.with(JsonRemoteSchemaDownloadState.fromRemoteFileState(stateOrNull))
    )
    Logger.getInstance(schemaFile.javaClass).error("$instantData, syncRefreshFileIsNull: $isNull, syncRefreshFileIsValid: $isValid")
  }
}

@Service(Service.Level.PROJECT)
private class DiagnosticsScopeProvider(val coroutineScope: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): DiagnosticsScopeProvider {
      return project.getService(DiagnosticsScopeProvider::class.java)
    }
  }
}
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
import java.util.concurrent.CancellationException

internal fun logSchemaDownloadFailureDiagnostics(schemaFile: HttpVirtualFile, project: Project) {
  val schemaPath = schemaFile.getPath()
  val nioSchemaFile = runCatching { schemaFile.toNioPath().toFile() }
  if (nioSchemaFile.isFailure && nioSchemaFile.exceptionOrNull() is CancellationException) {
    nioSchemaFile.getOrThrow()
  }
  val nioFileExists = nioSchemaFile.getOrNull()?.exists() ?: false
  val isFile = nioSchemaFile.getOrNull()?.isFile ?: false
  val nioFileLength = nioSchemaFile.getOrNull()?.length() ?: -2
  val canRead = nioSchemaFile.getOrNull()?.canRead() ?: false
  val fileInfo = schemaFile.getFileInfo()
  val errorMessage = listOfNotNull(fileInfo?.getErrorMessage(), nioSchemaFile.exceptionOrNull()?.message)
    .ifEmpty { listOf("none") }.joinToString(", ")
  val stateOrNull = fileInfo?.getState()
  val instantData = "Schema loading failure report. SchemaPath: $schemaPath, " +
                    "File exists: $nioFileExists, isFile: $isFile, File length: $nioFileLength bytes, " +
                    "Can read: $canRead, Error message: ${errorMessage}, State: ${stateOrNull ?: "unknown"}"

  DiagnosticsScopeProvider.getInstance(project).coroutineScope.launch {
    val anotherFileLookupAttempt = runCatching { VfsUtil.findFile(schemaFile.toNioPath(), true) }
    val isNull = anotherFileLookupAttempt.getOrNull() == null
    val isValid = anotherFileLookupAttempt.getOrNull()?.isValid ?: false
    JsonHttpFileLoadingUsageCollector.jsonSchemaHighlightingSessionData.log(
      JsonHttpFileNioFile.with(nioFileExists),
      JsonHttpFileNioFileCanBeRead.with(canRead),
      JsonHttpFileNioFileLength.with(nioFileLength),
      JsonHttpFileVfsFile.with(schemaFile.fileInfo?.localFile != null),
      JsonHttpFileSyncRefreshVfsFile.with(anotherFileLookupAttempt.getOrNull() != null),
      JsonHttpFileVfsFileValidity.with(isValid),
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
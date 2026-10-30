// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.welcomeFiles

import com.intellij.ide.rpc.awaitWithLocalFallback
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Connects the editor tabs of the Home files on the frontend with the Home files on the backend.
 */
@ApiStatus.Internal
@Rpc
interface WelcomeFilesApi : RemoteApi<Unit> {
  /**
   * Returns true when [file] is a Home file of the welcome project [projectId].
   */
  suspend fun isWelcomeFile(projectId: ProjectId, file: VirtualFileId): Boolean

  /**
   * Shows the save dialog and copies [file] to the target.
   * Then it opens the target, closes the tab of [file], and deletes [file].
   * The call returns at once. The returned deferred completes when the user closes the save dialog.
   */
  suspend fun saveAs(projectId: ProjectId, file: VirtualFileId): Deferred<Unit>

  /**
   * Deletes [file] without a confirmation.
   */
  suspend fun discard(projectId: ProjectId, file: VirtualFileId)

  /**
   * Copies [files] to a target the user selects, then closes and deletes them. The project close waits for the result.
   * One file gets the save dialog. Several files get a directory chooser and keep their names.
   * The call returns at once. The returned deferred completes with false when the user cancels the dialog.
   */
  suspend fun saveOnClose(projectId: ProjectId, files: List<VirtualFileId>): Deferred<Boolean>

  /**
   * Closes the editors of [files] and deletes the files without a confirmation.
   */
  suspend fun discardOnClose(projectId: ProjectId, files: List<VirtualFileId>)

  /**
   * Emits the language of [file], then emits it again after each rename or language change of [file].
   * The flow is empty when [file] is not a Home file of the welcome project [projectId].
   */
  suspend fun fileLanguage(projectId: ProjectId, file: VirtualFileId): Flow<WelcomeFileLanguage>

  /**
   * Returns the languages that the user can select for a Home file.
   */
  suspend fun getLanguages(): List<WelcomeLanguageItem>

  /**
   * Sets the language [languageId] for [file]. [languageId] is a [WelcomeLanguageItem.id] from [getLanguages].
   */
  suspend fun changeLanguage(projectId: ProjectId, file: VirtualFileId, languageId: String)

  companion object {
    /**
     * Returns the api of this session. A light session has no backend, so it gets an api that finds no Home file.
     */
    suspend fun getInstance(): WelcomeFilesApi {
      return LiteRemoteApiProviderService.awaitWithLocalFallback(remoteApiDescriptor<WelcomeFilesApi>()) {
        NoBackendWelcomeFilesApi
      }
    }
  }
}

private object NoBackendWelcomeFilesApi : WelcomeFilesApi {
  override suspend fun isWelcomeFile(projectId: ProjectId, file: VirtualFileId): Boolean = false

  override suspend fun saveAs(projectId: ProjectId, file: VirtualFileId): Deferred<Unit> = CompletableDeferred(Unit)

  override suspend fun discard(projectId: ProjectId, file: VirtualFileId) {
  }

  override suspend fun saveOnClose(projectId: ProjectId, files: List<VirtualFileId>): Deferred<Boolean> = CompletableDeferred(true)

  override suspend fun discardOnClose(projectId: ProjectId, files: List<VirtualFileId>) {
  }

  override suspend fun fileLanguage(projectId: ProjectId, file: VirtualFileId): Flow<WelcomeFileLanguage> = emptyFlow()

  override suspend fun getLanguages(): List<WelcomeLanguageItem> = emptyList()

  override suspend fun changeLanguage(projectId: ProjectId, file: VirtualFileId, languageId: String) {
  }
}

/**
 * The current language of a Home file.
 */
@ApiStatus.Internal
@Serializable
class WelcomeFileLanguage(val displayName: @NlsSafe String, val icon: IconId?)

/**
 * A language that the user can select for a Home file.
 * [id] is the language ID, or the file type name for a file type without a language.
 */
@ApiStatus.Internal
@Serializable
class WelcomeLanguageItem(val id: String, val displayName: @NlsSafe String, val extension: @NlsSafe String, val icon: IconId?)

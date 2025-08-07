// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find

import com.intellij.find.FindModel
import com.intellij.ide.ui.SerializableTextChunk
import com.intellij.ide.ui.colors.ColorId
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.usageView.UsageInfo
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@Rpc
interface FindRemoteApi : RemoteApi<Unit> {
  /**
   * Searches for matches based on the specified find model within a given project and initial set of files.
   *
   * @param findModel the model containing search parameters and criteria
   * @param projectId the identifier of the project where the search is performed
   * @param filesToScanInitially a list of file identifiers to be scanned initially as part of the search
   * @return a flow emitting search results as instances of [FindInFilesResult]
   */
  suspend fun findByModel(findModel: FindModel, projectId: ProjectId, filesToScanInitially: List<VirtualFileId>, maxUsagesCount: Int): Flow<SearchResult>

  /**
   * Initiates a "Find all"/"Replace all" operation on the backend and displays results in the Find tool window.
   * NOTE: Currently, the operation is performed on the backend only, with results not being returned to the frontend
   * should be reworked when Find tool window is split for remote development.
   *
   * This function handles searching for text based on the provided search model
   *
   * @param findModel the model containing search parameters and criteria
   * @param project the project where the search is performed
   * @param openInNewTab whether to show results in a separate view. This setting is persisted on the backend
   *                     and restored after performing the findAll / replaceAll operation
   */
  suspend fun performFindAllOrReplaceAll(findModel: FindModel, openInNewTab: Boolean, projectId: ProjectId)

  suspend fun checkDirectoryExists(findModel: FindModel): Boolean

  companion object {
    @JvmStatic
    suspend fun getInstance(): FindRemoteApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<FindRemoteApi>())
    }
  }
}

/**
 * Represents the result of a "Find in files" operation, encapsulating information about the search result's presentation,
 * location, and attributes.
 *
 * @property presentation A list of [SerializableTextChunk] containing formatted text and its visual attributes.
 * @property line The line number where the search result was found, starting from 0.
 * @property navigationOffset The character offset from the start of the file to the result's location.
 * @property mergedOffsets A list of integer offsets that indicate additional positions related to the search result.
 * @property length The length of the search result in characters.
 * @property fileId The unique identifier of the file containing the search result.
 * @property presentablePath A user-readable path to the file containing the search result in the preview line.
 * @property shortenPresentablePath A user-readable path to the file containing the search result in the list of results.
 * @property backgroundColor The background color depends on the search scope.
 * @property tooltipText The tooltip text to be displayed when hovering over the result, if available.
 * @property iconId The identifier for an icon, if any.
 */
@Internal
@Serializable
data class FindInFilesResult(
  val presentation: List<SerializableTextChunk>,
  val line: Int,
  val navigationOffset: Int,
  val mergedOffsets: List<Int>,
  val length: Int,
  val fileId: VirtualFileId,
  val presentablePath: @NlsSafe String,
  val shortenPresentablePath: @NlsSafe String,
  val backgroundColor: ColorId?,
  val tooltipText: @NlsContexts.Tooltip String?,
  val iconId: IconId?,
  val fileLength: Int,
  @Transient val usageInfos: List<UsageInfo> = emptyList(),
)

@Internal
@Serializable
sealed interface SearchResult {
  fun getData(): FindInFilesResult?
}

@Internal
@Serializable
data class SearchResultFound(val result: FindInFilesResult) : SearchResult {
  override fun getData(): FindInFilesResult = result
}

@Internal
@Serializable
class SearchStopped() : SearchResult {
  override fun getData(): FindInFilesResult? = null
}

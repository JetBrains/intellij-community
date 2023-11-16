// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff

import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.filePath
import com.intellij.collaboration.util.fileStatus
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.history.VcsDiffUtil
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class CodeReviewDiffRequestProducer(
  private val project: Project,
  val change: RefComparisonChange,
  private val delegate: ChangeDiffRequestProducer,
  private val diffComputer: DiffUserDataKeysEx.DiffComputer?,
  private val postProcess: DiffRequest.() -> Unit = {}
) : ChangeDiffRequestChain.Producer by delegate, ScrollableDiffRequestProducer {

  private val _scrollRequest = Channel<DiffLineLocation>(capacity = 1, BufferOverflow.DROP_OLDEST)
  override val scrollRequests: Flow<DiffLineLocation> = _scrollRequest.receiveAsFlow()

  private var requestedScrollLocation: DiffLineLocation? = null

  override fun getName(): String = delegate.name
  override fun getFilePath(): FilePath = change.filePath
  override fun getFileStatus(): FileStatus = change.fileStatus

  override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest =
    delegate.process(context, indicator).also { request ->
      request.putUserData(RefComparisonChange.KEY, change)
      request.putUserData(DiffUserDataKeysEx.CUSTOM_DIFF_COMPUTER, diffComputer)

      val titleLeft = VcsDiffUtil.getRevisionTitle(change.revisionNumberBefore.toShortString(), change.filePathAfter, null)
      request.putUserData(DiffUserDataKeysEx.VCS_DIFF_LEFT_CONTENT_TITLE, titleLeft)
      val titleRight = VcsDiffUtil.getRevisionTitle(change.revisionNumberAfter.toShortString(), change.filePathAfter, change.filePathBefore)
      request.putUserData(DiffUserDataKeysEx.VCS_DIFF_RIGHT_CONTENT_TITLE, titleRight)

      val scrollLocation = requestedScrollLocation?.let { Pair(it.first, it.second) }
      if (scrollLocation != null) {
        request.putUserData(DiffUserDataKeys.SCROLL_TO_LINE, scrollLocation)
        // only perform the scroll once
        requestedScrollLocation = null
      }
    }.apply(postProcess)

  fun scrollTo(loc: DiffLineLocation) {
    requestedScrollLocation = loc
    _scrollRequest.trySend(loc)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as CodeReviewDiffRequestProducer

    if (project != other.project) return false
    if (change != other.change) return false

    return true
  }

  override fun hashCode(): Int {
    var result = project.hashCode()
    result = 31 * result + change.hashCode()
    return result
  }
}
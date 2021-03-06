// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.commit

import com.intellij.CommonBundle
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.PerformInBackgroundOption.ALWAYS_BACKGROUND
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.commit.AmendCommitAware
import com.intellij.vcs.commit.EditedCommitDetails
import com.intellij.vcs.commit.EditedCommitDetailsImpl
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsProjectLog
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.CancellablePromise
import org.jetbrains.concurrency.errorIfNotMessage
import org.jetbrains.concurrency.rejectedCancellablePromise

private val LOG = logger<AmendCommitService>()

private fun rejected(message: String): CancellablePromise<EditedCommitDetails> {
  LOG.debug(message)
  return rejectedCancellablePromise(message)
}

abstract class AmendCommitService(protected val project: Project) : AmendCommitAware {
  private val vcsLog: VcsProjectLog get() = VcsProjectLog.getInstance(project)
  private val vcsLogObjectsFactory: VcsLogObjectsFactory get() = project.service()

  override fun getAmendCommitDetails(root: VirtualFile): CancellablePromise<EditedCommitDetails> {
    val repository = VcsRepositoryManager.getInstance(project).getRepositoryForRootQuick(root) ?: return rejected(DvcsBundle.message("error.message.amend.no.repository.for.root", root))
    val logData = vcsLog.dataManager ?: return rejected(DvcsBundle.message("error.message.amend.no.vcs.log.available"))
    val lastCommitId = repository.currentRevision ?: return rejected(DvcsBundle.message("error.message.amend.repository.is.empty.for.root", root))

    return getCommitDetails(logData, root, vcsLogObjectsFactory.createHash(lastCommitId))
  }

  private fun getCommitDetails(logData: VcsLogData, root: VirtualFile, hash: Hash): AsyncPromise<EditedCommitDetails> {
    val promise = AsyncPromise<EditedCommitDetails>()
    val indicator = BackgroundableProcessIndicator(project, LoadDetailsTaskInfo(), ALWAYS_BACKGROUND)

    promise.onError {
      LOG.errorIfNotMessage(it)
      if (indicator.isRunning) indicator.cancel() // promise canceled
    }
    logData.commitDetailsGetter.loadCommitsData(
      listOf(logData.getCommitIndex(hash, root)),
      { commits -> promise.setCommit(hash, commits.firstOrNull(), logData.currentUser[root]) },
      { error -> promise.setError(error) },
      indicator
    )

    return promise
  }
}

private fun AsyncPromise<EditedCommitDetails>.setCommit(hash: Hash, commit: VcsFullCommitDetails?, currentUser: VcsUser?) {
  if (commit == null) {
    val message = DvcsBundle.message("error.message.amend.commit.cant.get.details.for.hash", hash)

    LOG.debug(message)
    setError(message)
  }
  else {
    setResult(EditedCommitDetailsImpl(currentUser, commit))
  }
}

private class LoadDetailsTaskInfo : TaskInfo {
  override fun getTitle(): String = VcsBundle.message("amend.commit.load.details.task.title")
  override fun isCancellable(): Boolean = false
  override fun getCancelText(): String = CommonBundle.message("button.cancel")
  override fun getCancelTooltipText(): String = cancelText
}
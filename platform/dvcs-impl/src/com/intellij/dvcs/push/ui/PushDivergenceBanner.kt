// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.dvcs.push.ui

import com.intellij.dvcs.push.PushDivergence
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.InlineBanner
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

/**
 * A repository whose push target has commits that are not in the pushed source.
 *
 * @param branchName the name of the pushed local branch, or `null` when the pushed source is not a branch
 * @param canForcePush true when Force Push in the push dialog can replace the remote commits
 * @param isFetchFailed true when the user asked to fetch the remote commits and the fetch failed
 */
internal class DivergedRepository(
  val branchName: @NlsSafe String?,
  val divergence: PushDivergence,
  val canForcePush: Boolean,
  val isFetchFailed: Boolean,
)

private const val BUNDLE = "messages.DvcsBundle"

/**
 * The texts of the warning for one state of the diverged repositories.
 *
 * @param single the text for one repository with a branch name and the number of the remote commits
 * @param unnamed the text for one repository whose pushed source is not a branch, with the number of the remote commits
 * @param many the text for several repositories, with the number of the repositories
 */
private enum class BannerText(
  @PropertyKey(resourceBundle = BUNDLE) val single: String,
  @PropertyKey(resourceBundle = BUNDLE) val unnamed: String,
  @PropertyKey(resourceBundle = BUNDLE) val many: String,
) {
  DIVERGED("push.divergence.banner.diverged", "push.divergence.banner.diverged.unnamed", "push.divergence.banner.diverged.many"),
  DIVERGED_NO_FORCE("push.divergence.banner.diverged.no.force", "push.divergence.banner.diverged.no.force.unnamed",
                    "push.divergence.banner.diverged.many.no.force"),
  FETCH_REQUIRED("push.divergence.banner.fetch.required", "push.divergence.banner.fetch.required.unnamed",
                 "push.divergence.banner.fetch.required.many"),
  FETCH_FAILED("push.divergence.banner.fetch.failed", "push.divergence.banner.fetch.failed.unnamed", "push.divergence.banner.fetch.failed.many");

  /**
   * Formats the text for [repositories]. The branch name is escaped, because the banner shows HTML.
   */
  fun format(repositories: List<DivergedRepository>): @Nls String {
    val repository = repositories.singleOrNull() ?: return DvcsBundle.message(many, repositories.size)
    val count = (repository.divergence as? PushDivergence.Diverged)?.remoteCommitCount ?: 0
    val branchName = repository.branchName ?: return DvcsBundle.message(unnamed, count)
    return DvcsBundle.message(single, HtmlChunk.text(branchName).toString(), count)
  }
}

/**
 * Creates the warning at the top of the push dialog. It tells that the push targets have commits that are not in the pushed sources.
 *
 * @param vcsName the name of the VCS for the link to the VCS log
 * @param openLog opens the VCS log with the remote commits, or `null` when the log can't show them
 */
internal fun createDivergenceBanner(
  repositories: List<DivergedRepository>,
  updateProject: () -> Unit,
  vcsName: @Nls String,
  openLog: (() -> Unit)?,
): InlineBanner? {
  if (repositories.isEmpty()) return null

  val fetchRequired = repositories.filter { it.divergence is PushDivergence.FetchRequired }
  val text = when {
    fetchRequired.isEmpty() && repositories.all { it.canForcePush } -> BannerText.DIVERGED.format(repositories)
    fetchRequired.isEmpty() -> BannerText.DIVERGED_NO_FORCE.format(repositories)
    fetchRequired.all { it.isFetchFailed } -> BannerText.FETCH_FAILED.format(fetchRequired)
    else -> BannerText.FETCH_REQUIRED.format(fetchRequired)
  }

  val banner = InlineBanner(text, EditorNotificationPanel.Status.Warning).showCloseButton(false)
  if (fetchRequired.isEmpty() && openLog != null) {
    banner.addAction(DvcsBundle.message("push.divergence.banner.show.in.log", vcsName)) { openLog() }
  }
  banner.addAction(DvcsBundle.message("push.divergence.banner.update.project")) { updateProject() }
  return banner
}

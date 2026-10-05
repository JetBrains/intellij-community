// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push.ui

import com.intellij.dvcs.push.PushDivergence

/**
 * The state of a repository in the push dialog, as far as the remote commits are concerned.
 *
 * @param isFetchFailed true when the user asked to fetch the remote commits and the fetch failed
 * @param divergence the remote commits, or `null` when a plain push can succeed
 * @param isForcePushAllowed true when the push support allows a force push to the target, for example to a branch that is not protected
 */
internal class PushRepositoryState(
  val isFetchFailed: Boolean,
  val divergence: PushDivergence?,
  val isForcePushAllowed: Boolean,
)

/**
 * Decides whether the push dialog requires a fetch, and which warning it shows, from the remote commits of the selected repositories.
 */
internal object PushDivergenceRules {
  /**
   * Returns true when a selected repository has remote commits that are not fetched, so a push fails.
   * After a failed fetch the dialog lets the user push, and the remote decides.
   */
  fun isFetchRequired(selected: List<PushRepositoryState>): Boolean =
    selected.any { it.divergence is PushDivergence.FetchRequired && !it.isFetchFailed }

  /**
   * Returns true when the warning can suggest Force Push to replace the remote commits of the repository.
   * Force Push is never the default action, because a force push drops the remote commits.
   */
  fun canForcePush(repository: PushRepositoryState): Boolean =
    repository.isForcePushAllowed && repository.divergence is PushDivergence.Diverged
}

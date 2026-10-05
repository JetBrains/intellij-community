// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push

import com.intellij.vcs.log.Hash
import org.jetbrains.annotations.ApiStatus

/**
 * Describes the commits in the push target that are not in the pushed source.
 * The remote rejects a plain push when the target has such commits.
 */
@ApiStatus.Experimental
sealed interface PushDivergence {
  /**
   * The position of the target that a force push expects, so it does not drop commits that the user did not see.
   * It is `null` when a force push does not check the position of the target.
   */
  val remoteTip: Hash?

  /**
   * The remote has commits that are not in the local repository.
   * A push fails until the user fetches them, see [PushSupport.fetchPushTarget].
   */
  class FetchRequired(override val remoteTip: Hash?) : PushDivergence

  /**
   * The local repository has the remote commits.
   *
   * @param remoteCommitCount the number of the remote commits
   * @param logBranchNames the names of the branches that show the divergence in the VCS log, for example the pushed branch and the target branch
   */
  class Diverged(val remoteCommitCount: Int, override val remoteTip: Hash?, val logBranchNames: List<String>) : PushDivergence
}

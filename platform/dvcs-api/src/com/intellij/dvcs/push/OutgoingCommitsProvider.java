// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push;

import com.intellij.dvcs.repo.Repository;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provider for outgoing commits
 */
public abstract class OutgoingCommitsProvider<Repo extends Repository, Source extends PushSource, Target extends PushTarget>  {

  /**
   * Collect either outgoing commits or errors for the given repository and {@link PushSpec}.
   *
   * @param initial true for the first attempt to load commits, which happens when the push dialog just appears on the screen.
   *                If later the user modifies the push target, commits are reloaded, and {@code initial} is false.
   *                <br/>
   *                Implementations should make sure that if {@code initial} is true, no user interaction is allowed
   *                (to avoid suddenly throwing dialogs into user's face).
   *                E.g. if authentication is needed to collect outgoing changes, then the method should silently show the corresponding
   *                request in the error field of the OutgoingResult.
   */
  public abstract @NotNull OutgoingResult getOutgoingCommits(@NotNull Repo repository, @NotNull PushSpec<Source, Target> pushSpec, boolean initial);

  /**
   * Finds the commits in the push target that are not in the pushed source.
   * The push dialog calls it after {@link #getOutgoingCommits(Repository, PushSpec, boolean)}: first without the remote, then with it.
   * With {@code checkRemote}, implementations can use the network, but must not interact with the user.
   *
   * @param checkRemote true to ask the remote for the position of the target
   * @return the divergence, or {@code null} if a plain push can succeed
   */
  @ApiStatus.Experimental
  public @Nullable PushDivergence detectDivergence(@NotNull Repo repository,
                                                   @NotNull PushSpec<Source, Target> pushSpec,
                                                   boolean checkRemote) {
    return null;
  }

}

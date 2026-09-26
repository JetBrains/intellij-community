// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vcs.AbstractVcs;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class to provide vcs-specific info
 */

public abstract class PushSupport<Repo extends Repository, Source extends PushSource, Target extends PushTarget> {

  public static final ExtensionPointName<PushSupport<? extends Repository, ? extends PushSource, ? extends PushTarget>> PUSH_SUPPORT_EP =
    ExtensionPointName.create("com.intellij.pushSupport");

  public abstract @NotNull AbstractVcs getVcs();

  public abstract @NotNull Pusher<Repo, Source, Target> getPusher();

  public abstract @NotNull OutgoingCommitsProvider<Repo, Source, Target> getOutgoingCommitsProvider();

  public boolean canBePushed(@NotNull Repo repository, @NotNull Source source, @NotNull Target target) {
    return true;
  }

  /**
   * @return Default push destination
   */
  public abstract @Nullable Target getDefaultTarget(@NotNull Repo repository);


  /**
   * @return Push destination for source
   */
  public @Nullable Target getDefaultTarget(@NotNull Repo repository, @NotNull Source source) { return null; }

  /**
   * @return current source(branch) for repository
   */
  public abstract @Nullable Source getSource(@NotNull Repo repository);

  /**
   * @return RepositoryManager for vcs
   */
  public abstract @NotNull RepositoryManager<Repo> getRepositoryManager();

  public @Nullable VcsPushOptionsPanel createOptionsPanel() {
    return null;
  }

  public abstract @NotNull PushTargetPanel<Target> createTargetPanel(@NotNull Repo repository,
                                                                     @NotNull Source source,
                                                                     @Nullable Target defaultTarget);

  public boolean shouldRequestIncomingChangesForNotCheckedRepositories() {
    return true;
  }

  /**
   * Returns true if force push is allowed now in the selected repository.
   * <p/>
   * Force push might depend on the branch user is pushing to.
   */
  public abstract boolean isForcePushAllowed(@NotNull Repo repo, Target target);

  public abstract boolean isSilentForcePushAllowed(@NotNull Target target);

  public @Nullable @Nls String getForcePushConfigurablePath() {
    return null;
  }

  public abstract void saveSilentForcePushTarget(@NotNull Target target);

  public boolean mayChangeTargetsSync() {
    return false;
  }
}

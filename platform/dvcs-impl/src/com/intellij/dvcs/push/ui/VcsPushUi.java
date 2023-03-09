// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.push.ui;

import com.intellij.dvcs.push.*;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.progress.Task;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

public interface VcsPushUi {
  DataKey<VcsPushUi> VCS_PUSH_DIALOG = DataKey.create("VcsPushDialog");

  /**
   * Runs {@link PrePushHandler pre-push handlers} under a modal progress,
   * if they succeed, schedules the given background task, and closes the push dialog.
   */
  @RequiresEdt
  void executeAfterRunningPrePushHandlers(@NotNull Task.Backgroundable activity);

  /**
   * Runs {@link PrePushHandler pre-push handlers} under a modal progress,
   * and after that starts push in a background task.
   */
  @RequiresEdt
  void push(boolean forcePush);

  /**
   * Returns push specifications (what is being pushed, where from and where to) collected from the push dialog,
   * grouped per PushSupports, which means in fact per-VCS.
   * <br/><br/>
   * Although no type specification is given, it is guaranteed that in the returned Map
   * each PushSupport is the one which corresponds to its RepoPushSpecs (all of which are of course of same types).
   */
  @NotNull
  Map<PushSupport<Repository, PushSource, PushTarget>, Collection<PushInfo>> getSelectedPushSpecs();

  /**
   * Checks if push is available right now for selected repositories and their targets.
   * <br/><br/>
   * E.g. push is not allowed, when a target is being edited, or when a repository without any remotes is selected.
   */
  @RequiresEdt
  boolean canPush();

  @RequiresEdt
  @ApiStatus.Experimental
  default boolean hasWarnings() {
    return false;
  }

  /**
   * Returns special push options, usually selected by user at the bottom of the push dialog.
   */
  @Nullable
  VcsPushOptionValue getAdditionalOptionValue(@NotNull PushSupport support);
}

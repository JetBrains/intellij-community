// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Interface for any checkers that should be called right before push operation starts.
 * All implemented handlers will be called on a background thread one by one (in unspecified order)
 * with cancelable progress indicator.
 */
public interface PrePushHandler {
  ExtensionPointName<PrePushHandler> EP_NAME = ExtensionPointName.create("com.intellij.prePushHandler");

  /**
   * Handler's decision of whether a push must be performed or canceled.
   */
  enum Result {
    /**
     * Push is allowed.
     */
    OK,
    /**
     * Push is not allowed. The Push Dialog won't be closed.
     */
    ABORT,
    /**
     * Push is not allowed. The Push Dialog will be closed immediately.
     */
    ABORT_AND_CLOSE
  }

  /**
   * Presentable name used in dialogs, UI, etc.
   *
   * @return presentable name of this handler
   */
  @NotNull
  @Nls(capitalization = Nls.Capitalization.Title)
  String getPresentableName();

  /**
   * Check synchronously if the push operation should be performed or canceled for specified {@link PushInfo}s.
   * <p>
   * Note: it is permissible for a handler to show its own modal dialogs with specifying
   * the supplied {@code indicator}'s {@link ProgressIndicator#getModalityState() modality} state.
   *
   * @param pushDetails information about the repository, source and target branches, and commits to be pushed
   * @param indicator   progress indicator to cancel this handler if necessary
   * @return handler's decision on whether the push must be performed or canceled
   */
  @CalledInAny
  @NotNull
  Result handle(@NotNull List<PushInfo> pushDetails, @NotNull ProgressIndicator indicator);
}

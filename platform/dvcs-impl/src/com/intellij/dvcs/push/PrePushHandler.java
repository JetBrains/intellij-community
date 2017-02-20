/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.dvcs.push;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Interface for any checkers that should be called right before push operation started.
 * All implemented handlers will be called on a background thread one by one (in unspecified order)
 * with cancelable progress indicator.
 */
public interface PrePushHandler {
  ExtensionPointName<PrePushHandler> EP_NAME = ExtensionPointName.create("com.intellij.prePushHandler");

  /**
   * Handler's decision of whether a push must be performed or canceled
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
   * Presentable name used in dialogs, UI, etc
   *
   * @return presentable name of this handler
   */
  @NotNull
  @Nls(capitalization = Nls.Capitalization.Title)
  String getPresentableName();

  /**
   * Check synchronously if the push operation should be performed or canceled for specified {@link PushInfo}s
   * <p>
   * Note: it is permissible for a handler to show it's own modal dialogs with specifying
   * the supplied {@code indicator}'s {@link ProgressIndicator#getModalityState() modality} state.
   *
   * @param pushDetails information about repository, source and target branches, and commits to be pushed
   * @param indicator progress indicator to cancel this handler if necessary
   * @return handler's decision on whether the push must be performed or canceled
   */
  @CalledInAny
  @NotNull
  Result handle(@NotNull List<PushInfo> pushDetails, @NotNull ProgressIndicator indicator);

}

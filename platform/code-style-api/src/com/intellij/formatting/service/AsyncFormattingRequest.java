// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.service;

import com.intellij.formatting.FormattingContext;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface AsyncFormattingRequest {
  @NotNull String getDocumentText();
  boolean canChangeWhitespaceOnly();
  @NotNull FormattingContext getContext();

  void onTextReady(@NotNull String updatedText);
  void onError(@NotNull @NlsContexts.NotificationTitle String title, @NotNull @NlsContexts.NotificationContent String message);

  interface CancellableRunnable extends Runnable {
    /**
     * Cancel the current runnable.
     * @return {@code true} if the runnable has been successfully cancelled, {@code false} otherwise.
     */
    boolean cancel();
  }
}

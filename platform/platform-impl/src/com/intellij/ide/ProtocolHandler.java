// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.NlsContexts.NotificationContent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public interface ProtocolHandler {
  ExtensionPointName<ProtocolHandler> EP_NAME = ExtensionPointName.create("com.intellij.protocolHandler");

  /**
   * This magic value allows a handler to tell the platform that it shouldn't display the welcome screen,
   * because the handler wants to take full care of the UI (not applicable if the UI is already visible).
   */
  String NO_UI_PLEASE = "6C831095-48C8-41D9-9753-076D30FEDABE";

  @NotNull String getScheme();
  @NotNull CompletableFuture<@Nullable @NotificationContent String> process(@NotNull String query, @NotNull ProgressIndicator indicator);

  @ApiStatus.Internal
  static @NotNull CompletableFuture<@Nullable @NotificationContent String> process(
    @NotNull String scheme,
    @NotNull String query,
    @NotNull ProgressIndicator indicator
  ) {
    try {
      for (ProtocolHandler handler : EP_NAME.getIterable()) {
        if (Objects.equals(scheme, handler.getScheme())) {
          return handler.process(query, indicator);
        }
      }

      return CompletableFuture.completedFuture(IdeBundle.message("ide.protocol.unsupported", scheme));
    }
    catch (Throwable t) {
      return CompletableFuture.failedFuture(t);
    }
  }
}

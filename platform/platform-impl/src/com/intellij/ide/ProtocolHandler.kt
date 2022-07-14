// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public interface ProtocolHandler {
  ExtensionPointName<ProtocolHandler> EP_NAME = ExtensionPointName.create("com.intellij.protocolHandler");

  /**
   * This exit code tells the platform that it shouldn't display the welcome screen,
   * because the handler wants to take full care of the UI (not applicable if URI is handled by an already running instance).
   */
  int PLEASE_NO_UI = -1;

  /**
   * This exit code tells the platform that it should shut itself down (not applicable if URI is handled by an already running instance).
   */
  int PLEASE_QUIT = -2;

  @NotNull String getScheme();
  @NotNull CompletableFuture<CliResult> process(@NotNull String query, @NotNull ProgressIndicator indicator);

  @ApiStatus.Internal
  static @NotNull CompletableFuture<CliResult> process(@NotNull String scheme, @NotNull String query, @NotNull ProgressIndicator indicator) {
    try {
      for (ProtocolHandler handler : EP_NAME.getIterable()) {
        if (Objects.equals(scheme, handler.getScheme())) {
          return handler.process(query, indicator);
        }
      }

      return CompletableFuture.completedFuture(new CliResult(0, IdeBundle.message("ide.protocol.unsupported", scheme)));
    }
    catch (Throwable t) {
      return CompletableFuture.failedFuture(t);
    }
  }
}

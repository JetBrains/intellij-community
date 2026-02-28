// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Internal API, unavailable to use in plugins.
 * <p>
 * For reporting errors, see [com.intellij.openapi.diagnostic.Logger.error] methods.
 * For receiving reports, register own [com.intellij.openapi.diagnostic.ErrorReportSubmitter].
 *
 * @deprecated use {@link MessagePoolAdvisor} instead.
 */
@ApiStatus.Internal
@Deprecated(forRemoval = true)
public interface MessagePoolListener extends EventListener {
  default boolean beforeEntryAdded(@NotNull AbstractMessage message) {
    return true;
  }

  void newEntryAdded();

  default void poolCleared() { }

  default void entryWasRead() { }
}

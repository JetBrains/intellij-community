// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Internal state of FileStatusMap.
 * Has two implementations:
 * {@link ClassicFileStatusMapState} does not store contexts
 * {@link MultiverseFileStatusMapState} has separate {@link FileStatus} for different contexts
 */
sealed interface FileStatusMapState permits ClassicFileStatusMapState, MultiverseFileStatusMapState {
  @NotNull FileStatus getOrCreateStatus(@NotNull Document document, @NotNull CodeInsightContext context);

  @Nullable FileStatus getStatusOrNull(@NotNull Document document, @NotNull CodeInsightContext context);

  @NotNull Collection<FileStatus> getFileStatuses(@NotNull Document document);

  boolean isEmpty();

  void clear();

  @NotNull String toString(@NotNull Document document);
}

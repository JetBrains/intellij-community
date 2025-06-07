// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.messages;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;

public final class FileDeletedEvent extends BuildMessage {
  private final Collection<String> filePaths;

  public FileDeletedEvent(@NotNull Collection<String> filePaths) {
    super("", Kind.INFO);

    this.filePaths = Collections.unmodifiableCollection(filePaths);
  }

  public @NotNull @Unmodifiable Collection<String> getFilePaths() {
    return filePaths;
  }
}

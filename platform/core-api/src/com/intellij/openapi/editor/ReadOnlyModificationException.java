// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ReadOnlyModificationException extends RuntimeException {
  private final Document myDocument;

  public ReadOnlyModificationException(@NotNull Document document, @Nullable String message) {
    super(message);
    myDocument = document;
  }

  public @NotNull Document getDocument() {
    return myDocument;
  }
}

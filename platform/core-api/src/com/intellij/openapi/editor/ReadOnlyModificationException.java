// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.core.CoreBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ReadOnlyModificationException extends RuntimeException {
  private final Document myDocument;

  /**
   * @deprecated use {@link ReadOnlyModificationException#ReadOnlyModificationException(Document, String)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public ReadOnlyModificationException(@NotNull Document document) {
    this(document, CoreBundle.message("attempt.to.modify.read.only.document.error.message"));
  }

  public ReadOnlyModificationException(@NotNull Document document, @Nullable String message) {
    super(message);
    myDocument = document;
  }

  @NotNull
  public Document getDocument() {
    return myDocument;
  }
}

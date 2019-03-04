// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import org.jetbrains.annotations.NotNull;

public final class ReadOnlyModificationException extends RuntimeException {
  private final Document myDocument;

  public ReadOnlyModificationException(@NotNull Document document) {
    super(EditorBundle.message("attempt.to.modify.read.only.document.error.message"));
    myDocument = document;
  }

  @NotNull
  public Document getDocument() {
    return myDocument;
  }
}

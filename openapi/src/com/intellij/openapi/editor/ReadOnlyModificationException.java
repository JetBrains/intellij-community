/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor;

public class ReadOnlyModificationException extends RuntimeException {
  private final Document myDocument;

  public ReadOnlyModificationException(Document document) {
    super("Attempt to modify read-only document");
    myDocument = document;
  }

  public Document getDocument() {
    return myDocument;
  }
}

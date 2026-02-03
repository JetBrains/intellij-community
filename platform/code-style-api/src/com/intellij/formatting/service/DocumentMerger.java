// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.service;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * Merges the current document with the new text content.
 */
public interface DocumentMerger {

  ExtensionPointName<DocumentMerger> EP_NAME = ExtensionPointName.create("com.intellij.documentMerger");

  /**
   * Merge the current document with the new content.
   *
   * @param document The document to be updated.
   * @param newText  The new text to be merged with the document.
   * @return True on success, false if the changes can't be merged.
   */
  boolean updateDocument(@NotNull Document document, @NotNull String newText);
}

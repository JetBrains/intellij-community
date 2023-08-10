// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.undo;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * Do not implement this directly. Use DocumentReferenceManager instead.
 */
public interface DocumentReference {
  DocumentReference[] EMPTY_ARRAY = new DocumentReference[0];

  @Nullable
  Document getDocument();

  @Nullable
  VirtualFile getFile();
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public interface FileContent extends IndexedFile {
  /**
   * For binary files this is exactly the bytes from the file.
   * <p>
   * For text files content is normalized: new lines are converted to "\n", if the file contains BOM (e.g. FF FE), it will be dropped.
   * <p>
   * The following invariant holds true:
   * <pre>
   *   getContent() == getContentAsText().toString().getBytes(charset)
   * </pre>
   * where {@code charset} is either detected, or forced (e.g. by FileType)
   *
   * @return normalized contents of the file as bytes
   */
  byte @NotNull [] getContent();

  /**
   * For text files content is normalized: new lines are converted to "\n", if the file contains BOM (e.g. FF FE), it will be dropped.
   *  <p>
   * Throws exception for binary files
   *
   * @return normalized contents of the file as CharSequence
   */
  @NotNull CharSequence getContentAsText();

  @NotNull PsiFile getPsiFile();
}

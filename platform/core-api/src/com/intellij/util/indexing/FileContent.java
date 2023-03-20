// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public interface FileContent extends IndexedFile {
  byte @NotNull [] getContent();

  @NotNull CharSequence getContentAsText();

  @NotNull PsiFile getPsiFile();
}

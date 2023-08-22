// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.fileSet;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Used to disable formatting for any file programmatically
 */
public class AnyFileDescriptor implements FileSetDescriptor {
  public final static String TYPE = "anyFile";

  @Override
  public boolean matches(@NotNull PsiFile psiFile) {
    return true;
  }

  @Override
  public @NotNull String getType() {
    return TYPE;
  }

  @Override
  public @Nullable String getPattern() {
    return null;
  }

  @Override
  public void setPattern(@Nullable String pattern) {
    throw new UnsupportedOperationException();
  }
}

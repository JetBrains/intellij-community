// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.lang.annotation;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class AnnotationSession extends UserDataHolderBase {
  private static final Key<TextRange> VR = Key.create("VR");
  private final PsiFile myFile;

  @ApiStatus.Internal
  public AnnotationSession(@NotNull PsiFile file) {
    myFile = file;
  }

  public @NotNull PsiFile getFile() {
    return myFile;
  }

  /**
   * @return text range (inside the {@link #getFile()}) for which annotators should be calculated sooner than for the remaining range in the file.
   * Usually this priority range corresponds to the range visible on screen.
   */
  public @NotNull TextRange getPriorityRange() {
    return Objects.requireNonNullElseGet(getUserData(VR), ()->getFile().getTextRange());
  }

  @ApiStatus.Internal
  public void setVR(@NotNull TextRange range) {
    putUserData(VR, range);
  }
}

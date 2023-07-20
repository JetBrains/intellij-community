// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.lang.annotation;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public /*abstract*/ class AnnotationSession implements UserDataHolder {
  private final PsiFile myFile;

  /**
   * FOR MAINTAINING BINARY COMPATIBILITY ONLY.
   * @deprecated Do not instantiate this class directly, use {@link AnnotationHolder#getCurrentAnnotationSession()} instead
   */
  @Deprecated
  public AnnotationSession(PsiFile file) {
    myFile = file;
  }

  @NotNull
  public /*abstract*/ PsiFile getFile() {
    return myFile;
  }

  /**
   * @return text range (inside the {@link #getFile()}) for which annotators should be calculated sooner than for the remaining range in the file.
   * Usually this priority range corresponds to the range visible on screen.
   */
  @NotNull
  public /*abstract*/ TextRange getPriorityRange() {
    return getFile().getTextRange();
  }

  @Override
  public /*abstract*/ <T> @Nullable T getUserData(@NotNull Key<T> key) {
    return null;
  }

  @Override
  public /*abstract*/ <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
  }

  /**
   * Minimum Severity is a hint that suggests what highlighting level is requested from this annotator.
   * For example, "code smell detector" called on VCS commit might request error/warnings only and ignore INFORMATION.
   * Knowing that, the corresponding annotator might react by skipping part of (the potentially expensive) work.
   * For example, spellchecker plugin might want to skip running itself altogether if minimumSeverity = WARNING.
   * This hint is only a hint, meaning that the annotator might choose to ignore it.
   */
  public /*abstract*/ HighlightSeverity getMinimumSeverity() {
    return null;
  }
}

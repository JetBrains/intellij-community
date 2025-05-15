// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.lang.annotation;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class holds a context during running {@link Annotator},
 * which can access it via {@link AnnotationHolder#getCurrentAnnotationSession()}.
 * This class is not intended for instantiating manually, and all its methods are effectively abstract (not marked as such yet due to compatibility reasons).
 */
@ApiStatus.NonExtendable
public /*abstract*/ class AnnotationSession implements UserDataHolder {
  private final PsiFile myPsiFile;

  /**
   * FOR MAINTAINING BINARY COMPATIBILITY ONLY.
   * @deprecated Do not instantiate this class directly, use {@link AnnotationHolder#getCurrentAnnotationSession()} instead
   */
  @Deprecated
  public AnnotationSession(@NotNull PsiFile psiFile) {
    myPsiFile = psiFile;
  }

  public @NotNull /*abstract*/ PsiFile getFile() {
    return myPsiFile;
  }

  /**
   * @return text range (inside the {@link #getFile()}) for which annotators should be calculated sooner than for the remaining range in the file.
   * Usually this priority range corresponds to the range visible on screen.
   */
  public @NotNull /*abstract*/ TextRange getPriorityRange() {
    return getFile().getTextRange();
  }

  /**
   * @return text range (inside the {@link #getFile()}) for which annotators should be calculated.
   * The highlighting range is the subset of psi file for which the highlighting was requested.
   * For example, when the user typed inside a function, the highlighting range is the containing code block.
   * The {@link #getPriorityRange()} on the other hand, is a visible part of the file for which the highlighting should be run first.
   * In the example above it could be a part of the function body and a couple of members below, visible onscreen.
   * It is guaranteed that no PSI elements outside this range are going to be analyzed in this session.
   * This method could be used as an optimization to reduce the analysis range.
   */
  @ApiStatus.Experimental
  @ApiStatus.Internal
  public @NotNull TextRange getHighlightRange() {
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
   * @return Minimum Severity (or null if not specified) which is a hint that suggests what highlighting level is requested
   * from this annotator in this specific annotation session.
   * For example, "code smell detector" called on VCS commit might request ERROR/WARNING only and ignore INFORMATION annotations.
   * Knowing this minimum requested severity, the corresponding annotator might react by skipping part of the (potentially expensive) work.
   * For example, spellchecker plugin might want to skip running itself altogether if minimumSeverity = WARNING.
   * This hint is only a hint, meaning that the annotator might choose to ignore it.
   */
  public /*abstract*/ @Nullable HighlightSeverity getMinimumSeverity() {
    return null;
  }
}

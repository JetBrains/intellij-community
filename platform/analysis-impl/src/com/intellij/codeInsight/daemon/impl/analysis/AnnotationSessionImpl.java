// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

@ApiStatus.Internal
public final class AnnotationSessionImpl extends AnnotationSession {
  private final UserDataHolder myDataHolder = new UserDataHolderBase();
  private volatile TextRange myPriorityRange;
  private volatile HighlightSeverity myMinimumSeverity;

  @ApiStatus.Internal
  AnnotationSessionImpl(@NotNull PsiFile file) {
    super(file);
  }

  /**
   * @return text range (inside the {@link #getFile()}) for which annotators should be calculated sooner than for the remaining range in the file.
   * Usually this priority range corresponds to the range visible on screen.
   */
  public @NotNull TextRange getPriorityRange() {
    return Objects.requireNonNullElseGet(myPriorityRange, ()->getFile().getTextRange());
  }

  @ApiStatus.Internal
  public void setVR(@NotNull TextRange range) {
    myPriorityRange = range;
  }

  @Override
  public <T> @Nullable T getUserData(@NotNull Key<T> key) {
    return myDataHolder.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myDataHolder.putUserData(key, value);
  }

  public void setMinimumSeverity(@Nullable HighlightSeverity severity) {
    myMinimumSeverity = severity;
  }

  @Override
  public HighlightSeverity getMinimumSeverity() {
    return myMinimumSeverity;
  }

  public static <T> T computeWithSession(@NotNull PsiFile psiFile, boolean batchMode, @NotNull Function<? super AnnotationHolderImpl, T> runnable) {
    AnnotationHolderImpl holder = new AnnotationHolderImpl(new AnnotationSessionImpl(psiFile), batchMode);
    return runnable.apply(holder);
  }
}

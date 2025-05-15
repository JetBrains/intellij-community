// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.annotation.*;
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
  private volatile TextRange myHighlightRange;
  private volatile HighlightSeverity myMinimumSeverity;

  private AnnotationSessionImpl(@NotNull PsiFile psiFile) {
    super(psiFile);
  }

  @ApiStatus.Internal
  public static @NotNull AnnotationSession create(@NotNull PsiFile psiFile) {
    return new AnnotationSessionImpl(psiFile);
  }

  /**
   * @return text range (inside the {@link #getFile()}) for which annotators should be calculated sooner than for the remaining range in the file.
   * Usually this priority range corresponds to the range visible on screen.
   */
  @Override
  public @NotNull TextRange getPriorityRange() {
    return Objects.requireNonNullElseGet(myPriorityRange, ()->getFile().getTextRange());
  }

  @Override
  public @NotNull TextRange getHighlightRange() {
    return Objects.requireNonNullElseGet(myHighlightRange, ()->getFile().getTextRange());
  }

  @ApiStatus.Internal
  void setVR(@NotNull TextRange priorityRange, @NotNull TextRange highlightRange) {
    myPriorityRange = priorityRange;
    myHighlightRange = highlightRange;
  }

  @Override
  public <T> @Nullable T getUserData(@NotNull Key<T> key) {
    return myDataHolder.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myDataHolder.putUserData(key, value);
  }

  void setMinimumSeverity(@Nullable HighlightSeverity severity) {
    myMinimumSeverity = severity;
  }

  @Override
  public HighlightSeverity getMinimumSeverity() {
    return myMinimumSeverity;
  }

  @ApiStatus.Internal
  public static <T> T computeWithSession(@NotNull PsiFile psiFile, boolean batchMode, @NotNull Annotator annotator, @NotNull Function<? super AnnotationHolder, T> runnable) {
    return computeWithSession(batchMode, annotator, new AnnotationSessionImpl(psiFile), runnable);
  }

  @ApiStatus.Internal
  public static <T> T computeWithSession(@NotNull PsiFile psiFile, boolean batchMode, @NotNull ExternalAnnotator<?,?> annotator, @NotNull Function<? super AnnotationHolder, T> runnable) {
    return computeWithSession(batchMode, annotator, new AnnotationSessionImpl(psiFile), runnable);
  }

  static <T> T computeWithSession(boolean batchMode,
                                  @NotNull Object annotator,
                                  @NotNull AnnotationSession session,
                                  @NotNull Function<? super AnnotationHolderImpl, T> runnable) {
    AnnotationHolderImpl holder = new AnnotationHolderImpl(annotator, session, batchMode);
    return runnable.apply(holder);
  }
}

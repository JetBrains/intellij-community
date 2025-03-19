// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Do not use, for binary compatibility only
 */
@Deprecated(forRemoval = true)
@ApiStatus.Internal
public final class AnnotationSessionImpl {
  @ApiStatus.Internal
  public static <T> T computeWithSession(@NotNull PsiFile psiFile, boolean batchMode, @NotNull Function<? super AnnotationHolderImpl, T> runnable) {
    AnnotationSession session = com.intellij.codeInsight.daemon.impl.AnnotationSessionImpl.create(psiFile);
    AnnotationHolderImpl holder = new AnnotationHolderImpl(session, batchMode);
    return runnable.apply(holder);
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.psi.PsiClass;
import com.intellij.psi.util.JavaPsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.RetentionPolicy;

/**
 * @deprecated the functionality was moved to other places
 */
@Deprecated
public final class AnnotationsHighlightUtil {
  /**
   * @deprecated use {@link JavaPsiAnnotationUtil#getRetentionPolicy(PsiClass)}
   */
  @Deprecated
  public static @Nullable RetentionPolicy getRetentionPolicy(@NotNull PsiClass annotation) {
    return JavaPsiAnnotationUtil.getRetentionPolicy(annotation);
  }
}

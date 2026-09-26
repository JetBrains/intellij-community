// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated all methods are deprecated
 */
@Deprecated
public final class HighlightClassUtil {

  /**
   * @deprecated use {@link PsiTypesUtil#isRestrictedIdentifier(String, LanguageLevel)}
   */
  @Deprecated
  public static boolean isRestrictedIdentifier(@Nullable String typeName, @NotNull LanguageLevel level) {
    return PsiTypesUtil.isRestrictedIdentifier(typeName, level);
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.core.CoreDeprecatedMessagesBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated use properties from other bundles ({@link com.intellij.lang.LangBundle}, {@link com.intellij.core.CoreBundle},
 * {@link com.intellij.util.indexing.IndexingBundle}) instead
 */
@Deprecated
public final class PsiBundle {
  public static @NotNull @Nls String message(@NotNull String key, Object @NotNull ... params) {
    return CoreDeprecatedMessagesBundle.message(key, params);
  }
}
// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  public static @Nls String message(@NotNull String key, Object @NotNull ... params) {
    return CoreDeprecatedMessagesBundle.message(key, params);
  }
}
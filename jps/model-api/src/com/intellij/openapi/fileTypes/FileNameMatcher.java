// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.fileTypes;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public interface FileNameMatcher {
  /**
   * @deprecated use {@link #acceptsCharSequence(CharSequence)}
   */
  @Deprecated
  default boolean accept(@NonNls @NotNull String fileName) {
    return acceptsCharSequence(fileName);
  }

  /**
   * This method must be overridden in specific matchers, it's default only for compatibility reasons.
   * @return whether the given file name is accepted by this matcher.
   */
  default boolean acceptsCharSequence(@NonNls @NotNull CharSequence fileName) {
    return accept(fileName.toString());
  }

  @NonNls @NotNull
  String getPresentableString();
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spi;

import com.intellij.lang.Commenter;
import org.jetbrains.annotations.Nullable;

public final class SPICommenter implements Commenter {
  @Override
  public @Nullable String getLineCommentPrefix() {
    return "#";
  }

  @Override
  public @Nullable String getBlockCommentPrefix() {
    return null;
  }

  @Override
  public @Nullable String getBlockCommentSuffix() {
    return null;
  }

  @Override
  public @Nullable String getCommentedBlockCommentPrefix() {
    return null;
  }

  @Override
  public @Nullable String getCommentedBlockCommentSuffix() {
    return null;
  }
}

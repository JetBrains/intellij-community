// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public enum VFileProperty {
  HIDDEN, SPECIAL, SYMLINK;

  public @NotNull String getName() {
    return StringUtil.toLowerCase(toString());
  }
}

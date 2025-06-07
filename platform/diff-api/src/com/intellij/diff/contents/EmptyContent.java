// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.contents;

import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.Nullable;

/**
 * Represents empty content
 * <p/>
 * ex: 'Before' state for new file
 */
public class EmptyContent extends DiffContentBase {
  @Override
  public @Nullable FileType getContentType() {
    return null;
  }
}

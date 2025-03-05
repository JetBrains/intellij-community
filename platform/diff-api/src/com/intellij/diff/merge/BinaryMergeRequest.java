// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge;

import com.intellij.diff.contents.FileContent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class BinaryMergeRequest extends ThreesideMergeRequest {
  public abstract @NotNull List<byte[]> getByteContents();

  @Override
  public abstract @NotNull FileContent getOutputContent();
}

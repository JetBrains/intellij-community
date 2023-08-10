// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.fileTypes.ex;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class FakeFileType implements FileTypeIdentifiableByVirtualFile {
  protected FakeFileType() {
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "fakeExtension";
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public boolean isBinary() {
    return true;
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }
}

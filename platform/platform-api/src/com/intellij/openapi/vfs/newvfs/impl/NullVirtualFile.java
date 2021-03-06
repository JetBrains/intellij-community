// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.impl;

public final class NullVirtualFile extends StubVirtualFile {
  public static final NullVirtualFile INSTANCE = new NullVirtualFile();

  private NullVirtualFile() {}

  @Override
  public String toString() {
    return "VirtualFile.NULL_OBJECT";
  }
}
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.impl;

/**
 * @author max
 */
public class NullVirtualFile extends StubVirtualFile {
  public static final NullVirtualFile INSTANCE = new NullVirtualFile();

  private NullVirtualFile() {}

  @Override
  public String toString() {
    return "VirtualFile.NULL_OBJECT";
  }
}
/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.impl;

import org.jetbrains.annotations.NonNls;

public class NullVirtualFile extends StubVirtualFile {
  public static final NullVirtualFile INSTANCE = new NullVirtualFile();

  private NullVirtualFile() {}

  @NonNls
  public String toString() {
    return "VirtualFile.NULL_OBJECT";
  }
}
/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vfs;

public interface VirtualFileFilter {
  boolean accept(VirtualFile file);

  VirtualFileFilter ALL = new VirtualFileFilter() {
    public boolean accept(VirtualFile file) {
      return true;
    }
  };

  VirtualFileFilter NONE = new VirtualFileFilter() {
    public boolean accept(VirtualFile file) {
      return false;
    }
  };
}
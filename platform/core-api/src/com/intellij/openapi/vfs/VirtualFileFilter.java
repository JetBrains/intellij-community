// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface VirtualFileFilter {
  boolean accept(@NotNull VirtualFile file);

  VirtualFileFilter ALL = new VirtualFileFilter() {
    @Override
    public boolean accept(@NotNull VirtualFile file) {
      return true;
    }

    @Override
    public String toString() {
      return "ALL";
    }
  };

  VirtualFileFilter NONE = new VirtualFileFilter() {
    @Override
    public boolean accept(@NotNull VirtualFile file) {
      return false;
    }

    @Override
    public String toString() {
      return "NONE";
    }
  };

  default @NotNull VirtualFileFilter and(@NotNull VirtualFileFilter other) {
    return file -> accept(file) && other.accept(file);
  }
}
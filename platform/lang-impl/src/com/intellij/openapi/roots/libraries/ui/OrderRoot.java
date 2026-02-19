// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries.ui;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public final class OrderRoot {
  private final VirtualFile myFile;
  private final OrderRootType myType;
  private final boolean myJarDirectory;

  public OrderRoot(@NotNull VirtualFile file, @NotNull OrderRootType type) {
    this(file, type, false);
  }

  public OrderRoot(@NotNull VirtualFile file, @NotNull OrderRootType type, boolean jarDirectory) {
    myFile = file;
    myType = type;
    myJarDirectory = jarDirectory;
  }

  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  public @NotNull OrderRootType getType() {
    return myType;
  }

  public boolean isJarDirectory() {
    return myJarDirectory;
  }
}

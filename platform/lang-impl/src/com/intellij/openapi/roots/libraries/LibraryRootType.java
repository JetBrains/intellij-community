// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.roots.OrderRootType;
import org.jetbrains.annotations.NotNull;

public final class LibraryRootType {
  private final OrderRootType myType;
  private final boolean myJarDirectory;

  public LibraryRootType(@NotNull OrderRootType type, boolean jarDirectory) {
    myType = type;
    myJarDirectory = jarDirectory;
  }

  public @NotNull OrderRootType getType() {
    return myType;
  }

  public boolean isJarDirectory() {
    return myJarDirectory;
  }
}

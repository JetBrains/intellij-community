// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries.ui;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.LibraryRootType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class DetectedLibraryRoot {
  private final VirtualFile myFile;
  private final List<LibraryRootType> myTypes;

  public DetectedLibraryRoot(@NotNull VirtualFile file, @NotNull OrderRootType rootType, boolean jarDirectory) {
    this(file, Collections.singletonList(new LibraryRootType(rootType, jarDirectory)));
  }

  public DetectedLibraryRoot(@NotNull VirtualFile file, @NotNull List<LibraryRootType> types) {
    myFile = file;
    myTypes = types;
  }

  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  public @NotNull List<LibraryRootType> getTypes() {
    return myTypes;
  }
}

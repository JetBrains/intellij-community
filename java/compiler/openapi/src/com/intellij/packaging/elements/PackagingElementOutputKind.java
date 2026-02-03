// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.elements;

import org.jetbrains.annotations.NotNull;

public class PackagingElementOutputKind {
  public static final @NotNull PackagingElementOutputKind DIRECTORIES_WITH_CLASSES = new PackagingElementOutputKind(true, false);
  public static final @NotNull PackagingElementOutputKind JAR_FILES = new PackagingElementOutputKind(false, true);
  public static final @NotNull PackagingElementOutputKind OTHER = new PackagingElementOutputKind(false, false);

  private final boolean myContainsDirectoriesWithClasses;
  private final boolean myContainsJarFiles;

  public PackagingElementOutputKind(boolean containsDirectoriesWithClasses, boolean containsJarFiles) {
    myContainsDirectoriesWithClasses = containsDirectoriesWithClasses;
    myContainsJarFiles = containsJarFiles;
  }

  public boolean containsDirectoriesWithClasses() {
    return myContainsDirectoriesWithClasses;
  }

  public boolean containsJarFiles() {
    return myContainsJarFiles;
  }
}

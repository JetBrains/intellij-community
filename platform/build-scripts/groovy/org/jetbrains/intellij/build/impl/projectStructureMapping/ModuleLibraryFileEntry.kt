// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.projectStructureMapping;

import java.nio.file.Path;

/**
 * Represents a file in module-level library
 */
public final class ModuleLibraryFileEntry extends DistributionFileEntry implements DistributionFileEntry.LibraryFileEntry {
  public ModuleLibraryFileEntry(Path path, String moduleName, Path libraryFile, int size) {
    super(path, "module-library-file");

    this.moduleName = moduleName;
    this.libraryFile = libraryFile;
    this.size = size;
  }

  public final String getModuleName() {
    return moduleName;
  }

  public final Path getLibraryFile() {
    return libraryFile;
  }

  public final int getSize() {
    return size;
  }

  private final String moduleName;
  private final Path libraryFile;
  private final int size;
}

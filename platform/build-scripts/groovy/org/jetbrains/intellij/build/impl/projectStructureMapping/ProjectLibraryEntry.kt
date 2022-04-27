// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.projectStructureMapping;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.intellij.build.impl.ProjectLibraryData;

import java.nio.file.Path;

/**
 * Represents a project-level library
 */
public final class ProjectLibraryEntry extends DistributionFileEntry implements DistributionFileEntry.LibraryFileEntry {
  public ProjectLibraryEntry(Path path, @NotNull ProjectLibraryData data, Path libraryFile, int size) {
    super(path, "project-library");

    this.libraryFile = libraryFile;
    this.data = data;
    this.size = size;
  }

  @Override
  public String toString() {
    return "ProjectLibraryEntry(" + "data='" + data + "\'" + ", libraryFile=" + libraryFile + ", size=" + size + ")";
  }

  public final ProjectLibraryData getData() {
    return data;
  }

  public final Path getLibraryFile() {
    return libraryFile;
  }

  public final int getSize() {
    return size;
  }

  private final ProjectLibraryData data;
  private final Path libraryFile;
  private final int size;
}

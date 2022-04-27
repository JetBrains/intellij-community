// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.projectStructureMapping;

import java.nio.file.Path;

/**
 * Base class for entries in {@link ProjectStructureMapping}.
 */
public abstract class DistributionFileEntry {
  public DistributionFileEntry(Path path, String type) {
    this.path = path;
    this.type = type;
  }

  public final Path getPath() {
    return path;
  }

  public final String getType() {
    return type;
  }

  /**
   * Path to a file in IDE distribution
   */
  private final Path path;
  /**
   * Type of the element in the project configuration which was copied to {@link #path}
   */
  private final String type;

  public static interface LibraryFileEntry {
    public abstract Path getLibraryFile();

    public abstract int getSize();
  }
}

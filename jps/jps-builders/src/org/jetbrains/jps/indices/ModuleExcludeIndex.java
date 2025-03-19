// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.indices;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Allows to check whether a particular file is in the content or under an exclude root of a module.
 */
public interface ModuleExcludeIndex {
  /**
   * Returns {@code true} if the specified file is located under project roots but the file itself or one of its parent directories is
   * excluded from the corresponding module.
   */
  boolean isExcluded(File file);

  /**
   * Returns {@code true} if the specified file is located under content roots of the module but the file itself or one of its parent
   * directories is excluded.
   */
  boolean isExcludedFromModule(@NotNull File file, @NotNull JpsModule module);

  /**
   * Returns the list of exclude roots for a specified module.
   * Note that patterns may exclude files from the module content, so
   * it makes sense to use this method together with {@link #getModuleFileFilterHonorExclusionPatterns}.
   */
  @Unmodifiable @NotNull Collection<@NotNull Path> getModuleExcludes(@NotNull JpsModule module);

  /**
   * Returns filter which accepts files located under module content roots and aren't excluded by exclusion patterns for that module. For
   * performance reasons, this method doesn't take into account {@link #getModuleExcludes excluded roots}.
   */
  @NotNull FileFilter getModuleFileFilterHonorExclusionPatterns(@NotNull JpsModule module);

  /**
   * Checks if the specified file is under the content of any module in the project and not under an exclude root.
   */
  boolean isInContent(@NotNull File file);
}

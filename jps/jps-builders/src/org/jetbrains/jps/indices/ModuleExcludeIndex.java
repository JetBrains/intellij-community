/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.indices;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.FileFilter;
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
  boolean isExcludedFromModule(File file, JpsModule module);

  /**
   * Returns the list of exclude roots for a specified module. Note that files may be excluded from the module content by patterns, so
   * it makes sense to used this method together with {@link #getModuleFileFilterHonorExclusionPatterns}.
   */
  Collection<File> getModuleExcludes(JpsModule module);

  /**
   * Returns filter which accepts files located under module content roots and aren't excluded by exclusion patterns for that module. For
   * performance reasons, this method doesn't take into account {@link #getModuleExcludes excluded roots}.
   */
  @NotNull FileFilter getModuleFileFilterHonorExclusionPatterns(@NotNull JpsModule module);

  /**
   * Checks if the specified file is under the content of any module in the project and not under an exclude root.
   */
  boolean isInContent(File file);
}

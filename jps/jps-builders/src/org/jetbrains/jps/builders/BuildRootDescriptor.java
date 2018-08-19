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
package org.jetbrains.jps.builders;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.cmdline.ProjectDescriptor;

import java.io.File;
import java.io.FileFilter;
import java.util.Collections;
import java.util.Set;

/**
 * Describes a source root of a build target.
 *
 * @author nik
 */
public abstract class BuildRootDescriptor {
  /**
   * Returns the serializable ID of the root, used for writing caches. May return simply the file path.
   */
  public abstract String getRootId();

  /**
   * Returns the directory of the source root.
   */
  public abstract File getRootFile();

  /**
   * Returns the target to which this source root belongs.
   */
  public abstract BuildTarget<?> getTarget();

  /**
   * @deprecated override {@link #createFileFilter()} instead
   */
  @Deprecated
  public FileFilter createFileFilter(@NotNull ProjectDescriptor descriptor) {
    return null;
  }

  /**
   * Creates the file filter specifying which files under the specified root belong to this build target.
   */
  @NotNull
  public FileFilter createFileFilter() {
    return FileUtilRt.ALL_FILES;
  }

  /**
   * @return the set of excluded directories under this root.
   */
  @NotNull
  public Set<File> getExcludedRoots() {
    return Collections.emptySet();
  }

  public boolean isGenerated() {
    return false;
  }

  public boolean canUseFileCache() {
    return false;
  }
}

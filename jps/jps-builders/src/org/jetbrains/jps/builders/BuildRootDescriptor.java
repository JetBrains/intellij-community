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
 * @author nik
 */
public abstract class BuildRootDescriptor {
  public abstract String getRootId();

  public abstract File getRootFile();

  public abstract BuildTarget<?> getTarget();

  /**
   * @deprecated override {@link #createFileFilter()} instead
   */
  public FileFilter createFileFilter(@NotNull ProjectDescriptor descriptor) {
    return null;
  }

  @NotNull
  public FileFilter createFileFilter() {
    return FileUtilRt.ALL_FILES;
  }

  /**
   * @return the set of excluded directories under this root
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

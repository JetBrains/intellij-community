/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.Set;

public abstract class JavaModuleIndex {
  /**
   * Returns a path to a module descriptor (module-info.java file) for the given module,
   * or {@code null} when there is no descriptor.
   */
  public abstract @Nullable File getModuleInfoFile(@NotNull JpsModule module);

  /**
   * Returns {@code true} when at least one module in the chunk has a module descriptor (module-info.java file).
   */
  public abstract boolean hasJavaModules(@NotNull Set<JpsModule> chunk);
}
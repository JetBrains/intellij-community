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

import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.Collection;

/**
 * Allows to check whether a particular file is in the content or under an exclude root of a module.
 *
 * @author nik
 */
public interface ModuleExcludeIndex {
  /**
   * Checks if the specified file is under an exclude root of a module.
   */
  boolean isExcluded(File file);

  /**
   * Returns the list of exclude roots for a specified module.
   */
  Collection<File> getModuleExcludes(JpsModule module);

  /**
   * Checks if the specified file is under the content of any module in the project and not under an exclude root.
   */
  boolean isInContent(File file);
}

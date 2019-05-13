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
package org.jetbrains.jps.model.java.compiler;

import java.io.File;
import java.util.Set;

/**
 * @author nik
 */
public interface JpsCompilerExcludes {
  void addExcludedFile(String url);

  void addExcludedDirectory(String url, boolean recursively);

  /**
   * @return {@code true} if {@code file} is explicitly excluded or located under excluded directory
   */
  boolean isExcluded(File file);

  /**
   * @return set of explicitly excluded files
   */
  Set<File> getExcludedFiles();

  Set<File> getExcludedDirectories();

  Set<File> getRecursivelyExcludedDirectories();
}

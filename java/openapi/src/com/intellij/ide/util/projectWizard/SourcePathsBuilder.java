/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public interface SourcePathsBuilder {

  @Nullable
  String getContentEntryPath();

  void setContentEntryPath(String moduleRootPath);

  /**
   * Pair: {@code first=sourceRoot, second=packagePrefix}.
   */
  List<Pair<String, String>> getSourcePaths() throws ConfigurationException;

  /**
   * Pair: {@code first=sourceRoot, second=packagePrefix}.
   */
  void setSourcePaths(List<Pair<String, String>> sourcePaths);

  /**
   * Pair: {@code first=sourceRoot, second=packagePrefix}.
   */
  void addSourcePath(Pair<String, String> sourcePathInfo);
}

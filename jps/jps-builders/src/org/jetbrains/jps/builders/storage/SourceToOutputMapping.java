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
package org.jetbrains.jps.builders.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author nik
 */
public interface SourceToOutputMapping {
  void setOutputs(@NotNull String srcPath, @NotNull Collection<String> outputs) throws IOException;

  void setOutput(@NotNull String srcPath, @NotNull String outputPath) throws IOException;

  void appendOutput(@NotNull String srcPath, @NotNull String outputPath) throws IOException;


  void remove(@NotNull String srcPath) throws IOException;

  void removeOutput(@NotNull String sourcePath, @NotNull String outputPath) throws IOException;


  @NotNull
  Collection<String> getSources() throws IOException;

  @Nullable
  Collection<String> getOutputs(@NotNull String srcPath) throws IOException;

  @NotNull
  Iterator<String> getSourcesIterator() throws IOException;
}

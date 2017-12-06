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
package org.jetbrains.jps.incremental.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author Eugene Zhuravlev
 */
public class SourceToOutputMappingImpl implements SourceToOutputMapping {
  private final OneToManyPathsMapping myMapping;

  public SourceToOutputMappingImpl(File storePath) throws IOException {
    myMapping = new OneToManyPathsMapping(storePath);
  }

  @Override
  public void setOutputs(@NotNull String srcPath, @NotNull Collection<String> outputs) throws IOException {
    myMapping.update(srcPath, outputs);
  }

  @Override
  public void setOutput(@NotNull String srcPath, @NotNull String outputPath) throws IOException {
    myMapping.update(srcPath, outputPath);
  }

  @Override
  public void appendOutput(@NotNull String srcPath, @NotNull String outputPath) throws IOException {
    myMapping.appendData(srcPath, outputPath);
  }

  @Override
  public void remove(@NotNull String srcPath) throws IOException {
    myMapping.remove(srcPath);
  }

  @Override
  public void removeOutput(@NotNull String sourcePath, @NotNull String outputPath) throws IOException {
    myMapping.removeData(sourcePath, outputPath);
  }

  @NotNull
  @Override
  public Collection<String> getSources() throws IOException {
    return myMapping.getKeys();
  }

  @Nullable
  @Override
  public Collection<String> getOutputs(@NotNull String srcPath) throws IOException {
    return myMapping.getState(srcPath);
  }

  @NotNull
  @Override
  public Iterator<String> getSourcesIterator() throws IOException {
    return myMapping.getKeysIterator();
  }

  public void flush(boolean memoryCachesOnly) {
    myMapping.flush(memoryCachesOnly);
  }

  public void close() throws IOException {
    myMapping.close();
  }

  public void clean() throws IOException {
    myMapping.clean();
  }
}

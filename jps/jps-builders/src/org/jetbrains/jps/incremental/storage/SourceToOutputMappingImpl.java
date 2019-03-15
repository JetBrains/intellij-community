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

import com.intellij.util.Function;
import com.intellij.util.containers.JBIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

import static com.intellij.util.containers.ContainerUtil.map;

/**
 * @author Eugene Zhuravlev
 */
public class SourceToOutputMappingImpl implements SourceToOutputMapping {
  private final OneToManyPathsMapping myMapping;
  private final MaybeRelativizer myRelativizer;

  public SourceToOutputMappingImpl(File storePath, MaybeRelativizer relativizer) throws IOException {
    myMapping = new OneToManyPathsMapping(storePath);
    myRelativizer = relativizer;
  }

  @Override
  public void setOutputs(@NotNull String srcPath, @NotNull Collection<String> outputs) throws IOException {
    myMapping.update(myRelativizer.toRelative(srcPath), map(outputs, s -> myRelativizer.toRelative(s)));
  }

  @Override
  public void setOutput(@NotNull String srcPath, @NotNull String outputPath) throws IOException {
    myMapping.update(myRelativizer.toRelative(srcPath), myRelativizer.toRelative(outputPath));
  }

  @Override
  public void appendOutput(@NotNull String srcPath, @NotNull String outputPath) throws IOException {
    myMapping.appendData(myRelativizer.toRelative(srcPath), myRelativizer.toRelative(outputPath));
  }

  @Override
  public void remove(@NotNull String srcPath) throws IOException {
    myMapping.remove(myRelativizer.toRelative(srcPath));
  }

  @Override
  public void removeOutput(@NotNull String srcPath, @NotNull String outputPath) throws IOException {
    myMapping.removeData(myRelativizer.toRelative(srcPath), myRelativizer.toRelative(outputPath));
  }

  @NotNull
  @Override
  public Collection<String> getSources() throws IOException {
    return map(myMapping.getKeys(), toFull());
  }

  @Nullable
  @Override
  public Collection<String> getOutputs(@NotNull String srcPath) throws IOException {
    Collection<String> fromMap = myMapping.getState(myRelativizer.toRelative(srcPath));
    return fromMap == null ? null : map(fromMap, toFull());
  }

  @NotNull
  @Override
  public Iterator<String> getSourcesIterator() throws IOException {
    return JBIterator.from(myMapping.getKeysIterator()).map(toFull());
  }

  @NotNull
  private Function<String, String> toFull() {
    return s -> myRelativizer.toFull(s);
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

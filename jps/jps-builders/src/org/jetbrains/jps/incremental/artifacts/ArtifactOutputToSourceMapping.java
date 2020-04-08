/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.artifacts;

import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.incremental.storage.AbstractStateStorage;
import org.jetbrains.jps.incremental.storage.PathStringDescriptor;

import java.io.*;
import java.util.Collections;
import java.util.List;

/**
 * Stores source paths for each output path. If a source file or an output file is located in a jar file the path to the jar file is stored.
 */
public class ArtifactOutputToSourceMapping extends AbstractStateStorage<String, List<ArtifactOutputToSourceMapping.SourcePathAndRootIndex>> {
  private final PathRelativizerService myRelativizer;

  public ArtifactOutputToSourceMapping(File storePath, PathRelativizerService relativizer) throws IOException {
    super(storePath, PathStringDescriptor.INSTANCE, new SourcePathListExternalizer());
    myRelativizer = relativizer;
  }

  @Override
  public void update(String path, @Nullable List<SourcePathAndRootIndex> state) throws IOException {
    super.update(normalizePath(path), state != null ? normalizePaths(state) : null);
  }

  @Override
  public void appendData(String path, List<SourcePathAndRootIndex> data) throws IOException {
    super.appendData(normalizePath(path), data != null ? normalizePaths(data) : null);
  }

  public void appendData(String outputPath, int rootIndex, String sourcePath) throws IOException {
    super.appendData(normalizePath(outputPath), Collections.singletonList(new SourcePathAndRootIndex(normalizePath(sourcePath), rootIndex)));
  }

  @Override
  public void remove(String path) throws IOException {
    super.remove(normalizePath(path));
  }

  @Nullable
  @Override
  public List<SourcePathAndRootIndex> getState(String path) throws IOException {
    List<SourcePathAndRootIndex> list = super.getState(normalizePath(path));
    return list != null ? ContainerUtil.map(list, it -> new SourcePathAndRootIndex(myRelativizer.toFull(it.myPath), it.myRootIndex)) : null;
  }

  private String normalizePath(@NotNull String path) {
    return myRelativizer.toRelative(path);
  }

  private List<SourcePathAndRootIndex> normalizePaths(@NotNull List<SourcePathAndRootIndex> state) {
    List<SourcePathAndRootIndex> normalizePathList = new SmartList<>();
    state.forEach(it -> normalizePathList.add(new SourcePathAndRootIndex(normalizePath(it.myPath), it.myRootIndex)));
    return normalizePathList;
  }

  public static class SourcePathAndRootIndex {
    private final String myPath;
    private final int myRootIndex;

    private SourcePathAndRootIndex(String path, int rootIndex) {
      myPath = path;
      myRootIndex = rootIndex;
    }

    public String getPath() {
      return myPath;
    }

    public int getRootIndex() {
      return myRootIndex;
    }
  }

  private static class SourcePathListExternalizer implements DataExternalizer<List<SourcePathAndRootIndex>> {
    @Override
    public void save(@NotNull DataOutput out, List<SourcePathAndRootIndex> value) throws IOException {
      for (SourcePathAndRootIndex pair : value) {
        IOUtil.writeUTF(out, pair.myPath);
        out.writeInt(pair.getRootIndex());
      }
    }

    @Override
    public List<SourcePathAndRootIndex> read(@NotNull DataInput in) throws IOException {
      List<SourcePathAndRootIndex> result = new SmartList<>();
      final DataInputStream stream = (DataInputStream)in;
      while (stream.available() > 0) {
        final String path = IOUtil.readUTF(stream);
        final int index = stream.readInt();
        result.add(new SourcePathAndRootIndex(path, index));
      }
      return result;
    }
  }
}

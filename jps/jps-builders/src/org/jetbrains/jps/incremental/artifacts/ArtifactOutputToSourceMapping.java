// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.incremental.storage.AbstractStateStorage;
import org.jetbrains.jps.incremental.storage.PathStringDescriptors;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Stores source paths for each output path.
 * If a source file or an output file is located in a jar file, the path to the jar file is stored.
 */
public final class ArtifactOutputToSourceMapping
  extends AbstractStateStorage<String, List<ArtifactOutputToSourceMapping.SourcePathAndRootIndex>> {
  private final PathRelativizerService myRelativizer;

  public ArtifactOutputToSourceMapping(File storePath, PathRelativizerService relativizer) throws IOException {
    super(storePath, PathStringDescriptors.createPathStringDescriptor(), new SourcePathListExternalizer());
    myRelativizer = relativizer;
  }

  @Override
  public void update(String path, @Nullable List<SourcePathAndRootIndex> state) throws IOException {
    super.update(normalizePath(path), state == null ? null : normalizePaths(state));
  }

  @Override
  public void appendData(String path, List<SourcePathAndRootIndex> data) throws IOException {
    super.appendData(normalizePath(path), data == null ? null : normalizePaths(data));
  }

  public void appendData(String outputPath, int rootIndex, String sourcePath) throws IOException {
    super.appendData(normalizePath(outputPath), List.of(new SourcePathAndRootIndex(normalizePath(sourcePath), rootIndex)));
  }

  @Override
  public void remove(String path) throws IOException {
    super.remove(normalizePath(path));
  }

  @Override
  public @Nullable List<SourcePathAndRootIndex> getState(String path) throws IOException {
    List<SourcePathAndRootIndex> list = super.getState(normalizePath(path));
    if (list == null) {
      return null;
    }
    else {
      if (list.isEmpty()) {
        return List.of();
      }

      SourcePathAndRootIndex[] result = new SourcePathAndRootIndex[list.size()];
      for (int i = 0; i < list.size(); i++) {
        ArtifactOutputToSourceMapping.SourcePathAndRootIndex t = list.get(i);
        result[i] = new SourcePathAndRootIndex(myRelativizer.toFull(t.myPath), t.myRootIndex);
      }
      return Arrays.asList(result);
    }
  }

  private String normalizePath(@NotNull String path) {
    return myRelativizer.toRelative(path);
  }

  private List<SourcePathAndRootIndex> normalizePaths(@NotNull List<SourcePathAndRootIndex> state) {
    SourcePathAndRootIndex[] result = new SourcePathAndRootIndex[state.size()];
    for (int i = 0, size = state.size(); i < size; i++) {
      SourcePathAndRootIndex it = state.get(i);
      result[i] = new SourcePathAndRootIndex(normalizePath(it.myPath), it.myRootIndex);
    }
    return Arrays.asList(result);
  }

  public static final class SourcePathAndRootIndex {
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

  private static final class SourcePathListExternalizer implements DataExternalizer<List<SourcePathAndRootIndex>> {
    @Override
    public void save(@NotNull DataOutput out, List<SourcePathAndRootIndex> value) throws IOException {
      for (SourcePathAndRootIndex pair : value) {
        IOUtil.writeUTF(out, pair.myPath);
        out.writeInt(pair.getRootIndex());
      }
    }

    @Override
    public List<SourcePathAndRootIndex> read(@NotNull DataInput in) throws IOException {
      List<SourcePathAndRootIndex> result = new ArrayList<>();
      DataInputStream stream = (DataInputStream)in;
      while (stream.available() > 0) {
        String path = IOUtil.readUTF(stream);
        int index = stream.readInt();
        result.add(new SourcePathAndRootIndex(path, index));
      }
      return result;
    }
  }
}

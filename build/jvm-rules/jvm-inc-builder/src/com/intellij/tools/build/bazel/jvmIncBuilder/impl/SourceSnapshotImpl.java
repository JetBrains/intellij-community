// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.NodeSourceSnapshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.DataReader;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.NodeSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SourceSnapshotImpl implements NodeSourceSnapshot {
  private final Map<NodeSource, String> mySources;

  public SourceSnapshotImpl(Map<NodeSource, String> digestSources) {
    mySources = Map.copyOf(digestSources);
  }
  
  public SourceSnapshotImpl(GraphDataInput in, DataReader<? extends NodeSource> sourceReader) throws IOException {
    Map<NodeSource, String> sources = new HashMap<>();
    int count = in.readInt();
    while (count-- > 0) {
      String digest = in.readUTF();
      sources.put(sourceReader.load(in), digest);
    }
    mySources = Map.copyOf(sources);
  }

  @Override
  public @NotNull Iterable<@NotNull NodeSource> getElements() {
    return mySources.keySet();
  }

  @Override
  public @NotNull String getDigest(NodeSource src) {
    return mySources.getOrDefault(src, "");
  }

  @Override
  public void write(GraphDataOutput out) throws IOException {
    out.writeInt(mySources.size());
    for (Map.Entry<NodeSource, String> entry : mySources.entrySet()) {
      out.writeUTF(entry.getValue());
      entry.getKey().write(out);
    }
  }
}

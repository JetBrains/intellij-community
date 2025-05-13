// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.BuildContext;
import com.intellij.tools.build.bazel.jvmIncBuilder.DataPaths;
import com.intellij.tools.build.bazel.jvmIncBuilder.Message;
import com.intellij.tools.build.bazel.jvmIncBuilder.NodeSourceSnapshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.NodeSourcePathMapper;
import org.jetbrains.jps.dependency.impl.GraphDataInputImpl;
import org.jetbrains.jps.dependency.impl.GraphDataOutputImpl;
import org.jetbrains.jps.dependency.impl.PathSource;
import org.jetbrains.jps.dependency.impl.PathSourceMapper;
import org.jetbrains.jps.javac.Iterators;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import static org.jetbrains.jps.javac.Iterators.flat;
import static org.jetbrains.jps.javac.Iterators.map;

public class ConfigurationState {
  private static final ConfigurationState EMPTY = new ConfigurationState(new PathSourceMapper(), NodeSourceSnapshot.EMPTY, NodeSourceSnapshot.EMPTY);

  private final NodeSourcePathMapper myPathMapper;
  private final NodeSourceSnapshot mySourcesSnapshot;
  private final NodeSourceSnapshot myLibsSnapshot;

  public ConfigurationState(NodeSourcePathMapper pathMapper, NodeSourceSnapshot sourcesSnapshot, NodeSourceSnapshot libsSnapshot) {
    myPathMapper = pathMapper;
    mySourcesSnapshot = sourcesSnapshot;
    myLibsSnapshot = libsSnapshot;
  }

  public ConfigurationState(NodeSourcePathMapper pathMapper, Path savedState) throws IOException {
    myPathMapper = pathMapper;
    try (var stream = new DataInputStream(new InflaterInputStream(Files.newInputStream(savedState, StandardOpenOption.READ)))) {
      GraphDataInput in = GraphDataInputImpl.wrap(stream);
      mySourcesSnapshot = new SourceSnapshotImpl(in, PathSource::new);
      myLibsSnapshot = new SourceSnapshotImpl(in, PathSource::new);
    }
  }

  public void save(BuildContext context) {
    Path snapshotPath = DataPaths.getConfigStateStoreFile(context);
    try (var stream = new DataOutputStream(new DeflaterOutputStream(Files.newOutputStream(snapshotPath), new Deflater(Deflater.BEST_SPEED)))) {
      GraphDataOutput out = GraphDataOutputImpl.wrap(stream);
      getSources().write(out);
      getLibraries().write(out);
    }
    catch (Throwable e) {
      context.report(Message.create(null, e));
    }
  }

  public static ConfigurationState loadSavedState(BuildContext context) {
    try {
      return new ConfigurationState(context.getPathMapper(), DataPaths.getConfigStateStoreFile(context));
    }
    catch (NoSuchFileException e) {
      return EMPTY;
    }
    catch (Throwable e) {
      context.report(Message.create(null, e));
      return EMPTY;
    }
  }

  public NodeSourceSnapshot getSources() {
    return mySourcesSnapshot;
  }

  public NodeSourceSnapshot getLibraries() {
    return myLibsSnapshot;
  }

  // tracks names and order of classpath entries as well as content digests of all third-party dependencies
  public long getClasspathStructureDigest() {
    NodeSourceSnapshot deps = getLibraries();

    // digest name, count and order of classpath entries as well as content digests of all non-abi deps
    Iterators.Function<@NotNull NodeSource, Iterable<String>> digestMapper =
      src -> {
        Path path = myPathMapper.toPath(src);
        return DataPaths.isLibraryTracked(path)? List.of(DataPaths.getLibraryName(path)) : List.of(DataPaths.getLibraryName(path), deps.getDigest(src));
      };

    return Utils.digest(flat(map(deps.getElements(), digestMapper)));
  }

}

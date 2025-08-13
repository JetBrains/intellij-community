// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.NodeSourcePathMapper;
import org.jetbrains.jps.dependency.impl.GraphDataInputImpl;
import org.jetbrains.jps.dependency.impl.GraphDataOutputImpl;
import org.jetbrains.jps.dependency.impl.PathSource;
import org.jetbrains.jps.dependency.impl.PathSourceMapper;
import org.jetbrains.jps.util.Iterators;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import static org.jetbrains.jps.util.Iterators.*;

public class ConfigurationState {
  // Update the version value whenever a serialization format changes.
  // This will help to avoid multiple "failed to load configuration" error messages
  private static final int VERSION = 1;

  private static final ConfigurationState EMPTY = new ConfigurationState(new PathSourceMapper(), NodeSourceSnapshot.EMPTY, NodeSourceSnapshot.EMPTY, Map.of());

  private static final Set<CLFlags> ourIgnoredFlags = EnumSet.of(
    CLFlags.NON_INCREMENTAL,
    CLFlags.JAVA_COUNT,
    CLFlags.TARGET_LABEL,
    CLFlags.CP, // processed separately
    CLFlags.OUT,
    CLFlags.ABI_OUT,
    CLFlags.WARN,
    CLFlags.X_WASM_ATTACH_JS_EXCEPTION,
    CLFlags.ADD_EXPORT,
    CLFlags.ADD_READS
  );
  
  private final NodeSourcePathMapper myPathMapper;
  private final NodeSourceSnapshot mySourcesSnapshot;
  private final NodeSourceSnapshot myLibsSnapshot;
  private final long myFlagsDigest;

  public ConfigurationState(NodeSourcePathMapper pathMapper, NodeSourceSnapshot sourcesSnapshot, NodeSourceSnapshot libsSnapshot, Map<CLFlags, List<String>> flags) {
    myPathMapper = pathMapper;
    mySourcesSnapshot = sourcesSnapshot;
    myLibsSnapshot = libsSnapshot;
    myFlagsDigest = buildFlagsDigest(flags);
  }

  public ConfigurationState(NodeSourcePathMapper pathMapper, Path savedState) throws IOException {
    myPathMapper = pathMapper;
    try (var stream = new DataInputStream(new InflaterInputStream(Files.newInputStream(savedState, StandardOpenOption.READ)))) {
      GraphDataInput in = GraphDataInputImpl.wrap(stream);
      int version = in.readInt();
      if (version == VERSION) {
        mySourcesSnapshot = new SourceSnapshotImpl(in, PathSource::new);
        myLibsSnapshot = new SourceSnapshotImpl(in, PathSource::new);
        myFlagsDigest = in.readLong();
      }
      else { // version differs
        mySourcesSnapshot = NodeSourceSnapshot.EMPTY;
        myLibsSnapshot = NodeSourceSnapshot.EMPTY;
        myFlagsDigest = buildFlagsDigest(Map.of());
      }
    }
  }

  public void save(BuildContext context) {
    Path snapshotPath = DataPaths.getConfigStateStoreFile(context);
    try (var stream = new DataOutputStream(new DeflaterOutputStream(Files.newOutputStream(snapshotPath), new Deflater(Deflater.BEST_SPEED)))) {
      GraphDataOutput out = GraphDataOutputImpl.wrap(stream);
      out.writeInt(VERSION);
      getSources().write(out);
      getLibraries().write(out);
      out.writeLong(myFlagsDigest);
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
      context.report(Message.create(null, Message.Kind.INFO, "Error loading configuration state for " + context.getTargetName(), e));
      return EMPTY;
    }
  }

  public NodeSourceSnapshot getSources() {
    return mySourcesSnapshot;
  }

  public NodeSourceSnapshot getLibraries() {
    return myLibsSnapshot;
  }

  public long getFlagsDigest() {
    return myFlagsDigest;
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

  private static long buildFlagsDigest(Map<CLFlags, List<String>> flags) {
    if (flags.isEmpty()) {
      return 0;
    }
    
    Function<List<String>, Iterable<String>> sorted = col -> {
      if (col == null) {
        return List.of();
      }
      if (col.size() <= 1) {
        return col;
      }
      List<String> copy = new ArrayList<>(col);
      Collections.sort(copy);
      return copy;
    };

    return Utils.digest(
      flat(map(filter(Arrays.asList(CLFlags.values()), flg -> flags.containsKey(flg) && !ourIgnoredFlags.contains(flg)), flg -> flat(asIterable(flg.name()), sorted.fun(flags.get(flg)))))
    );
  }

}

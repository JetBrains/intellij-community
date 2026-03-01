// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.intellij.tools.build.bazel.jvmIncBuilder.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.GraphDataInput;
import org.jetbrains.jps.dependency.GraphDataOutput;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.NodeSourcePathMapper;
import org.jetbrains.jps.dependency.impl.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import static org.jetbrains.jps.util.Iterators.*;

public class ConfigurationState {
  private static final Logger LOG = Logger.getLogger("com.intellij.tools.build.bazel.jvmIncBuilder.impl.ConfigurationState");
  // Update the version value whenever a serialization format changes.
  // This will help to avoid multiple "failed to load configuration" error messages
  // Also consider advancing the version when
  //  - ABI generation logic changed (e.g. changes in ordering, filtering, etc)
  //  - Any changes in builder's logic implemented, that might affect sources processing
  private static final int VERSION = 7;

  private static final ConfigurationState EMPTY = new ConfigurationState(
    new PathSourceMapper(), NodeSourceSnapshot.EMPTY, List.of(), NodeSourceSnapshot.EMPTY, Map.of(), -1L
  );

  private static final Set<CLFlags> ourIgnoredFlags = EnumSet.of(
    CLFlags.NON_INCREMENTAL,
    CLFlags.JAVA_COUNT,
    CLFlags.TARGET_LABEL,

    CLFlags.CP, // processed separately
    CLFlags.OUT,
    CLFlags.ABI_OUT,
    CLFlags.KOTLIN_CRI_OUT,

    CLFlags.WARN,
    CLFlags.X_WASM_ATTACH_JS_EXCEPTION,
    CLFlags.ADD_EXPORT,
    CLFlags.ADD_READS,

    CLFlags.SRCS,
    CLFlags.RESOURCES
  );
  
  private final NodeSourcePathMapper myPathMapper;
  private final NodeSourceSnapshot mySourcesSnapshot;
  private final Iterable<ResourceGroup> myResources;
  private final NodeSourceSnapshot myLibsSnapshot;
  private final long myFlagsDigest;
  private final long myRunnersDigest;
  private final long myUntrackedInputsDigest;

  public ConfigurationState(
    NodeSourcePathMapper pathMapper, NodeSourceSnapshot sourcesSnapshot, Iterable<ResourceGroup> resourceGroups, NodeSourceSnapshot libsSnapshot, Map<CLFlags, List<String>> flags, long untrackedInputsDigest
  ) {
    myPathMapper = pathMapper;
    mySourcesSnapshot = sourcesSnapshot;
    myResources = resourceGroups;
    myLibsSnapshot = libsSnapshot;
    myFlagsDigest = buildFlagsDigest(flags);
    myRunnersDigest = RunnerRegistry.getConfigurationDigest();
    myUntrackedInputsDigest = untrackedInputsDigest;
  }

  public ConfigurationState(NodeSourcePathMapper pathMapper, Path savedState) throws IOException {
    myPathMapper = pathMapper;
    try (var stream = new DataInputStream(new InflaterInputStream(Files.newInputStream(savedState, StandardOpenOption.READ)))) {
      GraphDataInput in = GraphDataInputImpl.wrap(stream);
      int version = in.readInt();
      if (version == VERSION) {
        mySourcesSnapshot = new SourceSnapshotImpl(in, PathSource::new);
        myResources = RW.readCollection(in, () -> new ResourceGroupImpl(in, PathSource::new));
        myLibsSnapshot = new SourceSnapshotImpl(in, PathSource::new);
        myFlagsDigest = in.readLong();
        myRunnersDigest = in.readLong();
        myUntrackedInputsDigest = in.readLong();
      }
      else { // version differs
        mySourcesSnapshot = EMPTY.mySourcesSnapshot;
        myResources = EMPTY.myResources;
        myLibsSnapshot = EMPTY.myLibsSnapshot;
        myFlagsDigest = EMPTY.myFlagsDigest;
        myRunnersDigest = 0; // will differ from current RUNNERS_DIGEST, triggering rebuild
        myUntrackedInputsDigest = EMPTY.myUntrackedInputsDigest;
      }
    }
  }

  public void save(BuildContext context) {
    Path snapshotPath = DataPaths.getConfigStateStoreFile(context);
    try (var stream = new DataOutputStream(new DeflaterOutputStream(Files.newOutputStream(snapshotPath), new Deflater(Deflater.BEST_SPEED)))) {
      GraphDataOutput out = GraphDataOutputImpl.wrap(stream);
      out.writeInt(VERSION);
      getSources().write(out);
      RW.writeCollection(out, myResources, gr -> gr.write(out));
      getLibraries().write(out);
      out.writeLong(myFlagsDigest);
      out.writeLong(myRunnersDigest);
      out.writeLong(myUntrackedInputsDigest);
    }
    catch (Throwable e) {
      LOG.log(Level.SEVERE, "Error saving build configuration state " + context.getTargetName(), e);
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

  public boolean digestsDiffer(ConfigurationState other) {
    return getFlagsDigest() != other.getFlagsDigest()
           || getClasspathStructureDigest() != other.getClasspathStructureDigest()
           || getRunnersDigest() != other.getRunnersDigest()
           || getUntrackedInputsDigest() != other.getUntrackedInputsDigest();
  }

  public NodeSourceSnapshot getSources() {
    return mySourcesSnapshot;
  }

  public Iterable<ResourceGroup> getResources() {
    return myResources;
  }

  public NodeSourceSnapshot getLibraries() {
    return myLibsSnapshot;
  }

  public long getFlagsDigest() {
    return myFlagsDigest;
  }

  public long getRunnersDigest() {
    return myRunnersDigest;
  }

  public long getUntrackedInputsDigest() {
    return myUntrackedInputsDigest;
  }

  // tracks names and order of classpath entries as well as content digests of all third-party dependencies
  private Long myClassPathStructureDigest;

  public long getClasspathStructureDigest() {
    Long cached = myClassPathStructureDigest;
    if (cached != null) {
      return cached;
    }
    NodeSourceSnapshot deps = getLibraries();

    // digest name, count and order of classpath entries as well as content digests of all non-abi deps
    Function<@NotNull NodeSource, Iterable<String>> digestMapper =
      src -> {
        Path path = myPathMapper.toPath(src);
        return DataPaths.isLibraryTracked(path)? List.of(DataPaths.getLibraryName(path)) : List.of(DataPaths.getLibraryName(path), deps.getDigest(src));
      };

    long dig = Utils.digest(flat(map(deps.getElements(), digestMapper)));
    myClassPathStructureDigest = dig;
    return dig;
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
      flat(map(filter(Arrays.asList(CLFlags.values()), flg -> flags.containsKey(flg) && !ourIgnoredFlags.contains(flg)), flg -> flat(asIterable(flg.name()), sorted.apply(flags.get(flg)))))
    );
  }

}

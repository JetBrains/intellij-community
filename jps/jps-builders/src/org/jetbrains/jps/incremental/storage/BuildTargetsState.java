// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.impl.BuildRootIndexImpl;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.TargetTypeRegistry;
import org.jetbrains.jps.model.JpsModel;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class BuildTargetsState {
  private static final Logger LOG = Logger.getInstance(BuildTargetsState.class);
  private final BuildDataPaths dataPaths;
  private final AtomicInteger myMaxTargetId = new AtomicInteger(0);
  private long myLastSuccessfulRebuildDuration = -1;
  private final ConcurrentMap<BuildTargetType<?>, BuildTargetTypeState> typeToState = new ConcurrentHashMap<>(16, 0.75f, 1);
  private final JpsModel model;

  /**
   * @deprecated temporary available to enable kotlin tests running. Should be removed eventually
   */
  @Deprecated
  @ApiStatus.Internal
  public BuildTargetsState(@NotNull BuildDataPaths dataPaths, JpsModel model, BuildRootIndexImpl ignored) {
    this(dataPaths, model);
  }

  @ApiStatus.Internal
  public BuildTargetsState(@NotNull BuildDataPaths dataPaths, @NotNull JpsModel model) {
    this.dataPaths = dataPaths;
    this.model = model;
    Path targetTypesFile = getTargetTypesFile();
    try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(targetTypesFile)))) {
      myMaxTargetId.set(input.readInt());
      myLastSuccessfulRebuildDuration = input.readLong();
    }
    catch (IOException e) {
      LOG.debug("Cannot load " + targetTypesFile + ":" + e.getMessage(), e);
      LOG.debug("Loading all target types to calculate max target id");
      for (BuildTargetType<?> type : TargetTypeRegistry.getInstance().getTargetTypes()) {
        getTypeState(type);
      }
    }
  }

  private @NotNull Path getTargetTypesFile() {
    return dataPaths.getTargetsDataRoot().resolve("targetTypes.dat");
  }

  public void save() {
    try {
      Path targetTypesFile = getTargetTypesFile();
      Files.createDirectories(targetTypesFile.getParent());
      try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(targetTypesFile)))) {
        output.writeInt(myMaxTargetId.get());
        output.writeLong(myLastSuccessfulRebuildDuration);
      }
    }
    catch (IOException e) {
      LOG.info("Cannot save targets info: " + e.getMessage(), e);
    }
    for (BuildTargetTypeState state : typeToState.values()) {
      state.save();
    }
  }

  public int getBuildTargetId(@NotNull BuildTarget<?> target) {
    return getTypeState(target.getTargetType()).getTargetId(target);
  }

  /**
   * @return -1 if this information is not available
   */
  public long getLastSuccessfulRebuildDuration() {
    return myLastSuccessfulRebuildDuration;
  }

  public void setLastSuccessfulRebuildDuration(long duration) {
    myLastSuccessfulRebuildDuration = duration;
  }

  public @NotNull BuildTargetConfiguration getTargetConfiguration(@NotNull BuildTarget<?> target) {
    return getTypeState(target.getTargetType()).getConfiguration(target, dataPaths);
  }

  public @NotNull @Unmodifiable List<Pair<String, Integer>> getStaleTargetIds(@NotNull BuildTargetType<?> type) {
    return getTypeState(type).getStaleTargetIds();
  }

  void cleanStaleTarget(BuildTargetType<?> type, String targetId) {
    getTypeState(type).removeStaleTarget(targetId);
  }

  public void setAverageBuildTime(BuildTargetType<?> type, long time) {
    getTypeState(type).setAverageTargetBuildTime(time);
  }

  public long getAverageBuildTime(BuildTargetType<?> type) {
    return getTypeState(type).getAverageTargetBuildTime();
  }

  private @NotNull BuildTargetTypeState getTypeState(@NotNull BuildTargetType<?> type) {
    return typeToState.computeIfAbsent(type, it -> new BuildTargetTypeState(it, this, dataPaths, model));
  }

  public void markUsedId(int id) {
    int current;
    int max;
    do {
      current = myMaxTargetId.get();
      max = Math.max(id, current);
    }
    while (!myMaxTargetId.compareAndSet(current, max));
  }

  public int getFreeId() {
    return myMaxTargetId.incrementAndGet();
  }

  public void clean() {
    try {
      FileUtilRt.deleteRecursively(dataPaths.getTargetsDataRoot());
    }
    catch (IOException ignored) {
    }
  }
}

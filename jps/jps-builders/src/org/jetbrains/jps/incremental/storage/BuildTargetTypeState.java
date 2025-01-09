// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.util.io.IOUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.model.JpsModel;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApiStatus.Internal
public final class BuildTargetTypeState {
  private static final int VERSION = 1;
  private static final Logger LOG = Logger.getInstance(BuildTargetTypeState.class);
  @SuppressWarnings("SSBasedInspection")
  private final Object2IntOpenHashMap<BuildTarget<?>> targetIds = new Object2IntOpenHashMap<>();
  private final List<Pair<String, Integer>> staleTargetIds;
  private final ConcurrentMap<BuildTarget<?>, BuildTargetConfiguration> configurations;
  private final BuildTargetType<?> targetType;
  private final BuildTargetStateManagerImpl targetStateManager;
  private final Path targetStateFile;
  private volatile long myAverageTargetBuildTimeMs = -1;

  public BuildTargetTypeState(@NotNull BuildTargetType<?> targetType,
                              @NotNull BuildTargetStateManagerImpl state,
                              @NotNull BuildDataPaths dataPaths,
                              @NotNull JpsModel model) {
    targetIds.defaultReturnValue(-1);

    this.targetType = targetType;
    targetStateManager = state;
    targetStateFile = dataPaths.getTargetTypeDataRootDir(targetType).resolve("targets.dat");
    configurations = new ConcurrentHashMap<>();
    staleTargetIds = new ArrayList<>();
    load(model);
  }

  private void load(@NotNull JpsModel model) {
    if (Files.notExists(targetStateFile)) {
      return;
    }

    try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(targetStateFile)))) {
      int version = input.readInt();
      int size = input.readInt();
      BuildTargetLoader<?> loader = targetType.createLoader(model);
      while (size-- > 0) {
        String stringId = IOUtil.readString(input);
        int intId = input.readInt();
        targetStateManager.markUsedId(intId);
        BuildTarget<?> target = loader.createTarget(stringId);
        if (target != null) {
          targetIds.put(target, intId);
        }
        else {
          staleTargetIds.add(Pair.create(stringId, intId));
        }
      }
      if (version >= 1) {
        myAverageTargetBuildTimeMs = input.readLong();
      }
    }
    catch (IOException e) {
      LOG.info("Cannot load " + targetType.getTypeId() + " targets data: " + e.getMessage(), e);
    }
  }

  public synchronized void save() {
    try {
      NioFiles.createParentDirectories(targetStateFile);
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(targetStateFile)))) {
      output.writeInt(VERSION);
      output.writeInt(targetIds.size() + staleTargetIds.size());
      for (Object2IntMap.Entry<BuildTarget<?>> entry : targetIds.object2IntEntrySet()) {
        IOUtil.writeString(entry.getKey().getId(), output);
        output.writeInt(entry.getIntValue());
      }
      for (Pair<String, Integer> pair : staleTargetIds) {
        IOUtil.writeString(pair.first, output);
        output.writeInt(pair.second);
      }
      output.writeLong(myAverageTargetBuildTimeMs);
    }
    catch (IOException e) {
      LOG.info("Cannot save " + targetType.getTypeId() + " targets data: " + e.getMessage(), e);
    }
  }

  public synchronized @NotNull List<Pair<String, Integer>> getStaleTargetIds() {
    return new ArrayList<>(staleTargetIds);
  }

  public synchronized void removeStaleTarget(@NotNull String targetId) {
    staleTargetIds.removeIf(pair -> pair.first.equals(targetId));
  }

  public synchronized int getTargetId(BuildTarget<?> target) {
    int result = targetIds.getInt(target);
    if (result == -1) {
      result = targetStateManager.getFreeId();
      targetIds.put(target, result);
    }
    return result;
  }

  public void setAverageTargetBuildTime(long timeInMs) {
    myAverageTargetBuildTimeMs = timeInMs;
  }

  /**
   * Returns average time required to rebuild a target of this type from scratch or {@code -1} if such information isn't available.
   */
  public long getAverageTargetBuildTime() {
    return myAverageTargetBuildTimeMs;
  }

  public @NotNull BuildTargetConfiguration getConfiguration(@NotNull BuildTarget<?> target, @NotNull BuildDataPaths dataPaths) {
    BuildTargetConfiguration configuration = configurations.get(target);
    if (configuration != null) {
      return configuration;
    }

    configuration = new BuildTargetConfiguration(target, dataPaths);
    BuildTargetConfiguration existing = configurations.putIfAbsent(target, configuration);
    return existing == null ? configuration : existing;
  }
}

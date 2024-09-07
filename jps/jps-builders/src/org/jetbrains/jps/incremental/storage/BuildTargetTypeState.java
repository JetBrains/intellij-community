// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.io.IOUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.BuildTargetType;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BuildTargetTypeState {
  private static final int VERSION = 1;
  private static final Logger LOG = Logger.getInstance(BuildTargetTypeState.class);
  private final Object2IntOpenHashMap<BuildTarget<?>> targetIds = new Object2IntOpenHashMap<>();
  private final List<Pair<String, Integer>> myStaleTargetIds;
  private final ConcurrentMap<BuildTarget<?>, BuildTargetConfiguration> myConfigurations;
  private final BuildTargetType<?> myTargetType;
  private final BuildTargetsState targetState;
  private final File myTargetsFile;
  private volatile long myAverageTargetBuildTimeMs = -1;

  public BuildTargetTypeState(BuildTargetType<?> targetType, BuildTargetsState state) {
    targetIds.defaultReturnValue(-1);

    myTargetType = targetType;
    targetState = state;
    myTargetsFile = new File(state.getDataPaths().getTargetTypeDataRoot(targetType), "targets.dat");
    myConfigurations = new ConcurrentHashMap<>(16, 0.75f, 1);
    myStaleTargetIds = new ArrayList<>();
    load();
  }

  private boolean load() {
    if (!myTargetsFile.exists()) {
      return false;
    }

    try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(myTargetsFile)))) {
      int version = input.readInt();
      int size = input.readInt();
      BuildTargetLoader<?> loader = myTargetType.createLoader(targetState.getModel());
      while (size-- > 0) {
        String stringId = IOUtil.readString(input);
        int intId = input.readInt();
        targetState.markUsedId(intId);
        BuildTarget<?> target = loader.createTarget(stringId);
        if (target != null) {
          targetIds.put(target, intId);
        }
        else {
          myStaleTargetIds.add(Pair.create(stringId, intId));
        }
      }
      if (version >= 1) {
        myAverageTargetBuildTimeMs = input.readLong();
      }
      return true;
    }
    catch (IOException e) {
      LOG.info("Cannot load " + myTargetType.getTypeId() + " targets data: " + e.getMessage(), e);
      return false;
    }
  }

  public synchronized void save() {
    FileUtilRt.createParentDirs(myTargetsFile);
    try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(myTargetsFile)))) {
      output.writeInt(VERSION);
      output.writeInt(targetIds.size() + myStaleTargetIds.size());
      for (Object2IntMap.Entry<BuildTarget<?>> entry : targetIds.object2IntEntrySet()) {
        IOUtil.writeString(entry.getKey().getId(), output);
        output.writeInt(entry.getIntValue());
      }
      for (Pair<String, Integer> pair : myStaleTargetIds) {
        IOUtil.writeString(pair.first, output);
        output.writeInt(pair.second);
      }
      output.writeLong(myAverageTargetBuildTimeMs);
    }
    catch (IOException e) {
      LOG.info("Cannot save " + myTargetType.getTypeId() + " targets data: " + e.getMessage(), e);
    }
  }

  public synchronized List<Pair<String, Integer>> getStaleTargetIds() {
    return new ArrayList<>(myStaleTargetIds);
  }

  public synchronized void removeStaleTarget(String targetId) {
    myStaleTargetIds.removeIf(pair -> pair.first.equals(targetId));
  }

  public synchronized int getTargetId(BuildTarget<?> target) {
    int result = targetIds.getInt(target);
    if (result == -1) {
      result = targetState.getFreeId();
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

  public BuildTargetConfiguration getConfiguration(BuildTarget<?> target) {
    BuildTargetConfiguration configuration = myConfigurations.get(target);
    if (configuration == null) {
      configuration = new BuildTargetConfiguration(target, targetState);
      final BuildTargetConfiguration existing = myConfigurations.putIfAbsent(target, configuration);
      if (existing != null) {
        configuration = existing;
      }
    }
    return configuration;
  }
}

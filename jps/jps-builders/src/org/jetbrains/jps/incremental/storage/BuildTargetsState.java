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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.impl.BuildRootIndexImpl;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.TargetTypeRegistry;
import org.jetbrains.jps.model.JpsModel;

import java.io.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class BuildTargetsState {
  private static final Logger LOG = Logger.getInstance(BuildTargetsState.class);
  private final BuildDataPaths myDataPaths;
  private final AtomicInteger myMaxTargetId = new AtomicInteger(0);
  private long myLastSuccessfulRebuildDuration = -1;
  private final ConcurrentMap<BuildTargetType<?>, BuildTargetTypeState> myTypeStates = new ConcurrentHashMap<>(16, 0.75f, 1);
  private final JpsModel myModel;
  private final BuildRootIndexImpl myBuildRootIndex;

  public BuildTargetsState(BuildDataPaths dataPaths, JpsModel model, BuildRootIndexImpl buildRootIndex) {
    myDataPaths = dataPaths;
    myModel = model;
    myBuildRootIndex = buildRootIndex;
    File targetTypesFile = getTargetTypesFile();
    try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(targetTypesFile)))) {
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

  private File getTargetTypesFile() {
    return new File(myDataPaths.getTargetsDataRoot(), "targetTypes.dat");
  }

  public void save() {
    try {
      File targetTypesFile = getTargetTypesFile();
      FileUtil.createParentDirs(targetTypesFile);
      try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(targetTypesFile)))) {
        output.writeInt(myMaxTargetId.get());
        output.writeLong(myLastSuccessfulRebuildDuration);
      }
    }
    catch (IOException e) {
      LOG.info("Cannot save targets info: " + e.getMessage(), e);
    }
    for (BuildTargetTypeState state : myTypeStates.values()) {
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

  @NotNull
  public BuildTargetConfiguration getTargetConfiguration(@NotNull BuildTarget<?> target) {
    return getTypeState(target.getTargetType()).getConfiguration(target);
  }

  public List<Pair<String, Integer>> getStaleTargetIds(@NotNull BuildTargetType<?> type) {
    return getTypeState(type).getStaleTargetIds();
  }

  public void cleanStaleTarget(BuildTargetType<?> type, String targetId) {
    getTypeState(type).removeStaleTarget(targetId);
  }

  public void setAverageBuildTime(BuildTargetType<?> type, long time) {
    getTypeState(type).setAverageTargetBuildTime(time);
  }

  public long getAverageBuildTime(BuildTargetType<?> type) {
    return getTypeState(type).getAverageTargetBuildTime();
  }

  private BuildTargetTypeState getTypeState(BuildTargetType<?> type) {
    BuildTargetTypeState state = myTypeStates.get(type);
    if (state == null) {
      final BuildTargetTypeState newState = new BuildTargetTypeState(type, this);
      state = myTypeStates.putIfAbsent(type, newState);
      if (state == null) {
        state = newState;
      }
    }
    return state;
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
    FileUtil.delete(myDataPaths.getTargetsDataRoot());
  }

  public JpsModel getModel() {
    return myModel;
  }

  public BuildRootIndexImpl getBuildRootIndex() {
    return myBuildRootIndex;
  }

  public BuildDataPaths getDataPaths() {
    return myDataPaths;
  }
}

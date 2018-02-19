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
import com.intellij.util.io.IOUtil;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.BuildTargetType;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author nik
 */
public class BuildTargetTypeState {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.storage.BuildTargetTypeState");
  private final Map<BuildTarget<?>, Integer> myTargetIds;
  private final List<Pair<String, Integer>> myStaleTargetIds;
  private final ConcurrentMap<BuildTarget<?>, BuildTargetConfiguration> myConfigurations;
  private final BuildTargetType<?> myTargetType;
  private final BuildTargetsState myTargetsState;
  private final File myTargetsFile;

  public BuildTargetTypeState(BuildTargetType<?> targetType, BuildTargetsState state) {
    myTargetType = targetType;
    myTargetsState = state;
    myTargetsFile = new File(state.getDataPaths().getTargetTypeDataRoot(targetType), "targets.dat");
    myConfigurations = new ConcurrentHashMap<>(16, 0.75f, 1);
    myTargetIds = new HashMap<>();
    myStaleTargetIds = new ArrayList<>();
    load();
  }

  private boolean load() {
    if (!myTargetsFile.exists()) {
      return false;
    }

    try {
      DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(myTargetsFile)));
      try {
        input.readInt();//reserved for version
        int size = input.readInt();
        BuildTargetLoader<?> loader = myTargetType.createLoader(myTargetsState.getModel());
        while (size-- > 0) {
          String stringId = IOUtil.readString(input);
          int intId = input.readInt();
          myTargetsState.markUsedId(intId);
          BuildTarget<?> target = loader.createTarget(stringId);
          if (target != null) {
            myTargetIds.put(target, intId);
          }
          else {
            myStaleTargetIds.add(Pair.create(stringId, intId));
          }
        }
        return true;
      }
      finally {
        input.close();
      }
    }
    catch (IOException e) {
      LOG.info("Cannot load " + myTargetType.getTypeId() + " targets data: " + e.getMessage(), e);
      return false;
    }
  }

  public synchronized void save() {
    try {
      FileUtil.createParentDirs(myTargetsFile);
      DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(myTargetsFile)));
      try {
        output.writeInt(0);
        output.writeInt(myTargetIds.size() + myStaleTargetIds.size());
        for (Map.Entry<BuildTarget<?>, Integer> entry : myTargetIds.entrySet()) {
          IOUtil.writeString(entry.getKey().getId(), output);
          output.writeInt(entry.getValue());
        }
        for (Pair<String, Integer> pair : myStaleTargetIds) {
          IOUtil.writeString(pair.first, output);
          output.writeInt(pair.second);
        }
      }
      finally {
        output.close();
      }
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
    if (!myTargetIds.containsKey(target)) {
      myTargetIds.put(target, myTargetsState.getFreeId());
    }
    return myTargetIds.get(target);
  }

  public BuildTargetConfiguration getConfiguration(BuildTarget<?> target) {
    BuildTargetConfiguration configuration = myConfigurations.get(target);
    if (configuration == null) {
      configuration = new BuildTargetConfiguration(target, myTargetsState);
      final BuildTargetConfiguration existing = myConfigurations.putIfAbsent(target, configuration);
      if (existing != null) {
        configuration = existing;
      }
    }
    return configuration;
  }
}

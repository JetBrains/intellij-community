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
package org.jetbrains.jps.incremental.fs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.IOUtil;
import gnu.trove.TObjectLongHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.TargetTypeRegistry;
import org.jetbrains.jps.incremental.storage.Timestamps;
import org.jetbrains.jps.model.JpsModel;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 4/20/12
 */
public class FSState {
  public static final int VERSION = 3;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.fs.FSState");
  private final Map<BuildTarget<?>, FilesDelta> myDeltas = Collections.synchronizedMap(new HashMap<BuildTarget<?>, FilesDelta>());
  private final Set<BuildTarget<?>> myInitialScanPerformed = Collections.synchronizedSet(new HashSet<BuildTarget<?>>());
  private final TObjectLongHashMap<File> myRegistrationStamps = new TObjectLongHashMap<File>(FileUtil.FILE_HASHING_STRATEGY);

  public void save(DataOutput out) throws IOException {
    MultiMap<BuildTargetType<?>, BuildTarget<?>> targetsByType = new MultiMap<BuildTargetType<?>, BuildTarget<?>>();
    for (BuildTarget<?> target : myInitialScanPerformed) {
      targetsByType.putValue(target.getTargetType(), target);
    }
    out.writeInt(targetsByType.size());
    for (BuildTargetType<?> type : targetsByType.keySet()) {
      IOUtil.writeString(type.getTypeId(), out);
      Collection<BuildTarget<?>> targets = targetsByType.get(type);
      out.writeInt(targets.size());
      for (BuildTarget<?> target : targets) {
        IOUtil.writeString(target.getId(), out);
        getDelta(target).save(out);
      }
    }
  }

  public void load(DataInputStream in, JpsModel model, final BuildRootIndex buildRootIndex) throws IOException {
    final TargetTypeRegistry registry = TargetTypeRegistry.getInstance();
    int typeCount = in.readInt();
    while (typeCount-- > 0) {
      final String typeId = IOUtil.readString(in);
      int targetCount = in.readInt();
      BuildTargetType<?> type = registry.getTargetType(typeId);
      BuildTargetLoader<?> loader = type != null ? type.createLoader(model) : null;
      while (targetCount-- > 0) {
        final String id = IOUtil.readString(in);
        boolean loaded = false;
        if (loader != null) {
          BuildTarget<?> target = loader.createTarget(id);
          if (target != null) {
            getDelta(target).load(in, target, buildRootIndex);
            myInitialScanPerformed.add(target);
            loaded = true;
          }
        }
        if (!loaded) {
          LOG.info("Skipping unknown target (typeId=" + typeId + ", type=" + type + ", id=" + id + ")");
          FilesDelta.skip(in);
        }
      }
    }
  }

  public void clearAll() {
    myInitialScanPerformed.clear();
    myDeltas.clear();
    myRegistrationStamps.clear();
  }

  public final void clearRecompile(final BuildRootDescriptor rd) {
    getDelta(rd.getTarget()).clearRecompile(rd);
  }

  public boolean markDirty(@Nullable CompileContext context, final File file, final BuildRootDescriptor rd, final @Nullable Timestamps tsStorage, boolean saveEventStamp) throws IOException {
    final boolean marked = getDelta(rd.getTarget()).markRecompile(rd, file);
    if (marked) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(rd.getTarget() + ": MARKED DIRTY: " + file.getPath());
      }
      if (saveEventStamp) {
        myRegistrationStamps.put(file, System.currentTimeMillis());
      }
      if (tsStorage != null) {
        tsStorage.removeStamp(file, rd.getTarget());
      }
    }
    else {
      if (LOG.isDebugEnabled()) {
        LOG.debug(rd.getTarget() + ": NOT MARKED DIRTY: " + file.getPath());
      }
    }
    return marked;
  }

  public long getEventRegistrationStamp(File file) {
    return myRegistrationStamps.get(file);
  }

  public boolean markDirtyIfNotDeleted(@Nullable CompileContext context,
                                       final File file,
                                       final BuildRootDescriptor rd,
                                       final @Nullable Timestamps tsStorage) throws IOException {
    final boolean marked = getDelta(rd.getTarget()).markRecompileIfNotDeleted(rd, file);
    if (marked && tsStorage != null) {
      tsStorage.removeStamp(file, rd.getTarget());
    }
    return marked;
  }

  public void registerDeleted(BuildTarget<?> target, final File file, @Nullable Timestamps tsStorage) throws IOException {
    registerDeleted(target, file);
    if (tsStorage != null) {
      tsStorage.removeStamp(file, target);
    }
  }

  public void registerDeleted(BuildTarget<?> target, File file) {
    getDelta(target).addDeleted(file);
  }

  public Map<BuildRootDescriptor, Set<File>> getSourcesToRecompile(@NotNull CompileContext context, BuildTarget<?> target) {
    return getDelta(target).getSourcesToRecompile();
  }

  public void clearDeletedPaths(BuildTarget<?> target) {
    final FilesDelta delta = myDeltas.get(target);
    if (delta != null) {
      delta.clearDeletedPaths();
    }
  }

  public Collection<String> getAndClearDeletedPaths(BuildTarget<?> target) {
    final FilesDelta delta = myDeltas.get(target);
    if (delta != null) {
      return delta.getAndClearDeletedPaths();
    }
    return Collections.emptyList();
  }

  @NotNull
  protected final FilesDelta getDelta(BuildTarget<?> buildTarget) {
    synchronized (myDeltas) {
      FilesDelta delta = myDeltas.get(buildTarget);
      if (delta == null) {
        delta = new FilesDelta();
        myDeltas.put(buildTarget, delta);
      }
      return delta;
    }
  }

  public boolean hasWorkToDo(BuildTarget<?> target) {
    if (!myInitialScanPerformed.contains(target)) return true;
    FilesDelta delta = myDeltas.get(target);
    return delta != null && delta.hasChanges();
  }

  public void markInitialScanPerformed(BuildTarget<?> target) {
    myInitialScanPerformed.add(target);
  }

  public boolean isInitialScanPerformed(BuildTarget<?> target) {
    return myInitialScanPerformed.contains(target);
  }
}

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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.IOUtil;
import gnu.trove.TObjectLongHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.storage.Timestamps;
import org.jetbrains.jps.model.JpsModel;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class BuildFSState {
  public static final int VERSION = 3;
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.fs.BuildFSState");
  private static final Key<Set<? extends BuildTarget<?>>> CONTEXT_TARGETS_KEY = Key.create("_fssfate_context_targets_");
  private static final Key<FilesDelta> NEXT_ROUND_DELTA_KEY = Key.create("_next_round_delta_");
  private static final Key<FilesDelta> CURRENT_ROUND_DELTA_KEY = Key.create("_current_round_delta_");

  // when true, will always determine dirty files by scanning FS and comparing timestamps
  // alternatively, when false, after first scan will rely on external notifications about changes
  private final boolean myAlwaysScanFS;
  private final Set<BuildTarget<?>> myInitialScanPerformed = Collections.synchronizedSet(new HashSet<BuildTarget<?>>());
  private final TObjectLongHashMap<File> myRegistrationStamps = new TObjectLongHashMap<>(FileUtil.FILE_HASHING_STRATEGY);
  private final Map<BuildTarget<?>, FilesDelta> myDeltas = Collections.synchronizedMap(new HashMap<BuildTarget<?>, FilesDelta>());

  public BuildFSState(boolean alwaysScanFS) {
    myAlwaysScanFS = alwaysScanFS;
  }

  public void save(DataOutput out) throws IOException {
    MultiMap<BuildTargetType<?>, BuildTarget<?>> targetsByType = new MultiMap<>();
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

  public final void clearRecompile(final BuildRootDescriptor rd) {
    getDelta(rd.getTarget()).clearRecompile(rd);
  }

  public long getEventRegistrationStamp(File file) {
    synchronized (myRegistrationStamps) {
      return myRegistrationStamps.get(file);
    }
  }

  public boolean hasWorkToDo(BuildTarget<?> target) {
    if (!myInitialScanPerformed.contains(target)) {
      return true;
    }
    FilesDelta delta = myDeltas.get(target);
    return delta != null && delta.hasChanges();
  }

  /**
   * @return true if there were changed files reported for the specified target, _after_ the target compilation had been started
   */
  public boolean hasUnprocessedChanges(@NotNull CompileContext context, @NotNull BuildTarget<?> target) {
    if (!myInitialScanPerformed.contains(target)) {
      return false;
    }
    final FilesDelta delta = myDeltas.get(target);
    if (delta == null) {
      return false;
    }
    final long targetBuildStart = context.getCompilationStartStamp(target);
    if (targetBuildStart <= 0L) {
      return false;
    }
    final CompileScope scope = context.getScope();
    final BuildRootIndex rootIndex = context.getProjectDescriptor().getBuildRootIndex();
    try {
      delta.lockData();
      for (Set<File> files : delta.getSourcesToRecompile().values()) {
        files_loop:
        for (File file : files) {
          if ((getEventRegistrationStamp(file) > targetBuildStart || FileSystemUtil.lastModified(file) > targetBuildStart) && scope.isAffected(target, file)) {
            for (BuildRootDescriptor rd : rootIndex.findAllParentDescriptors(file, context)) {
              if (rd.isGenerated()) { // do not send notification for generated sources
                continue files_loop;
              }
            }
            if (LOG.isDebugEnabled()) {
              LOG.debug("Unprocessed changes detected for target " + target +
                        "; file: " + file.getPath() +
                        "; targetBuildStart=" + targetBuildStart +
                        "; eventRegistrationStamp=" + getEventRegistrationStamp(file) +
                        "; lastModified=" + FileSystemUtil.lastModified(file)
              );
            }
            return true;
          }
        }
      }
    }
    finally {
      delta.unlockData();
    }
    return false;
  }

  public void markInitialScanPerformed(BuildTarget<?> target) {
    myInitialScanPerformed.add(target);
  }

  public void registerDeleted(@Nullable CompileContext context, BuildTarget<?> target, final File file, @Nullable Timestamps tsStorage) throws IOException {
    registerDeleted(context, target, file);
    if (tsStorage != null) {
      tsStorage.removeStamp(file, target);
    }
  }

  public void registerDeleted(@Nullable CompileContext context, BuildTarget<?> target, final File file) {
    final FilesDelta currentDelta = getRoundDelta(CURRENT_ROUND_DELTA_KEY, context);
    if (currentDelta != null) {
      currentDelta.addDeleted(file);
    }
    final FilesDelta nextDelta = getRoundDelta(NEXT_ROUND_DELTA_KEY, context);
    if (nextDelta != null) {
      nextDelta.addDeleted(file);
    }
    getDelta(target).addDeleted(file);
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
  private FilesDelta getDelta(BuildTarget<?> buildTarget) {
    synchronized (myDeltas) {
      FilesDelta delta = myDeltas.get(buildTarget);
      if (delta == null) {
        delta = new FilesDelta();
        myDeltas.put(buildTarget, delta);
      }
      return delta;
    }
  }


  public boolean isInitialScanPerformed(BuildTarget<?> target) {
    return !myAlwaysScanFS && myInitialScanPerformed.contains(target);
  }

  @NotNull
  public FilesDelta getEffectiveFilesDelta(@NotNull CompileContext context, BuildTarget<?> target) {
    if (target instanceof ModuleBuildTarget) {
      // multiple compilation rounds are applicable to ModuleBuildTarget only
      final FilesDelta lastRoundDelta = getRoundDelta(CURRENT_ROUND_DELTA_KEY, context);
      if (lastRoundDelta != null) {
        return lastRoundDelta;
      }
    }
    return getDelta(target);
  }

  public boolean isMarkedForRecompilation(@Nullable CompileContext context, CompilationRound round, BuildRootDescriptor rd, File file) {
    FilesDelta delta = getRoundDelta(round == CompilationRound.NEXT? NEXT_ROUND_DELTA_KEY : CURRENT_ROUND_DELTA_KEY, context);
    if (delta == null) {
      delta = getDelta(rd.getTarget());
    }
    
    return delta.isMarkedRecompile(rd, file);
  }

  /**
   * Note: marked file will well be visible as "dirty" only on the next compilation round!
   * @throws IOException
   */
  public final boolean markDirty(@Nullable CompileContext context, File file, final BuildRootDescriptor rd, @Nullable Timestamps tsStorage, boolean saveEventStamp) throws IOException {
    return markDirty(context, CompilationRound.NEXT, file, rd, tsStorage, saveEventStamp);
  }

  public boolean markDirty(@Nullable CompileContext context, CompilationRound round, File file, final BuildRootDescriptor rd, @Nullable Timestamps tsStorage, boolean saveEventStamp) throws IOException {
    final FilesDelta roundDelta = getRoundDelta(round == CompilationRound.NEXT? NEXT_ROUND_DELTA_KEY : CURRENT_ROUND_DELTA_KEY, context);
    if (roundDelta != null && isInCurrentContextTargets(context, rd)) {
      roundDelta.markRecompile(rd, file);
    }

    final FilesDelta filesDelta = getDelta(rd.getTarget());
    filesDelta.lockData();
    try {
      final boolean marked = filesDelta.markRecompile(rd, file);
      if (marked) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(rd.getTarget() + ": MARKED DIRTY: " + file.getPath());
        }
        if (saveEventStamp) {
          final long eventStamp = System.currentTimeMillis();
          synchronized (myRegistrationStamps) {
            myRegistrationStamps.put(file, eventStamp);
          }
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
    finally {
     filesDelta.unlockData();
    }
  }

  private static boolean isInCurrentContextTargets(CompileContext context, BuildRootDescriptor rd) {
    if (context == null) {
      return false;
    }
    Set<? extends BuildTarget<?>> targets = CONTEXT_TARGETS_KEY.get(context, Collections.emptySet());
    return targets.contains(rd.getTarget());
  }

  public boolean markDirtyIfNotDeleted(@Nullable CompileContext context, CompilationRound round, File file, final BuildRootDescriptor rd, @Nullable Timestamps tsStorage) throws IOException {
    final boolean marked = getDelta(rd.getTarget()).markRecompileIfNotDeleted(rd, file);
    if (marked && tsStorage != null) {
      tsStorage.removeStamp(file, rd.getTarget());
    }
    if (marked) {
      final FilesDelta roundDelta = getRoundDelta(round == CompilationRound.NEXT? NEXT_ROUND_DELTA_KEY : CURRENT_ROUND_DELTA_KEY, context);
      if (roundDelta != null) {
        if (isInCurrentContextTargets(context, rd)) {
          roundDelta.markRecompile(rd, file);
        }
      }
    }
    return marked;
  }

  public void clearAll() {
    clearContextRoundData(null);
    clearContextChunk(null);
    myInitialScanPerformed.clear();
    myDeltas.clear();
    synchronized (myRegistrationStamps) {
      myRegistrationStamps.clear();
    }
  }

  public void clearContextRoundData(@Nullable CompileContext context) {
    setRoundDelta(NEXT_ROUND_DELTA_KEY, context, null);
    setRoundDelta(CURRENT_ROUND_DELTA_KEY, context, null);
  }

  public void clearContextChunk(@Nullable CompileContext context) {
    setContextTargets(context, null);
  }

  public void beforeChunkBuildStart(@NotNull CompileContext context, BuildTargetChunk chunk) {
    setContextTargets(context, chunk.getTargets());
  }

  public void beforeNextRoundStart(@NotNull CompileContext context, ModuleChunk chunk) {
    FilesDelta currentDelta = getRoundDelta(NEXT_ROUND_DELTA_KEY, context);
    if (currentDelta == null) {
      // this is the initial round.
      // Need to make a snapshot of the FS state so that all builders in the chain see the same picture
      final List<FilesDelta> deltas = new SmartList<>();
      for (ModuleBuildTarget target : chunk.getTargets()) {
        deltas.add(getDelta(target));
      }
      currentDelta = new FilesDelta(deltas);
    }
    setRoundDelta(CURRENT_ROUND_DELTA_KEY, context, currentDelta);
    setRoundDelta(NEXT_ROUND_DELTA_KEY, context, new FilesDelta());
  }

  public <R extends BuildRootDescriptor, T extends BuildTarget<R>> boolean processFilesToRecompile(CompileContext context, final @NotNull T target, final FileProcessor<R, T> processor) throws IOException {
    final CompileScope scope = context.getScope();
    final FilesDelta delta = getEffectiveFilesDelta(context, target);
    delta.lockData();
    try {
      for (Map.Entry<BuildRootDescriptor, Set<File>> entry : delta.getSourcesToRecompile().entrySet()) {
        //noinspection unchecked
        R root = (R)entry.getKey();
        if (!target.equals(root.getTarget())) {
          // the data can contain roots from other targets (e.g. when compiling module cycles)
          continue;
        }
        for (File file : entry.getValue()) {
          if (!scope.isAffected(target, file)) {
            continue;
          }
          if (!processor.apply(target, file, root)) {
            return false;
          }
        }
      }
      return true;
    }
    finally {
      delta.unlockData();
    }
  }

  /**
   * @return true if marked something, false otherwise
   */
  public boolean markAllUpToDate(CompileContext context, final BuildRootDescriptor rd, final Timestamps stamps) throws IOException {
    boolean marked = false;
    final BuildTarget<?> target = rd.getTarget();
    final FilesDelta delta = getDelta(target);
    final long targetBuildStartStamp = context.getCompilationStartStamp(target);
    // prevent modifications to the data structure from external FS events
    delta.lockData();
    try {
      final Set<File> files = delta.clearRecompile(rd);
      if (files != null) {
        CompileScope scope = context.getScope();
        for (File file : files) {
          if (scope.isAffected(target, file)) {
            final long currentFileStamp = FileSystemUtil.lastModified(file);
            if (!rd.isGenerated() && (currentFileStamp > targetBuildStartStamp || getEventRegistrationStamp(file) > targetBuildStartStamp)) {
              // if the file was modified after the compilation had started,
              // do not save the stamp considering file dirty
              // Important!
              // Event registration stamp check is essential for the files that were actually changed _before_ targetBuildStart,
              // but corresponding change event was received and processed _after_ targetBuildStart
              if (Utils.IS_TEST_MODE) {
                LOG.info("Timestamp after compilation started; marking dirty again: " + file.getPath());
              }
              delta.markRecompile(rd, file);
            }
            else {
              marked = true;
              stamps.saveStamp(file, target, currentFileStamp);
            }
          }
          else {
            if (Utils.IS_TEST_MODE) {
              LOG.info("Not affected by compile scope; marking dirty again: " + file.getPath());
            }
            delta.markRecompile(rd, file);
          }
        }
      }
      return marked;
    }
    finally {
      delta.unlockData();
    }
  }

  private static void setContextTargets(@Nullable CompileContext context, @Nullable Set<? extends BuildTarget<?>> targets) {
    if (context != null) {
      CONTEXT_TARGETS_KEY.set(context, targets);
    }
  }

  @Nullable
  private static FilesDelta getRoundDelta(@NotNull Key<FilesDelta> key, @Nullable CompileContext context) {
    return context != null? key.get(context) : null;
  }

  private static void setRoundDelta(@NotNull Key<FilesDelta> key, @Nullable CompileContext context, @Nullable FilesDelta delta) {
    if (context != null) {
      key.set(context, delta);
    }
  }

}

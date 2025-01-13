// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.fs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.PathHashStrategy;
import com.intellij.util.io.IOUtil;
import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.storage.StampsStorage;
import org.jetbrains.jps.model.JpsModel;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public final class BuildFSState {
  public static final int VERSION = 3;
  private static final Logger LOG = Logger.getInstance(BuildFSState.class);
  private static final Key<Collection<? extends BuildTarget<?>>> CONTEXT_TARGETS_KEY = Key.create("_fssfate_context_targets_");
  @ApiStatus.Internal
  public static final Key<FilesDelta> NEXT_ROUND_DELTA_KEY = Key.create("_next_round_delta_");
  @ApiStatus.Internal
  public static final Key<FilesDelta> CURRENT_ROUND_DELTA_KEY = Key.create("_current_round_delta_");

  // when true, will always determine dirty files by scanning FS and comparing timestamps
  // alternatively, when false, after first scan will rely on external notifications about changes
  private final boolean alwaysScanFS;
  private final Set<BuildTarget<?>> initialScanPerformed = Collections.synchronizedSet(new HashSet<>());
  @SuppressWarnings("SSBasedInspection")
  private final Object2LongOpenCustomHashMap<Path> registrationStamps = new Object2LongOpenCustomHashMap<>(PathHashStrategy.INSTANCE);
  private final Map<BuildTarget<?>, FilesDelta> deltas = Collections.synchronizedMap(new HashMap<>());

  public BuildFSState(boolean alwaysScanFS) {
    this.alwaysScanFS = alwaysScanFS;
  }

  public void save(DataOutput out) throws IOException {
    MultiMap<BuildTargetType<?>, BuildTarget<?>> targetsByType = new MultiMap<>();
    for (BuildTarget<?> target : initialScanPerformed) {
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
            initialScanPerformed.add(target);
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

  public void clearRecompile(@NotNull BuildRootDescriptor rootDescriptor) {
    getDelta(rootDescriptor.getTarget()).clearRecompile(rootDescriptor);
  }

  public long getEventRegistrationStamp(@NotNull Path file) {
    synchronized (registrationStamps) {
      return registrationStamps.getLong(file);
    }
  }

  public boolean hasWorkToDo(@NotNull BuildTarget<?> target) {
    if (!initialScanPerformed.contains(target)) {
      return true;
    }

    FilesDelta delta = deltas.get(target);
    return delta != null && delta.hasChanges();
  }

  /**
   * @return true, if there were changed files reported for the specified target, _after_ the target compilation had been started
   */
  public boolean hasUnprocessedChanges(@NotNull CompileContext context, @NotNull BuildTarget<?> target) {
    if (!initialScanPerformed.contains(target)) {
      return false;
    }
    final FilesDelta delta = deltas.get(target);
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
      final long now = System.currentTimeMillis();
      for (Set<Path> files : delta.getSourceSetsToRecompile()) {
        files_loop:
        for (Path file : files) {
          long fileStamp;
          if (getEventRegistrationStamp(file) > targetBuildStart || (fileStamp = FSOperations.lastModified(file)) > targetBuildStart && fileStamp < now) {
            if (scope.isAffected(target, file)) {
              for (BuildRootDescriptor rd : rootIndex.findAllParentDescriptors(file.toFile(), context)) {
                if (rd.isGenerated()) { // do not send notification for generated sources
                  continue files_loop;
                }
              }
              if (LOG.isDebugEnabled()) {
                LOG.debug("Unprocessed changes detected for target " + target +
                            "; file: " + file +
                            "; targetBuildStart=" + targetBuildStart +
                            "; eventRegistrationStamp=" + getEventRegistrationStamp(file) +
                            "; lastModified=" + FSOperations.lastModified(file)
                );
              }
              return true;
            }
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
    initialScanPerformed.add(target);
  }

  public void registerDeleted(@Nullable CompileContext context,
                              BuildTarget<?> target,
                              @NotNull Path file,
                              @Nullable StampsStorage<?> stampStorage) throws IOException {
    registerDeleted(context, target, file);
    if (stampStorage != null) {
      stampStorage.removeStamp(file, target);
    }
  }

  public void registerDeleted(@Nullable CompileContext context, BuildTarget<?> target, @NotNull Path file) {
    FilesDelta currentDelta = getRoundDelta(CURRENT_ROUND_DELTA_KEY, context);
    if (currentDelta != null) {
      currentDelta.addDeleted(file);
    }
    FilesDelta nextDelta = getRoundDelta(NEXT_ROUND_DELTA_KEY, context);
    if (nextDelta != null) {
      nextDelta.addDeleted(file);
    }
    getDelta(target).addDeleted(file);
  }

  public void clearDeletedPaths(BuildTarget<?> target) {
    final FilesDelta delta = deltas.get(target);
    if (delta != null) {
      delta.clearDeletedPaths();
    }
  }

  public Collection<String> getAndClearDeletedPaths(BuildTarget<?> target) {
    FilesDelta delta = deltas.get(target);
    return delta == null ? List.of() : delta.getAndClearDeletedPaths();
  }

  @ApiStatus.Internal
  public @NotNull FilesDelta getDelta(@NotNull BuildTarget<?> buildTarget) {
    synchronized (deltas) {
      return deltas.computeIfAbsent(buildTarget, __ -> new FilesDelta());
    }
  }

  public boolean isInitialScanPerformed(BuildTarget<?> target) {
    return !alwaysScanFS && initialScanPerformed.contains(target);
  }

  @ApiStatus.Internal
  public @NotNull FilesDelta getEffectiveFilesDelta(@NotNull CompileContext context, BuildTarget<?> target) {
    if (target instanceof ModuleBuildTarget) {
      // multiple compilation rounds are applicable to ModuleBuildTarget only
      final FilesDelta lastRoundDelta = getRoundDelta(CURRENT_ROUND_DELTA_KEY, context);
      if (lastRoundDelta != null) {
        return lastRoundDelta;
      }
    }
    return getDelta(target);
  }

  public boolean isMarkedForRecompilation(@Nullable CompileContext context, CompilationRound round, BuildRootDescriptor rd, Path file) {
    FilesDelta delta = getRoundDelta(round == CompilationRound.NEXT? NEXT_ROUND_DELTA_KEY : CURRENT_ROUND_DELTA_KEY, context);
    if (delta == null) {
      delta = getDelta(rd.getTarget());
    }

    return delta.isMarkedRecompile(rd, file);
  }

  /**
   * Note: a marked file will well be visible as "dirty" only on the next compilation round!
   */
  public boolean markDirty(@Nullable CompileContext context,
                           @NotNull File file,
                           @NotNull BuildRootDescriptor buildRootDescriptor,
                           @Nullable StampsStorage<?> stampStorage,
                           boolean saveEventStamp) throws IOException {
    return markDirty(context, CompilationRound.NEXT, file.toPath(), buildRootDescriptor, stampStorage, saveEventStamp);
  }

  public boolean markDirty(@Nullable CompileContext context,
                           @NotNull Path file,
                           @NotNull BuildRootDescriptor buildRootDescriptor,
                           @Nullable StampsStorage<?> stampStorage,
                           boolean saveEventStamp) throws IOException {
    return markDirty(context, CompilationRound.NEXT, file, buildRootDescriptor, stampStorage, saveEventStamp);
  }

  /**
   * @deprecated Use {@link #markDirty(CompileContext, CompilationRound, Path, BuildRootDescriptor, StampsStorage, boolean)}
   */
  @Deprecated
  public boolean markDirty(@Nullable CompileContext context,
                             @NotNull CompilationRound round,
                             @NotNull File file,
                             @NotNull BuildRootDescriptor buildRootDescriptor,
                             @Nullable StampsStorage<?> stampStorage,
                             boolean saveEventStamp) throws IOException {
    return markDirty(context, round, file.toPath(), buildRootDescriptor, stampStorage, saveEventStamp);
  }

  public boolean markDirty(@Nullable CompileContext context,
                           @NotNull CompilationRound round,
                           @NotNull Path file,
                           @NotNull BuildRootDescriptor buildRootDescriptor,
                           @Nullable StampsStorage<?> stampStorage,
                           boolean saveEventStamp) throws IOException {
    FilesDelta roundDelta = getRoundDelta(round == CompilationRound.NEXT ? NEXT_ROUND_DELTA_KEY : CURRENT_ROUND_DELTA_KEY, context);
    BuildTarget<?> target = buildRootDescriptor.getTarget();
    if (roundDelta != null && isInCurrentContextTargets(context, target)) {
      roundDelta.markRecompile(buildRootDescriptor, file);
    }

    FilesDelta filesDelta = getDelta(target);
    filesDelta.lockData();
    try {
      boolean marked = filesDelta.markRecompile(buildRootDescriptor, file);
      if (marked) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(target + ": MARKED DIRTY: " + file);
        }
        if (saveEventStamp) {
          long eventStamp = System.currentTimeMillis();
          synchronized (registrationStamps) {
            registrationStamps.put(file, eventStamp);
          }
        }
        if (stampStorage != null) {
          stampStorage.removeStamp(file, target);
        }
      }
      else if (LOG.isDebugEnabled()) {
        LOG.debug(target + ": NOT MARKED DIRTY: " + file);
      }
      return marked;
    }
    finally {
     filesDelta.unlockData();
    }
  }

  private static boolean isInCurrentContextTargets(CompileContext context, BuildTarget<?> target) {
    return context != null && CONTEXT_TARGETS_KEY.get(context, Set.of()).contains(target);
  }

  public boolean markDirtyIfNotDeleted(@Nullable CompileContext context,
                                       CompilationRound round,
                                       Path file,
                                       @NotNull BuildRootDescriptor buildRootDescriptor,
                                       @Nullable StampsStorage<?> stampStorage) throws IOException {
    final boolean marked = getDelta(buildRootDescriptor.getTarget()).markRecompileIfNotDeleted(buildRootDescriptor, file);
    if (marked && stampStorage != null) {
      stampStorage.removeStamp(file, buildRootDescriptor.getTarget());
    }
    if (marked) {
      final FilesDelta roundDelta = getRoundDelta(round == CompilationRound.NEXT? NEXT_ROUND_DELTA_KEY : CURRENT_ROUND_DELTA_KEY, context);
      if (roundDelta != null) {
        if (isInCurrentContextTargets(context, buildRootDescriptor.getTarget())) {
          roundDelta.markRecompile(buildRootDescriptor, file);
        }
      }
    }
    return marked;
  }

  public void clearAll() {
    clearContextRoundData(null);
    clearContextChunk(null);
    initialScanPerformed.clear();
    deltas.clear();
    synchronized (registrationStamps) {
      registrationStamps.clear();
    }
  }

  public void clearContextRoundData(@Nullable CompileContext context) {
    setRoundDelta(NEXT_ROUND_DELTA_KEY, context, null);
    setRoundDelta(CURRENT_ROUND_DELTA_KEY, context, null);
  }

  public void clearContextChunk(@Nullable CompileContext context) {
    if (context != null) {
      CONTEXT_TARGETS_KEY.set(context, null);
    }
  }

  @ApiStatus.Internal
  public void beforeChunkBuildStart(@NotNull CompileContext context, @NotNull Set<? extends BuildTarget<?>> targets) {
    CONTEXT_TARGETS_KEY.set(context, targets);
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

  public <R extends BuildRootDescriptor, T extends BuildTarget<R>> boolean processFilesToRecompile(CompileContext context,
                                                                                                   @NotNull T target,
                                                                                                   FileProcessor<R, T> processor) throws IOException {
    final CompileScope scope = context.getScope();
    final FilesDelta delta = getEffectiveFilesDelta(context, target);
    delta.lockData();
    try {
      for (Map.Entry<BuildRootDescriptor, Set<Path>> entry : delta.getSourceMapToRecompile().entrySet()) {
        //noinspection unchecked
        R root = (R)entry.getKey();
        if (!target.equals(root.getTarget())) {
          // the data can contain roots from other targets (e.g., when compiling module cycles)
          continue;
        }
        for (Path file : entry.getValue()) {
          if (!scope.isAffected(target, file)) {
            continue;
          }
          if (!processor.apply(target, file.toFile(), root)) {
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
  public boolean markAllUpToDate(@NotNull CompileContext context,
                                 @NotNull BuildRootDescriptor buildRootDescriptor,
                                 @Nullable StampsStorage<?> stampStorage,
                                 long targetBuildStartStamp) throws IOException {
    boolean marked = false;
    final BuildTarget<?> target = buildRootDescriptor.getTarget();
    final FilesDelta delta = getDelta(target);
    // prevent modifications to the data structure from external FS events
    delta.lockData();
    try {
      Set<Path> files = delta.clearRecompile(buildRootDescriptor);
      if (files == null) {
        return marked;
      }

      CompileScope scope = context.getScope();
      for (Path file : files) {
        if (scope.isAffected(target, file)) {
          long currentFileTimestamp = FSOperations.lastModified(file);
          if (!buildRootDescriptor.isGenerated() && (currentFileTimestamp > targetBuildStartStamp || getEventRegistrationStamp(file) > targetBuildStartStamp)) {
            // if the file was modified after the compilation had started,
            // do not save the stamp considering a file dirty
            // Important!
            // Event registration stamp check is essential for the files that were actually changed _before_ targetBuildStart,
            // but the corresponding change event was received and processed _after_ targetBuildStart
            if (Utils.IS_TEST_MODE) {
              LOG.info("Timestamp after compilation started; marking dirty again: " + file);
            }
            delta.markRecompile(buildRootDescriptor, file);
          }
          else {
            marked = true;
            if (stampStorage != null) {
              stampStorage.updateStamp(file, target, currentFileTimestamp);
            }
          }
        }
        else {
          if (Utils.IS_TEST_MODE) {
            LOG.info("Not affected by compile scope; marking dirty again: " + file);
          }
          delta.markRecompile(buildRootDescriptor, file);
        }
      }
      return marked;
    }
    finally {
      delta.unlockData();
    }
  }

  private static @Nullable FilesDelta getRoundDelta(@NotNull Key<FilesDelta> key, @Nullable CompileContext context) {
    return context == null ? null : key.get(context);
  }

  private static void setRoundDelta(@NotNull Key<FilesDelta> key, @Nullable CompileContext context, @Nullable FilesDelta delta) {
    if (context != null) {
      key.set(context, delta);
    }
  }
}

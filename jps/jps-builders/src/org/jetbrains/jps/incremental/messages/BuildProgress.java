// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.messages;

import com.intellij.openapi.diagnostic.Logger;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.FreezeDetector;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetIndex;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.impl.BuildTargetChunk;
import org.jetbrains.jps.incremental.BuilderRegistry;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.FSOperations;
import org.jetbrains.jps.incremental.Utils;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.BuildTargetStateManager;

import java.util.*;
import java.util.function.Predicate;

/**
 * Estimates build time to provide information about build progress.
 * <p>
 * During build it remembers time required to fully rebuild a {@link BuildTarget} from scratch and computes average time for all targets
 * of each {@link BuildTargetType}. This information is used in subsequent builds to estimate what part of work is done.
 * </p>
 * <p>
 * For incremental build it'll take less time to build each target. However we assume that that time is proportional to time required to
 * fully rebuild a target from scratch and compute the progress accordingly.
 * </p>
 */
@SuppressWarnings("SSBasedInspection")
public final class BuildProgress {
  private static final Logger LOG = Logger.getInstance(BuildProgress.class);
  private final BuildDataManager dataManager;
  private final BuildTargetIndex targetIndex;
  //private final Object2IntOpenHashMap<BuildTargetType<?>> myNumberOfFinishedTargets = new Object2IntOpenHashMap<>();
  private final Object2LongMap<BuildTargetType<?>> expectedBuildTimeForTarget = new Object2LongOpenHashMap<>();
  /** sum of expected build time for all affected targets */
  private final long expectedTotalTime;
  /** maps a currently building target to part of work which was done for this target (value between 0.0 and 1.0) */
  private final Map<BuildTarget<?>, Double> currentProgress = new HashMap<>();
  /** sum of expected build time for all finished targets */
  private long expectedTimeForFinishedTargets;
  /** sum of all target build times for the current session*/
  private long absoluteBuildTime;

  @SuppressWarnings("SSBasedInspection")
  private final Object2IntOpenHashMap<BuildTargetType<?>> totalTargets = new Object2IntOpenHashMap<>();
  private final Object2LongOpenHashMap<BuildTargetType<?>> totalBuildTimeForFullyRebuiltTargets = new Object2LongOpenHashMap<>();
  private final Object2IntOpenHashMap<BuildTargetType<?>> numberOfFullyRebuiltTargets = new Object2IntOpenHashMap<>();
  private final FreezeDetector freezeDetector;

  public BuildProgress(BuildDataManager dataManager, BuildTargetIndex targetIndex, List<BuildTargetChunk> allChunks, Predicate<? super BuildTargetChunk> isAffected) {
    this.dataManager = dataManager;
    this.targetIndex = targetIndex;
    Set<BuildTargetType<?>> targetTypes = new LinkedHashSet<>();
    Object2IntOpenHashMap<BuildTargetType<?>> totalAffectedTargets = new Object2IntOpenHashMap<>();
    for (BuildTargetChunk chunk : allChunks) {
      boolean affected = isAffected.test(chunk);
      for (BuildTarget<?> target : chunk.getTargets()) {
        if (!this.targetIndex.isDummy(target)) {
          if (affected) {
            totalAffectedTargets.addTo(target.getTargetType(), 1);
            targetTypes.add(target.getTargetType());
          }
          totalTargets.addTo(target.getTargetType(), 1);
        }
      }
    }

    long expectedTotalTime = 0;
    for (BuildTargetType<?> targetType : targetTypes) {
      expectedBuildTimeForTarget.put(targetType, dataManager.getTargetStateManager().getAverageBuildTime(targetType));
    }
    for (BuildTargetType<?> type : targetTypes) {
      if (expectedBuildTimeForTarget.getLong(type) == -1) {
        expectedBuildTimeForTarget.put(type, computeExpectedTimeBasedOnOtherTargets(type, targetTypes, expectedBuildTimeForTarget));
      }
    }
    for (BuildTargetType<?> targetType : targetTypes) {
      expectedTotalTime += expectedBuildTimeForTarget.getLong(targetType) * totalAffectedTargets.getInt(targetType);
    }
    this.expectedTotalTime = Math.max(expectedTotalTime, 1);
    freezeDetector = new FreezeDetector();
    freezeDetector.start();
    if (LOG.isDebugEnabled()) {
      LOG.debug("expected total time is " + this.expectedTotalTime);
      for (BuildTargetType<?> type : targetTypes) {
        LOG.debug(" expected build time for " + type.getTypeId() + " is " + expectedBuildTimeForTarget.getLong(type));
      }
    }
  }

  /**
   * If there is no information about average build time for any {@link BuildTargetType} returns {@link BuilderRegistry#getExpectedBuildTimeForTarget the default expected value}.
   * Otherwise, estimate build time using real average time for other targets and ratio between the default expected times.
   */
  private static long computeExpectedTimeBasedOnOtherTargets(BuildTargetType<?> type,
                                                             Set<? extends BuildTargetType<?>> allTypes,
                                                             Object2LongMap<BuildTargetType<?>> expectedBuildTimeForTarget) {
    BuilderRegistry registry = BuilderRegistry.getInstance();
    int baseTargetsCount = 0;
    long expectedTimeSum = 0;
    for (BuildTargetType<?> anotherType : allTypes) {
      long realExpectedTime = expectedBuildTimeForTarget.getLong(anotherType);
      long defaultExpectedTime = registry.getExpectedBuildTimeForTarget(anotherType);
      if (realExpectedTime != -1 && defaultExpectedTime > 0) {
        baseTargetsCount++;
        expectedTimeSum += realExpectedTime * registry.getExpectedBuildTimeForTarget(type) / defaultExpectedTime;
      }
    }
    return baseTargetsCount == 0 ? registry.getExpectedBuildTimeForTarget(type) : expectedTimeSum / baseTargetsCount;
  }

  private synchronized void notifyAboutTotalProgress(CompileContext context) {
    double expectedTimeForFinishedWork = expectedTimeForFinishedTargets;
    for (Map.Entry<BuildTarget<?>, Double> entry : currentProgress.entrySet()) {
      expectedTimeForFinishedWork += expectedBuildTimeForTarget.getLong(entry.getKey().getTargetType()) * entry.getValue();
    }
    context.setDone((float)(expectedTimeForFinishedWork / expectedTotalTime));
  }

  public synchronized void updateProgress(BuildTarget<?> target, double done, CompileContext context) {
    currentProgress.put(target, done);
    notifyAboutTotalProgress(context);
  }

  @ApiStatus.Internal
  public synchronized void onTargetChunkFinished(@NotNull Collection<? extends BuildTarget<?>> targets, CompileContext context) {
    boolean successful = !Utils.errorsDetected(context) && !context.getCancelStatus().isCanceled();
    long nonDummyTargetsCount = targets.stream().filter(it -> !targetIndex.isDummy(it)).count();
    for (BuildTarget<?> target : targets) {
      currentProgress.remove(target);
      if (!targetIndex.isDummy(target)) {
        BuildTargetType<?> targetType = target.getTargetType();
        //myNumberOfFinishedTargets.addTo(targetType, 1);
        expectedTimeForFinishedTargets += expectedBuildTimeForTarget.getLong(targetType);

        long elapsedTime = freezeDetector.getAdjustedDuration(context.getCompilationStartStamp(target), System.currentTimeMillis());
        absoluteBuildTime += elapsedTime;
        
        if (successful && FSOperations.isMarkedDirty(context, target)) {
          long buildTime = elapsedTime / nonDummyTargetsCount;
          totalBuildTimeForFullyRebuiltTargets.addTo(targetType, buildTime);
          numberOfFullyRebuiltTargets.addTo(targetType, 1);
        }
      }
    }
    notifyAboutTotalProgress(context);
  }

  public void updateExpectedAverageTime() {
    freezeDetector.stop();
    if (LOG.isDebugEnabled()) {
      LOG.debug("update expected build time for " + totalBuildTimeForFullyRebuiltTargets.size() + " target types");
    }

    if (totalBuildTimeForFullyRebuiltTargets.isEmpty()) {
      return;
    }

    ObjectIterator<Object2LongMap.Entry<BuildTargetType<?>>> iterator = totalBuildTimeForFullyRebuiltTargets.object2LongEntrySet().fastIterator();
    BuildTargetStateManager targetStateManager = dataManager.getTargetStateManager();
    while (iterator.hasNext()) {
      Object2LongMap.Entry<BuildTargetType<?>> entry = iterator.next();
      BuildTargetType<?> type = entry.getKey();
      long totalTime = entry.getLongValue();
      long oldAverageTime = targetStateManager.getAverageBuildTime(type);
      long newAverageTime;
      if (oldAverageTime == -1) {
        newAverageTime = totalTime / numberOfFullyRebuiltTargets.getInt(type);
      }
      else {
        // if not all targets of this type were fully rebuilt, we assume that old average value is still actual for them;
        // this way we won't get incorrect value if only one small target was fully rebuilt
        newAverageTime = (totalTime + (totalTargets.getInt(type) - numberOfFullyRebuiltTargets.getInt(type)) * oldAverageTime) / totalTargets.getInt(type);
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug(" " + type.getTypeId() + ": old=" + oldAverageTime + ", new=" + newAverageTime + " (based on " + numberOfFullyRebuiltTargets.getInt(type)
                  + " of " + totalTargets.getInt(type) + " targets)");
      }
      targetStateManager.setAverageBuildTime(type, newAverageTime);
    }
  }

  public synchronized long getAbsoluteBuildTime() {
    return absoluteBuildTime;
  }
}

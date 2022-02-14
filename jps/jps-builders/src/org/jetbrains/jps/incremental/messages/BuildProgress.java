// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.messages;

import com.intellij.openapi.diagnostic.Logger;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
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
import org.jetbrains.jps.incremental.storage.BuildTargetsState;

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
  private final BuildDataManager myDataManager;
  private final BuildTargetIndex myTargetIndex;
  //private final Object2IntOpenHashMap<BuildTargetType<?>> myNumberOfFinishedTargets = new Object2IntOpenHashMap<>();
  private final Object2LongMap<BuildTargetType<?>> myExpectedBuildTimeForTarget = new Object2LongOpenHashMap<>();
  /** sum of expected build time for all affected targets */
  private final long myExpectedTotalTime;
  /** maps a currently building target to part of work which was done for this target (value between 0.0 and 1.0) */
  private final Map<BuildTarget<?>, Double> myCurrentProgress = new HashMap<>();
  /** sum of expected build time for all finished targets */
  private long myExpectedTimeForFinishedTargets;
  /** sum of all target build times for the current session*/
  private long myAbsoluteBuildTime;

  @SuppressWarnings("SSBasedInspection")
  private final Object2IntOpenHashMap<BuildTargetType<?>> myTotalTargets = new Object2IntOpenHashMap<>();
  private final Object2LongOpenHashMap<BuildTargetType<?>> myTotalBuildTimeForFullyRebuiltTargets = new Object2LongOpenHashMap<>();
  private final Object2IntOpenHashMap<BuildTargetType<?>> myNumberOfFullyRebuiltTargets = new Object2IntOpenHashMap<>();
  private final FreezeDetector myFreezeDetector;

  public BuildProgress(BuildDataManager dataManager, BuildTargetIndex targetIndex, List<BuildTargetChunk> allChunks, Predicate<? super BuildTargetChunk> isAffected) {
    myDataManager = dataManager;
    myTargetIndex = targetIndex;
    Set<BuildTargetType<?>> targetTypes = new LinkedHashSet<>();
    Object2IntOpenHashMap<BuildTargetType<?>> totalAffectedTargets = new Object2IntOpenHashMap<>();
    for (BuildTargetChunk chunk : allChunks) {
      boolean affected = isAffected.test(chunk);
      for (BuildTarget<?> target : chunk.getTargets()) {
        if (!myTargetIndex.isDummy(target)) {
          if (affected) {
            totalAffectedTargets.addTo(target.getTargetType(), 1);
            targetTypes.add(target.getTargetType());
          }
          myTotalTargets.addTo(target.getTargetType(), 1);
        }
      }
    }

    long expectedTotalTime = 0;
    for (BuildTargetType<?> targetType : targetTypes) {
      myExpectedBuildTimeForTarget.put(targetType, myDataManager.getTargetsState().getAverageBuildTime(targetType));
    }
    for (BuildTargetType<?> type : targetTypes) {
      if (myExpectedBuildTimeForTarget.getLong(type) == -1) {
        myExpectedBuildTimeForTarget.put(type, computeExpectedTimeBasedOnOtherTargets(type, targetTypes, myExpectedBuildTimeForTarget));
      }
    }
    for (BuildTargetType<?> targetType : targetTypes) {
      expectedTotalTime += myExpectedBuildTimeForTarget.getLong(targetType) * totalAffectedTargets.getInt(targetType);
    }
    myExpectedTotalTime = Math.max(expectedTotalTime, 1);
    myFreezeDetector = new FreezeDetector();
    myFreezeDetector.start();
    if (LOG.isDebugEnabled()) {
      LOG.debug("expected total time is " + myExpectedTotalTime);
      for (BuildTargetType<?> type : targetTypes) {
        LOG.debug(" expected build time for " + type.getTypeId() + " is " + myExpectedBuildTimeForTarget.getLong(type));
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
    return baseTargetsCount != 0 ? expectedTimeSum/baseTargetsCount : registry.getExpectedBuildTimeForTarget(type);
  }

  private synchronized void notifyAboutTotalProgress(CompileContext context) {
    long expectedTimeForFinishedWork = myExpectedTimeForFinishedTargets;
    for (Map.Entry<BuildTarget<?>, Double> entry : myCurrentProgress.entrySet()) {
      expectedTimeForFinishedWork += myExpectedBuildTimeForTarget.getLong(entry.getKey().getTargetType()) * entry.getValue();
    }
    float done = ((float)expectedTimeForFinishedWork) / myExpectedTotalTime;
    context.setDone(done);
  }

  public synchronized void updateProgress(BuildTarget<?> target, double done, CompileContext context) {
    myCurrentProgress.put(target, done);
    notifyAboutTotalProgress(context);
  }

  public synchronized void onTargetChunkFinished(BuildTargetChunk chunk, CompileContext context) {
    boolean successful = !Utils.errorsDetected(context) && !context.getCancelStatus().isCanceled();
    long nonDummyTargetsCount = chunk.getTargets().stream().filter(it -> !myTargetIndex.isDummy(it)).count();
    for (BuildTarget<?> target : chunk.getTargets()) {
      myCurrentProgress.remove(target);
      if (!myTargetIndex.isDummy(target)) {
        BuildTargetType<?> targetType = target.getTargetType();
        //myNumberOfFinishedTargets.addTo(targetType, 1);
        myExpectedTimeForFinishedTargets += myExpectedBuildTimeForTarget.getLong(targetType);

        long elapsedTime = myFreezeDetector.getAdjustedDuration(context.getCompilationStartStamp(target), System.currentTimeMillis());
        myAbsoluteBuildTime += elapsedTime;
        
        if (successful && FSOperations.isMarkedDirty(context, target)) {
          long buildTime = elapsedTime / nonDummyTargetsCount;
          myTotalBuildTimeForFullyRebuiltTargets.addTo(targetType, buildTime);
          myNumberOfFullyRebuiltTargets.addTo(targetType, 1);
        }
      }
    }
    notifyAboutTotalProgress(context);
  }

  public void updateExpectedAverageTime() {
    myFreezeDetector.stop();
    if (LOG.isDebugEnabled()) {
      LOG.debug("update expected build time for " + myTotalBuildTimeForFullyRebuiltTargets.size() + " target types");
    }

    myTotalBuildTimeForFullyRebuiltTargets.object2LongEntrySet().fastForEach(entry -> {
      BuildTargetType<?> type = entry.getKey();
      long totalTime = entry.getLongValue();
      BuildTargetsState targetsState = myDataManager.getTargetsState();
      long oldAverageTime = targetsState.getAverageBuildTime(type);
      long newAverageTime;
      if (oldAverageTime == -1) {
        newAverageTime = totalTime / myNumberOfFullyRebuiltTargets.getInt(type);
      }
      else {
        //if not all targets of this type were fully rebuilt, we assume that old average value is still actual for them; this way we won't get incorrect value if only one small target was fully rebuilt
        newAverageTime = (totalTime + (myTotalTargets.getInt(type) - myNumberOfFullyRebuiltTargets.getInt(type)) * oldAverageTime) / myTotalTargets.getInt(type);
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug(" " + type.getTypeId() + ": old=" + oldAverageTime + ", new=" + newAverageTime + " (based on " + myNumberOfFullyRebuiltTargets.getInt(type)
                  + " of " + myTotalTargets.getInt(type) + " targets)");
      }
      targetsState.setAverageBuildTime(type, newAverageTime);
    });
  }

  public synchronized long getAbsoluteBuildTime() {
    return myAbsoluteBuildTime;
  }
}

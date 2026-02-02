// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventField;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.eventLog.events.IntEventField;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.ui.treeStructure.ProjectView3rdPartyPluginUpdateCause;
import com.intellij.ui.treeStructure.ProjectViewUpdateCause;
import com.intellij.ui.treeStructure.ProjectViewUpdateCauseId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ProjectViewPerformanceCollector extends CounterUsagesCollector {

  private static final EventLogGroup GROUP = new EventLogGroup("project.view.performance", 4);

  private static final EventId1<Long> EXPAND_DIR_DURATION = GROUP.registerEvent("dir.expanded", EventFields.DurationMs);
  private static final EventId1<Long> CACHED_STATE_LOAD_DURATION = GROUP.registerEvent("cached.state.loaded", EventFields.DurationMs);
  private static final EventId1<Long> FULL_STATE_LOAD_DURATION = GROUP.registerEvent("full.state.loaded", EventFields.DurationMs);

  private static final EventField<ProjectViewUpdateCauseId> UPDATE_CAUSE_ID = EventFields.Enum("update_cause_id", ProjectViewUpdateCauseId.class);
  private static final IntEventField NODE_COUNT = EventFields.Int("node_count");

  private static final VarargEventId UPDATED = GROUP.registerVarargEvent(
    "updated",
    "Information about Project View updates per minute",
    UPDATE_CAUSE_ID,
    EventFields.PluginInfo,
    NODE_COUNT,
    EventFields.DurationMs
  );

  private static final IntEventField REQUEST_COUNT = EventFields.Int("request_count");

  private static final VarargEventId STUCK_REQUEST_DETECTED = GROUP.registerVarargEvent(
    "stuck.request.detected",
    "Some Project View update requests have been running for more than one minute",
    UPDATE_CAUSE_ID,
    EventFields.PluginInfo,
    REQUEST_COUNT,
    NODE_COUNT,
    EventFields.DurationMs
  );

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void logExpandDirDuration(long durationMs) {
    EXPAND_DIR_DURATION.log(durationMs);
  }

  public static void logCachedStateLoadDuration(long durationMs) {
    CACHED_STATE_LOAD_DURATION.log(durationMs);
  }

  public static void logFullStateLoadDuration(long durationMs) {
    FULL_STATE_LOAD_DURATION.log(durationMs);
  }

  public static void logUpdated(@NotNull ProjectViewUpdateCause cause, int nodeCount, long durationMs) {
    UPDATED.log(
      UPDATE_CAUSE_ID.with(cause.getId()),
      EventFields.PluginInfo.with(PluginInfoDetectorKt.getPluginInfoById(cause instanceof ProjectView3rdPartyPluginUpdateCause plugin ? plugin.getPluginId() : null)),
      NODE_COUNT.with(nodeCount),
      EventFields.DurationMs.with(durationMs)
    );
  }

  public static void logStuckUpdateRequest(@NotNull ProjectViewUpdateCause cause, int requestCount, int nodeCount, long durationMs) {
    STUCK_REQUEST_DETECTED.log(
      UPDATE_CAUSE_ID.with(cause.getId()),
      EventFields.PluginInfo.with(PluginInfoDetectorKt.getPluginInfoById(cause instanceof ProjectView3rdPartyPluginUpdateCause plugin ? plugin.getPluginId() : null)),
      REQUEST_COUNT.with(requestCount),
      NODE_COUNT.with(nodeCount),
      EventFields.DurationMs.with(durationMs)
    );
  }

}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl;

import com.intellij.diff.DiffTool;
import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.IgnorePolicy;
import com.intellij.diff.util.DiffPlaces;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public final class DiffUsageTriggerCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("vcs.diff.trigger", 8);
  private static final StringEventField DIFF_PLACE_FIELD = EventFields.String("diff_place",
                                                                              List.of(DiffPlaces.DEFAULT, DiffPlaces.CHANGES_VIEW,
                                                                                      DiffPlaces.VCS_LOG_VIEW, DiffPlaces.COMMIT_DIALOG,
                                                                                      DiffPlaces.TESTS_FAILED_ASSERTIONS, DiffPlaces.MERGE,
                                                                                      DiffPlaces.DIR_DIFF, DiffPlaces.EXTERNAL,
                                                                                     "unknown"));
  private static final EventId2<HighlightPolicy, String> TOGGLE_HIGHLIGHT_POLICY =
    GROUP.registerEvent("toggle.highlight.policy", EventFields.Enum("value", HighlightPolicy.class, value -> value.name()),
                        DIFF_PLACE_FIELD);
  private static final EventId2<IgnorePolicy, String> TOGGLE_IGNORE_POLICY =
    GROUP.registerEvent("toggle.ignore.policy", EventFields.Enum("value", IgnorePolicy.class, value -> value.name()),
                        DIFF_PLACE_FIELD);
  private static final ClassEventField DIFF_TOOL_CLASS = EventFields.Class("value");
  private static final EventId3<PluginInfo, Class<?>, String> TOGGLE_DIFF_TOOL =
    GROUP.registerEvent("toggle.diff.tool", EventFields.PluginInfo, DIFF_TOOL_CLASS, DIFF_PLACE_FIELD);

  private static final EventId TOGGLE_COMBINED_DIFF_BLOCK_COLLAPSE = GROUP.registerEvent("toggle.combined.diff.block.collapse");

  private static final EventId3<PluginInfo, Class<?>, String> SHOW_DIFF_TOOL =
    GROUP.registerEvent("show.diff.tool", EventFields.PluginInfo, DIFF_TOOL_CLASS, DIFF_PLACE_FIELD);

  private static final BooleanEventField IS_MERGE = EventFields.Boolean("is_merge");
  private static final EventId1<Boolean> SHOW_EXTERNAL_DIFF_TOOL = GROUP.registerEvent("show.external.diff.tool", IS_MERGE);

  private static final EventId MARKER_POPUP_SHOWN = GROUP.registerEvent("marker.popup.shown");

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void logToggleHighlightPolicy(@NotNull HighlightPolicy value, @Nullable @NonNls String place) {
    TOGGLE_HIGHLIGHT_POLICY.log(value, getPlaceName(place));
  }

  public static void logToggleIgnorePolicy(@NotNull IgnorePolicy value, @Nullable @NonNls String place) {
    TOGGLE_IGNORE_POLICY.log(value, getPlaceName(place));
  }

  public static void logToggleDiffTool(@Nullable Project project,
                                       @NotNull DiffTool diffTool,
                                       @Nullable @NonNls String place) {
    TOGGLE_DIFF_TOOL.log(project, PluginInfoDetectorKt.getPluginInfo(diffTool.getClass()), diffTool.getClass(), getPlaceName(place));
  }

  public static void logShowDiffTool(@Nullable Project project,
                                     @NotNull DiffTool diffTool,
                                     @Nullable @NonNls String place) {
    SHOW_DIFF_TOOL.log(project, PluginInfoDetectorKt.getPluginInfo(diffTool.getClass()), diffTool.getClass(), getPlaceName(place));
  }

  public static void logShowExternalTool(@Nullable Project project, boolean isMerge) {
    SHOW_EXTERNAL_DIFF_TOOL.log(project, isMerge);
  }

  public static void logShowCombinedDiffTool(@Nullable Project project,
                                             @NotNull DiffTool diffTool,
                                             @Nullable @NonNls String place) {
    SHOW_DIFF_TOOL.log(project, PluginInfoDetectorKt.getPluginInfo(diffTool.getClass()), diffTool.getClass(), getPlaceName(place));
  }

  public static void logToggleCombinedDiffBlockCollapse(@Nullable Project project) {
    TOGGLE_COMBINED_DIFF_BLOCK_COLLAPSE.log(project);
  }

  public static void logShowMarkerPopup(@Nullable Project project) {
    MARKER_POPUP_SHOWN.log(project);
  }

  private static @NotNull String getPlaceName(@NonNls @Nullable String place) {
    return StringUtil.notNullize(place, "unknown");
  }
}

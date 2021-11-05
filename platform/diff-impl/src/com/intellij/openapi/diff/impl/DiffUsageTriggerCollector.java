// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl;

import com.intellij.diff.DiffTool;
import com.intellij.diff.tools.util.base.HighlightPolicy;
import com.intellij.diff.tools.util.base.IgnorePolicy;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId2;
import com.intellij.internal.statistic.eventLog.events.EventId3;
import com.intellij.internal.statistic.eventLog.events.StringEventField;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DiffUsageTriggerCollector extends CounterUsagesCollector {
  private final static EventLogGroup GROUP = new EventLogGroup("vcs.diff.trigger", 3);
  private static final StringEventField DIFF_PLACE_FIELD = EventFields.String("diff_place",
                                                                             List.of("Default", "ChangesView", "VcsLogView", "CommitDialog",
                                                                                     "TestsFiledAssertions", "Merge", "DirDiff", "External",
                                                                                     "unknown"));
  private final static EventId2<HighlightPolicy, String> TOGGLE_HIGHLIGHT_POLICY =
    GROUP.registerEvent("toggle.highlight.policy", EventFields.Enum("value", HighlightPolicy.class, value -> value.name()),
                        DIFF_PLACE_FIELD);
  private final static EventId2<IgnorePolicy, String> TOGGLE_IGNORE_POLICY =
    GROUP.registerEvent("toggle.ignore.policy", EventFields.Enum("value", IgnorePolicy.class, value -> value.name()),
                        DIFF_PLACE_FIELD);
  private static final StringEventField DIFF_TOOL_NAME = EventFields.String("value",
                                                                            List.of("Side-by-side_viewer", "Binary_file_viewer", "Unified_viewer",
                                                                           "Error_viewer", "Patch_content_viewer", "Apply_patch_somehow",
                                                                           "Data_Diff_Viewer", "Database_Schema_Diff_Viewer",
                                                                           "Directory_viewer", "SVN_properties_viewer"));
  private final static EventId3<PluginInfo, String, String> TOGGLE_DIFF_TOOL =
    GROUP.registerEvent("toggle.diff.tool", EventFields.PluginInfo, DIFF_TOOL_NAME, DIFF_PLACE_FIELD);

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
    TOGGLE_DIFF_TOOL.log(project, PluginInfoDetectorKt.getPluginInfo(diffTool.getClass()), diffTool.getName(), getPlaceName(place));
  }

  @NotNull
  private static String getPlaceName(@NonNls @Nullable String place) {
    return StringUtil.notNullize(place, "unknown");
  }
}

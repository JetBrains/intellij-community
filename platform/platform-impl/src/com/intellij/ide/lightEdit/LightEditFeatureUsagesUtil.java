// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class LightEditFeatureUsagesUtil extends CounterUsagesCollector {

  private static final EventLogGroup GROUP = new EventLogGroup("light.edit", 3);
  private static final EventId1<OpenPlace> OPEN_FILE_EVENT_ID =
    GROUP.registerEvent("open.file", EventFields.Enum("open_place", OpenPlace.class));
  private static final EventId1<Boolean> AUTO_SAVE_MODE_EVENT_ID = GROUP.registerEvent("autosave.mode", EventFields.Boolean("enabled"));
  private static final EventId1<ProjectStatus> OPEN_IN_PROJECT_EVENT_ID =
    GROUP.registerEvent("open.in.project", EventFields.Enum("project_status", ProjectStatus.class));

  public enum OpenPlace {
    CommandLine,
    WelcomeScreenOpenAction,
    LightEditOpenAction,
    DragAndDrop,
    RecentFiles
  }

  public enum ProjectStatus {
    Open,
    Existing,
    New
  }

  private LightEditFeatureUsagesUtil() {
  }

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static void logFileOpen(@Nullable Project project, OpenPlace openPlace) {
    OPEN_FILE_EVENT_ID.log(project, openPlace);
  }

  public static void logAutosaveModeChanged(boolean isAutoSave) {
    AUTO_SAVE_MODE_EVENT_ID.log(isAutoSave);
  }

  public static void logOpenFileInProject(@Nullable Project project, ProjectStatus projectStatus) {
    OPEN_IN_PROJECT_EVENT_ID.log(project, projectStatus);
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;

public final class LightEditFeatureUsagesUtil {

  private final static String USAGE_GROUP_ID = "light.edit";

  private final static String OPEN_FILE_EVENT_ID    = "open.file";
  private final static String OPEN_FILE_EVENT_PLACE = "open_place";

  private final static String AUTOSAVE_MODE_EVENT_ID     = "autosave.mode";
  private final static String AUTOSAVE_MODE_ENABLED_FLAG = "enabled";

  private final static String OPEN_IN_PROJECT_EVENT_ID = "open.in.project";
  private final static String OPEN_IN_PROJECT_STATUS   = "project_status";

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

  public static void logFileOpen(OpenPlace openPlace) {
    FUCounterUsageLogger.getInstance().logEvent(
      USAGE_GROUP_ID,
      OPEN_FILE_EVENT_ID,
      new FeatureUsageData().addData(OPEN_FILE_EVENT_PLACE, openPlace.name()));
  }

  public static void logAutosaveModeChanged(boolean isAutosave) {
    FUCounterUsageLogger.getInstance().logEvent(
      USAGE_GROUP_ID,
      AUTOSAVE_MODE_EVENT_ID,
      new FeatureUsageData().addData(AUTOSAVE_MODE_ENABLED_FLAG, isAutosave));
  }

  public static void logOpenFileInProject(ProjectStatus projectStatus) {
    FUCounterUsageLogger.getInstance().logEvent(
      USAGE_GROUP_ID,
      OPEN_IN_PROJECT_EVENT_ID,
      new FeatureUsageData().addData(OPEN_IN_PROJECT_STATUS, projectStatus.name()));
  }

}

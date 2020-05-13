// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

public interface ToolWindowId {
  String COMMANDER = "Commander";
  String MESSAGES_WINDOW = "Messages";
  String PROJECT_VIEW = "Project";
  String STRUCTURE_VIEW = "Structure";
  String FAVORITES_VIEW = "Favorites";
  String ANT_BUILD = "Ant";
  String DEBUG = "Debug";
  String RUN = "Run";

  /**
   * @deprecated Use {@link com.intellij.build.BuildContentManager#getOrCreateToolWindow()}
   */
  @Deprecated
  String BUILD = "Build";

  String FIND = "Find";
  String HIERARCHY = "Hierarchy";
  String INSPECTION = "Inspection Results";
  String TODO_VIEW = "TODO";
  String DEPENDENCIES = "Dependency Viewer";
  String VCS = "Version Control";
  String MODULES_DEPENDENCIES = "Module Dependencies";
  String DUPLICATES = "Duplicates";
  String EXTRACT_METHOD = "Extract Method";
  String DOCUMENTATION = "Documentation";
  String TASKS = "Time Tracking";
  String DATABASE_VIEW = "Database";
  String PREVIEW = "Preview";
  String RUN_DASHBOARD = "Run Dashboard";
  String SERVICES = "Services";
  String ENDPOINTS = "Endpoints";
}
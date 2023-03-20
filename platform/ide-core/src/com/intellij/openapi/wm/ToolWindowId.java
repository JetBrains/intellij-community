// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm;

public interface ToolWindowId {
  String COMMANDER = "Commander";
  String MESSAGES_WINDOW = "Messages";
  String PROJECT_VIEW = "Project";
  String STRUCTURE_VIEW = "Structure";
  String FAVORITES_VIEW = "Favorites";
  String BOOKMARKS = "Bookmarks";
  String ANT_BUILD = "Ant";
  String DEBUG = "Debug";
  String RUN = "Run";

  /**
   * @deprecated Use {@link com.intellij.analysis.problemsView.toolWindow.ProblemsView}
   */
  @Deprecated(forRemoval = true)
  String INSPECTION = "Inspection Results";

  String FIND = "Find";
  String HIERARCHY = "Hierarchy";
  String TODO_VIEW = "TODO";
  String DEPENDENCIES = "Dependency Viewer";
  String VCS = "Version Control";
  String COMMIT = "Commit";
  String MODULES_DEPENDENCIES = "Module Dependencies";
  String DUPLICATES = "Duplicates";
  String EXTRACT_METHOD = "Extract Method";
  String DOCUMENTATION = "Documentation";
  String TASKS = "Time Tracking";
  String DATABASE_VIEW = "Database";
  String PREVIEW = "Preview";
  String SERVICES = "Services";
  String ENDPOINTS = "Endpoints";
  String MEET_NEW_UI = "Meet New UI";
}

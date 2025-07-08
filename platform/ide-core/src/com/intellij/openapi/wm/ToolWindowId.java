// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm;

import org.jetbrains.annotations.ApiStatus;

import java.util.Set;

public interface ToolWindowId {
  String COMMANDER = "Commander";
  String MESSAGES_WINDOW = "Messages";
  String PROJECT_VIEW = "Project";
  String STRUCTURE_VIEW = "Structure";
  String PROBLEMS_VIEW = "Problems View";
  String FAVORITES_VIEW = "Favorites";
  String BOOKMARKS = "Bookmarks";
  String ANT_BUILD = "Ant";
  /**
   * Please don't use it as a default debug executor, unless {@link com.intellij.execution.executors.DefaultDebugExecutor} is inaccessible.
   */
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
  String ANALYZE_DEPENDENCIES = "Dependency Viewer";
  String BUILD_DEPENDENCIES = "Dependencies";
  String VCS = "Version Control";
  String COMMIT = "Commit";
  String MODULES_DEPENDENCIES = "Module Dependencies";
  String DUPLICATES = "Duplicates";
  String EXTRACT_METHOD = "Extract Method";
  /**
   * @deprecated Not used in v2.
   * Consider using {@link com.intellij.platform.backend.documentation.DocumentationResult#asyncDocumentation}
   * instead of accessing the tool window directly.
   */
  @Deprecated(forRemoval = true)
  String DOCUMENTATION = "Documentation";
  String TASKS = "Time Tracking";
  String DATABASE_VIEW = "Database";
  String PREVIEW = "Preview";
  String SERVICES = "Services";
  String ENDPOINTS = "Endpoints";
  String MEET_NEW_UI = "Meet New UI";

  @ApiStatus.Internal
  Set<String> TOOL_WINDOW_IDS = Set.of(
    COMMANDER,
    MESSAGES_WINDOW,
    PROJECT_VIEW,
    STRUCTURE_VIEW,
    PROBLEMS_VIEW,
    FAVORITES_VIEW,
    BOOKMARKS,
    ANT_BUILD,
    DEBUG,
    RUN,
    FIND,
    HIERARCHY,
    TODO_VIEW,
    ANALYZE_DEPENDENCIES,
    BUILD_DEPENDENCIES,
    VCS,
    COMMIT,
    MODULES_DEPENDENCIES,
    DUPLICATES,
    EXTRACT_METHOD,
    TASKS,
    DATABASE_VIEW,
    PREVIEW,
    SERVICES,
    ENDPOINTS,
    MEET_NEW_UI
  );
}

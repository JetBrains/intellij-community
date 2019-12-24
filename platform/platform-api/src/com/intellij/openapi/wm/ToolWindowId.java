// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.ui.IdeUICustomization;
import com.intellij.ui.UIBundle;

public interface ToolWindowId {
  String COMMANDER = UIBundle.message("tool.window.name.commander");
  String MESSAGES_WINDOW = UIBundle.message("tool.window.name.messages");
  String PROJECT_VIEW = UIBundle.message("tool.window.name.project");
  String STRUCTURE_VIEW = UIBundle.message("tool.window.name.structure");
  String FAVORITES_VIEW = UIBundle.message("tool.window.name.favorites");
  String ANT_BUILD = UIBundle.message("tool.window.name.ant.build");
  String DEBUG = UIBundle.message("tool.window.name.debug");
  String RUN = UIBundle.message("tool.window.name.run");

  /**
   * @deprecated Use {@link com.intellij.build.BuildContentManager#getOrCreateToolWindow()}
   */
  @Deprecated
  String BUILD = UIBundle.message("tool.window.name.build");

  String FIND = UIBundle.message("tool.window.name.find");
  String CVS = UIBundle.message("tool.window.name.cvs");
  String HIERARCHY = UIBundle.message("tool.window.name.hierarchy");
  String INSPECTION = UIBundle.message("tool.window.name.inspection");
  String TODO_VIEW = UIBundle.message("tool.window.name.todo");
  String DEPENDENCIES = UIBundle.message("tool.window.name.dependency.viewer");
  String VCS = IdeUICustomization.getInstance().getVcsToolWindowName();
  String MODULES_DEPENDENCIES = UIBundle.message("tool.window.name.module.dependencies");
  String DUPLICATES = UIBundle.message("tool.window.name.module.duplicates");
  String EXTRACT_METHOD = UIBundle.message("tool.window.name.extract.method");
  String DOCUMENTATION = UIBundle.message("tool.window.name.documentation");
  String TASKS = UIBundle.message("tool.window.name.tasks");
  String DATABASE_VIEW = UIBundle.message("tool.window.name.database");
  String PREVIEW = UIBundle.message("tool.window.name.preview");
  String RUN_DASHBOARD = UIBundle.message("tool.window.name.run.dashboard");
  String SERVICES = UIBundle.message("tool.window.name.services");
}
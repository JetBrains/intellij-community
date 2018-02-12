/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm;

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
  String BUILD = UIBundle.message("tool.window.name.build");
  String FIND = UIBundle.message("tool.window.name.find");
  String CVS = UIBundle.message("tool.window.name.cvs");
  String HIERARCHY = UIBundle.message("tool.window.name.hierarchy");
  String INSPECTION = UIBundle.message("tool.window.name.inspection");
  String TODO_VIEW = UIBundle.message("tool.window.name.todo");
  String DEPENDENCIES = UIBundle.message("tool.window.name.dependency.viewer");
  String VCS = UIBundle.message("tool.window.name.version.control");
  String MODULES_DEPENDENCIES = UIBundle.message("tool.window.name.module.dependencies");
  String DUPLICATES = UIBundle.message("tool.window.name.module.duplicates");
  String DOCUMENTATION = UIBundle.message("tool.window.name.documentation");
  String TASKS = UIBundle.message("tool.window.name.tasks");
  String DATABASE_VIEW = UIBundle.message("tool.window.name.database");
  String PREVIEW = UIBundle.message("tool.window.name.preview");
  String RUN_DASHBOARD = UIBundle.message("tool.window.name.run.dashboard");
}
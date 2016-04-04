/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.util;

import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 4/16/13 11:44 AM
 */
public class ExternalSystemConstants {

  @NonNls @NotNull public static final String EXTERNAL_SYSTEM_ID_KEY  = "external.system.id";
  @NonNls @NotNull public static final String LINKED_PROJECT_PATH_KEY = "external.linked.project.path";
  @NonNls @NotNull public static final String ROOT_PROJECT_PATH_KEY = "external.root.project.path";
  @NonNls @NotNull public static final String LINKED_PROJECT_ID_KEY = "external.linked.project.id";

  @NonNls @NotNull public static final String EXTERNAL_SYSTEM_MODULE_TYPE_KEY = "external.system.module.type";
  @NonNls @NotNull public static final String EXTERNAL_SYSTEM_MODULE_GROUP_KEY  = "external.system.module.group";
  @NonNls @NotNull public static final String EXTERNAL_SYSTEM_MODULE_VERSION_KEY  = "external.system.module.version";

  @NonNls @NotNull public static final String TOOL_WINDOW_TOOLBAR_ACTIONS_GROUP_ID = "ExternalSystem.ToolWindow.Toolbar";
  @NonNls @NotNull public static final String TREE_ACTIONS_GROUP_ID                = "ExternalSystem.Tree.Context";

  @NonNls @NotNull public static final String TOOL_WINDOW_PLACE       = "ExternalSystem.ToolWindow";
  @NonNls @NotNull public static final String TREE_CONTEXT_MENU_PLACE = "ExternalSystem.Tree.Context.Menu";

  @NotNull @NonNls public static final String USE_IN_PROCESS_COMMUNICATION_REGISTRY_KEY_SUFFIX = ".system.in.process";
  @NotNull @NonNls public static final String EXTERNAL_SYSTEM_REMOTE_COMMUNICATION_MANAGER_DEBUG_PORT
    = "external.system.remote.communication.manager.debug.port";

  @NotNull public static final String DEBUG_RUNNER_ID = "ExternalSystemTaskDebugRunner";
  @NotNull public static final String RUNNER_ID       = "ExternalSystemTaskRunner";

  public static final boolean VERBOSE_PROCESSING       = SystemProperties.getBooleanProperty("external.system.verbose.processing", false);
  public static final int     RECENT_TASKS_NUMBER      = SystemProperties.getIntProperty("external.system.recent.tasks.number", 7);
  public static final int     AUTO_IMPORT_DELAY_MILLIS = SystemProperties.getIntProperty("external.system.auto.import.delay.ms", 3000);

  public static final char PATH_SEPARATOR = '/';

  // Order.
  public static final int BUILTIN_PROJECT_DATA_SERVICE_ORDER = Integer.MIN_VALUE;
  public static final int BUILTIN_MODULE_DATA_SERVICE_ORDER = BUILTIN_PROJECT_DATA_SERVICE_ORDER + 1;
  public static final int BUILTIN_LIBRARY_DATA_SERVICE_ORDER = BUILTIN_MODULE_DATA_SERVICE_ORDER + 1;
  public static final int BUILTIN_SERVICE_ORDER = BUILTIN_LIBRARY_DATA_SERVICE_ORDER + 1;
  public static final int BUILTIN_TOOL_WINDOW_SERVICE_ORDER = BUILTIN_SERVICE_ORDER + 1;
  public static final int UNORDERED = 1000;

  public static final int TEXT_FIELD_WIDTH_IN_COLUMNS = 20;
}

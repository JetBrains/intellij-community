// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.util;

import com.intellij.openapi.roots.ExternalProjectSystemRegistry;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class ExternalSystemConstants {
  @NonNls @NotNull public static final String EXTERNAL_SYSTEM_ID_KEY  = ExternalProjectSystemRegistry.EXTERNAL_SYSTEM_ID_KEY;

  @NotNull @NonNls public static final String USE_IN_PROCESS_COMMUNICATION_REGISTRY_KEY_SUFFIX = ".system.in.process";
  @NotNull @NonNls public static final String EXTERNAL_SYSTEM_REMOTE_COMMUNICATION_MANAGER_DEBUG_PORT
    = "external.system.remote.communication.manager.debug.port";

  @NotNull public static final String DEBUG_RUNNER_ID = "ExternalSystemTaskDebugRunner";
  @NotNull public static final String RUNNER_ID       = "ExternalSystemTaskRunner";

  public static final boolean VERBOSE_PROCESSING       = SystemProperties.getBooleanProperty("external.system.verbose.processing", false);

  public static final char PATH_SEPARATOR = '/';

  // Order.
  public static final int BUILTIN_PROJECT_DATA_SERVICE_ORDER = Integer.MIN_VALUE;
  public static final int BUILTIN_MODULE_DATA_SERVICE_ORDER = BUILTIN_PROJECT_DATA_SERVICE_ORDER + 10;
  public static final int BUILTIN_LIBRARY_DATA_SERVICE_ORDER = BUILTIN_MODULE_DATA_SERVICE_ORDER + 10;
  public static final int BUILTIN_SERVICE_ORDER = BUILTIN_LIBRARY_DATA_SERVICE_ORDER + 10;
  public static final int BUILTIN_TOOL_WINDOW_SERVICE_ORDER = BUILTIN_SERVICE_ORDER + 10;
  public static final int UNORDERED = 1000;
}

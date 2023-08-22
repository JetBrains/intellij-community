// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.debugger;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.rt.execution.ForkedDebuggerHelper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Provides ability to preconfigure tasks run by external system and to attach them with debugger.
 */
public interface DebuggerBackendExtension {
  ExtensionPointName<DebuggerBackendExtension> EP_NAME = ExtensionPointName.create("com.intellij.externalSystem.debuggerBackend");
  Key<String> RUNTIME_MODULE_DIR_KEY  = Key.create("RUNTIME_MODULE_DIR_KEY");

  String id();

  default List<String> initializationCode(@NotNull String dispatchPort, @NotNull String parameters) {
    return new ArrayList();
  }

  RunnerAndConfigurationSettings debugConfigurationSettings(@NotNull Project project,
                                                            @NotNull String processName,
                                                            @NotNull String processParameters);

  default HashMap<String, String> splitParameters(@NotNull String processParameters) {
    HashMap<String, String> result = new HashMap();

    final String[] envVars = processParameters.split(ForkedDebuggerHelper.PARAMETERS_SEPARATOR);
    for (String envVar : envVars) {
      final int idx = envVar.indexOf('=');
      if (idx > -1) {
        result.put(envVar.substring(0, idx), idx < envVar.length() - 1 ? envVar.substring(idx + 1) : "");
      }
    }

    return result;
  }
}
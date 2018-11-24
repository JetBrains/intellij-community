/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.dashboard;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;

/**
 * In order to show run configurations of the specific configuration type in Run Dashboard tool window,
 * one should register this extension.
 *
 * @author konstantin.aleev
 */
@ApiStatus.Experimental
public abstract class RunDashboardContributor {
  public static final ExtensionPointName<RunDashboardContributor> EP_NAME =
    ExtensionPointName.create("com.intellij.runDashboardContributor");

  private final ConfigurationType myType;

  protected RunDashboardContributor(ConfigurationType type) {
    myType = type;
  }

  /**
   * @return configuration type for which contributor is registered.
   */
  public ConfigurationType getType() {
    return myType;
  }

  public void updatePresentation(PresentationData presentation, DashboardNode node) {
  }

  /**
   * Returns node's status. Subclasses may override this method to provide custom statuses.
   *
   * @param node dashboard node
   * @return node's status. Returned status is used for grouping nodes by status.
   */
  public DashboardRunConfigurationStatus getStatus(DashboardRunConfigurationNode node) {
    RunContentDescriptor descriptor = node.getDescriptor();
    if (descriptor == null) {
      return DashboardRunConfigurationStatus.STOPPED;
    }
    ProcessHandler processHandler = descriptor.getProcessHandler();
    if (processHandler == null) {
      return DashboardRunConfigurationStatus.STOPPED;
    }
    Integer exitCode = processHandler.getExitCode();
    if (exitCode == null) {
      return DashboardRunConfigurationStatus.STARTED;
    }
    Boolean terminationRequested = processHandler.getUserData(ProcessHandler.TERMINATION_REQUESTED);
    if (exitCode == 0 || (terminationRequested != null && terminationRequested)) {
      return DashboardRunConfigurationStatus.STOPPED;
    }
    return DashboardRunConfigurationStatus.FAILED;
  }

  public static RunDashboardContributor getContributor(ConfigurationType type) {
    if (!Registry.is("ide.run.dashboard")) {
      return null;
    }

    if (type != null) {
      for (RunDashboardContributor contributor : EP_NAME.getExtensions()) {
        if (type.equals(contributor.getType())) {
          return contributor;
        }
      }
    }
    return null;
  }

  public static boolean isShowInDashboard(ConfigurationType type) {
    return getContributor(type) != null;
  }
}

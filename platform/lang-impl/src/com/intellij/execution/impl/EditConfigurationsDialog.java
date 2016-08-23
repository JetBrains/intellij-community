/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.execution.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.DumbModePermission;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class EditConfigurationsDialog extends SingleConfigurableEditor implements RunConfigurable.RunDialogBase {
  protected Executor myExecutor;

  public EditConfigurationsDialog(final Project project) {
    this(project, null);
  }

  public EditConfigurationsDialog(final Project project, @Nullable final ConfigurationFactory factory) {
    super(project, new RunConfigurable(project).selectConfigurableOnShow(factory == null), "#com.intellij.execution.impl.EditConfigurationsDialog", IdeModalityType.PROJECT);
    ((RunConfigurable)getConfigurable()).setRunDialog(this);
    setTitle(ExecutionBundle.message("run.debug.dialog.title"));
    setHorizontalStretch(1.3F);
    if (factory != null) {
      addRunConfiguration(factory);
    }
  }

  @Override
  public void show() {
    // run configurations don't support dumb mode yet, but some code inside them may trigger root change and start it
    // so let it be modal to prevent IndexNotReadyException from the configuration editors
    DumbService.allowStartingDumbModeInside(DumbModePermission.MAY_START_MODAL, () -> super.show());
  }

  public void addRunConfiguration(@NotNull final ConfigurationFactory factory) {
    final RunConfigurable configurable = (RunConfigurable)getConfigurable();
    final SingleConfigurationConfigurable<RunConfiguration> configuration = configurable.createNewConfiguration(factory);

    if (!isVisible()) {
       getContentPanel().addComponentListener(new ComponentAdapter() {
         @Override
         public void componentShown(ComponentEvent e) {
           if (configuration != null) {
             configurable.updateRightPanel(configuration);
             getContentPanel().removeComponentListener(this);
           }
         }
       });
    }
  }

  @Override
  protected void doOKAction() {
    RunConfigurable configurable = (RunConfigurable)getConfigurable();
    super.doOKAction();
    if (isOK()) {
      // if configurable was not modified, apply was not called and Run Configurable has not called 'updateActiveConfigurationFromSelected'
      configurable.updateActiveConfigurationFromSelected();
    }
  }

  @Nullable
  @Override
  public Executor getExecutor() {
    return myExecutor;
  }

  @Override
  public void setOKActionEnabled(boolean isEnabled) {
    super.setOKActionEnabled(isEnabled);
  }
}
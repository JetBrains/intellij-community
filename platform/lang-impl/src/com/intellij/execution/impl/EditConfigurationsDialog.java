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
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public class EditConfigurationsDialog extends SingleConfigurableEditor implements RunConfigurable.RunDialogBase {
  protected Executor myExecutor;

  public EditConfigurationsDialog(final Project project) {
    super(project, new RunConfigurable(project), IdeModalityType.PROJECT);
    ((RunConfigurable)getConfigurable()).setRunDialog(this);
    setTitle(ExecutionBundle.message("run.debug.dialog.title"));
    setHorizontalStretch(1.3F);
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

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.execution.impl.EditConfigurationsDialog";
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
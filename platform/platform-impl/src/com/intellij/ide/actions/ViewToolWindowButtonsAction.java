
/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;

public class ViewToolWindowButtonsAction extends ToggleAction implements DumbAware {
  public ViewToolWindowButtonsAction() {
    super("Show Tool Buttons");
  }

  public boolean isSelected(AnActionEvent event) {
    return !UISettings.getInstance().HIDE_TOOL_STRIPES;
  }

  public void setSelected(AnActionEvent event,boolean state) {
    UISettings uiSettings = UISettings.getInstance();
    uiSettings.HIDE_TOOL_STRIPES=!state;
    uiSettings.fireUISettingsChanged();
  }
}

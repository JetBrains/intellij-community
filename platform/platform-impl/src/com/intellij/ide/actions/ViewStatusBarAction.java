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

package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;

public class ViewStatusBarAction extends ToggleAction implements DumbAware {
  public boolean isSelected(AnActionEvent e){
    return UISettings.getInstance().SHOW_STATUS_BAR;
  }

  public void setSelected(AnActionEvent e,boolean state){
    UISettings uiSettings = UISettings.getInstance();
    uiSettings.SHOW_STATUS_BAR=state;
    uiSettings.fireUISettingsChanged();
  }
}

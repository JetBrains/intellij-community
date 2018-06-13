/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.ide.actions;

import com.intellij.ide.plugins.PluginManagerConfigurableProxy;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;

import java.awt.*;

public class ShowPluginManagerAction extends AnAction implements DumbAware {

  @Override
  public void actionPerformed(AnActionEvent e) {
    Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
    ShowSettingsUtil.getInstance().editConfigurable(component, new PluginManagerConfigurableProxy());
  }
}

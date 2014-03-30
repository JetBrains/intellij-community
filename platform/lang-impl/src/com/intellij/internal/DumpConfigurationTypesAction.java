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

/*
 * User: anna
 * Date: 28-Jun-2007
 */
package com.intellij.internal;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

public class DumpConfigurationTypesAction extends AnAction implements DumbAware {
  public DumpConfigurationTypesAction() {
    super("Dump Configurations");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    final ConfigurationType[] factories =
      RunManager.getInstance(project).getConfigurationFactories();
    for (ConfigurationType factory : factories) {
      System.out.println(factory.getDisplayName() + " : " + factory.getId());
    }
  }

  @Override
  public void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(e.getData(CommonDataKeys.PROJECT) != null);
  }
}
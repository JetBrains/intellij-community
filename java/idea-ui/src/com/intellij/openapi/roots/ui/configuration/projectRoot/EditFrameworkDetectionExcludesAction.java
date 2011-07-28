/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.framework.detection.DetectionExcludesConfiguration;
import com.intellij.framework.detection.impl.exclude.DetectionExcludesConfigurable;
import com.intellij.framework.detection.impl.exclude.DetectionExcludesConfigurationImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class EditFrameworkDetectionExcludesAction extends DumbAwareAction {
  private final Project myProject;

  public EditFrameworkDetectionExcludesAction(@NotNull Project project) {
    super("Edit Detection Excludes...", null, PlatformIcons.EDIT);
    myProject = project;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DetectionExcludesConfigurationImpl configuration = (DetectionExcludesConfigurationImpl)DetectionExcludesConfiguration.getInstance(myProject);
    ShowSettingsUtil.getInstance().editConfigurable(myProject, new DetectionExcludesConfigurable(myProject, configuration));
  }
}

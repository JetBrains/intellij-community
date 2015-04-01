/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;

/**
 * @author Dmitry Batkovich
 */
public class SeverityEditorDialogAction extends AnAction implements DumbAware {

  public SeverityEditorDialogAction() {
    super("Show Severities Editor");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    final SeverityRegistrar severityRegistrar = SeverityRegistrar.getSeverityRegistrar(project);
    final Component component = PlatformDataKeys.CONTEXT_COMPONENT.getData(e.getDataContext());
    if (component instanceof JComponent) {
      final SeverityEditorDialog dialog =
        new SeverityEditorDialog((JComponent)component, null, severityRegistrar, false);
      dialog.show();
    }
  }
}

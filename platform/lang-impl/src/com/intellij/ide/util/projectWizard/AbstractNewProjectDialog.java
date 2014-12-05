/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.impl.welcomeScreen.CardActionsPanel;
import com.intellij.platform.DirectoryProjectGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Dennis.Ushakov
 */
public abstract class AbstractNewProjectDialog extends DialogWrapper {
  public AbstractNewProjectDialog() {
    super(ProjectManager.getInstance().getDefaultProject());
    setTitle(" "); // hack to make native fileChooser work on Mac. See MacFileChooserDialogImpl.MAIN_THREAD_RUNNABLE
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    final Runnable runnable = new Runnable() {
      @Override
      public void run() {
        close(OK_EXIT_CODE);
      }
    };
    final DirectoryProjectGenerator[] generators = Extensions.getExtensions(DirectoryProjectGenerator.EP_NAME);
    final DefaultActionGroup root = createRootStep(runnable, generators.length == 0 ? "Create Project" : "Select Project Type");

    return new CardActionsPanel(root) {

      @Override
      public Dimension getPreferredSize() {
        return getMinimumSize();
      }

      @Override
      public Dimension getMinimumSize() {
        if (generators.length == 0) return new Dimension(550, 200);
        return new Dimension(650, 450);
      }
    };
  }

  protected abstract DefaultActionGroup createRootStep(Runnable runnable, final String name);

  @Override
  protected String getHelpId() {
    return null;
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[0];
  }
}

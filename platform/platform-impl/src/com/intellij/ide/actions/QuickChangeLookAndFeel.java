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
package com.intellij.ide.actions;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.laf.darcula.DarculaInstaller;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author max
 */
public class QuickChangeLookAndFeel extends QuickSwitchSchemeAction {

  protected void fillActions(Project project, @NotNull DefaultActionGroup group, @NotNull DataContext dataContext) {
    final LafManager lafMan = LafManager.getInstance();
    final UIManager.LookAndFeelInfo[] lfs = lafMan.getInstalledLookAndFeels();
    final UIManager.LookAndFeelInfo current = lafMan.getCurrentLookAndFeel();
    for (final UIManager.LookAndFeelInfo lf : lfs) {
      group.add(new DumbAwareAction(lf.getName(), "", lf == current ? ourCurrentAction : ourNotCurrentAction) {
        public void actionPerformed(AnActionEvent e) {
          UIManager.LookAndFeelInfo cur = lafMan.getCurrentLookAndFeel();
          if (cur == lf) return;
          boolean wasDarcula = UIUtil.isUnderDarcula();
          lafMan.setCurrentLookAndFeel(lf);
          // hack not to updateUI twice: here and in DarculaInstaller
          final AtomicBoolean updated = new AtomicBoolean(false);
          LafManagerListener listener = (s) -> updated.set(true);
          lafMan.addLafManagerListener(listener);
          try {
            if (UIUtil.isUnderDarcula()) {
              DarculaInstaller.install();
            }
            else if (wasDarcula) {
              DarculaInstaller.uninstall();
            }
          }
          finally {
            lafMan.removeLafManagerListener(listener);
            if (!updated.get()) {
              lafMan.updateUI();
            }
          }
        }
      });
    }
  }

  protected boolean isEnabled() {
    return LafManager.getInstance().getInstalledLookAndFeels().length > 1;
  }
}

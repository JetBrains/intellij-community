// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.laf.darcula.DarculaInstaller;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author max
 */
public class QuickChangeLookAndFeel extends QuickSwitchSchemeAction {

  protected void fillActions(Project project, @NotNull DefaultActionGroup group, @NotNull DataContext dataContext) {
    LafManager lafMan = LafManager.getInstance();
    UIManager.LookAndFeelInfo[] lfs = lafMan.getInstalledLookAndFeels();
    UIManager.LookAndFeelInfo current = lafMan.getCurrentLookAndFeel();
    for (UIManager.LookAndFeelInfo lf : lfs) {
      group.add(new DumbAwareAction(lf.getName(), "", lf == current ? ourCurrentAction : ourNotCurrentAction) {
        public void actionPerformed(AnActionEvent e) {
          switchLafAndUpdateUI(lafMan, lf, false);
        }
      });
    }
  }

  public static void switchLafAndUpdateUI(@NotNull final LafManager lafMan, @NotNull UIManager.LookAndFeelInfo lf, boolean async) {
    UIManager.LookAndFeelInfo cur = lafMan.getCurrentLookAndFeel();
    if (cur == lf) return;

    final boolean wasDarcula = UIUtil.isUnderDarcula();
    lafMan.setCurrentLookAndFeel(lf);

    if (StringUtil.equals(cur != null ? cur.getClassName() : null, lf.getClassName())) return;
    Runnable updater = () -> {
      // a twist not to updateUI twice: here and in DarculaInstaller
      // double updateUI shall be avoided and causes NPE in some components (HelpView)
      Ref<Boolean> updated = Ref.create(false);
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
    };
    if (async) {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(updater);
    }
    else {
      updater.run();
    }
  }

  protected boolean isEnabled() {
    return LafManager.getInstance().getInstalledLookAndFeels().length > 1;
  }
}

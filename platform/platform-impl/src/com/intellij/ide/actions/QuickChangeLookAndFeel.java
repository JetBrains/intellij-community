// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.laf.darcula.DarculaInstaller;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author max
 */
public class QuickChangeLookAndFeel extends QuickSwitchSchemeAction {

  @Override
  protected void fillActions(Project project, @NotNull DefaultActionGroup group, @NotNull DataContext dataContext) {
    LafManager lafMan = LafManager.getInstance();
    UIManager.LookAndFeelInfo[] lfs = lafMan.getInstalledLookAndFeels();
    UIManager.LookAndFeelInfo current = lafMan.getCurrentLookAndFeel();
    for (UIManager.LookAndFeelInfo lf : lfs) {
      group.add(new DumbAwareAction(lf.getName(), "", lf == current ? AllIcons.Actions.Forward : ourNotCurrentAction) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          switchLafAndUpdateUI(lafMan, lf, false);
        }
      });
    }
  }

  public static void switchLafAndUpdateUI(@NotNull final LafManager lafMan, @NotNull UIManager.LookAndFeelInfo lf, boolean async) {
    UIManager.LookAndFeelInfo cur = lafMan.getCurrentLookAndFeel();
    if (cur == lf) return;
    ChangeLAFAnimator animator = Registry.is("ide.intellij.laf.enable.animation") ? ChangeLAFAnimator.showSnapshot() : null;

    final boolean wasDarcula = StartupUiUtil.isUnderDarcula();
    lafMan.setCurrentLookAndFeel(lf);

    Runnable updater = () -> {
      // a twist not to updateUI twice: here and in DarculaInstaller
      // double updateUI shall be avoided and causes NPE in some components (HelpView)
      Ref<Boolean> updated = Ref.create(false);
      Disposable disposable = Disposer.newDisposable();
      ApplicationManager.getApplication().getMessageBus().connect(disposable).subscribe(LafManagerListener.TOPIC, source -> updated.set(true));
      try {
        if (StartupUiUtil.isUnderDarcula()) {
          DarculaInstaller.install();
        }
        else if (wasDarcula) {
          DarculaInstaller.uninstall();
        }
      }
      finally {
        Disposer.dispose(disposable);
        if (!updated.get()) {
          lafMan.updateUI();
        }
        if (animator != null) {
          animator.hideSnapshotWithAnimation();
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

  @Override
  protected boolean isEnabled() {
    return LafManager.getInstance().getInstalledLookAndFeels().length > 1;
  }
}

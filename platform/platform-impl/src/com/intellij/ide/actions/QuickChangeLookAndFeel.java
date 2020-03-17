// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.laf.darcula.DarculaInstaller;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author max
 */
public class QuickChangeLookAndFeel extends QuickSwitchSchemeAction {

  @Override
  protected void fillActions(Project project, @NotNull DefaultActionGroup group, @NotNull DataContext dataContext) {
    LafManager lafMan = LafManager.getInstance();
    UIManager.LookAndFeelInfo[] lfs = lafMan.getInstalledLookAndFeels();
    UIManager.LookAndFeelInfo currentLaf = lafMan.getCurrentLookAndFeel();

    for (UIManager.LookAndFeelInfo lf : lfs) {
      group.add(new LafChangeAction(lf, currentLaf == lf));
    }
  }

  @Override
  protected void showPopup(AnActionEvent e, ListPopup popup) {
    if (Registry.is("ide.instant.theme.switch")) {
      LafManager lafMan = LafManager.getInstance();
      popup.addListSelectionListener(event -> {
        JList list = (JList)event.getSource();
        Object item = list.getSelectedValue();
        if (item instanceof AnActionHolder) {
          LafChangeAction action = (LafChangeAction)((AnActionHolder)item).getAction();
          switchLafAndUpdateUI(lafMan, action.myLookAndFeelInfo, false);
        }
      });
    }

    super.showPopup(e, popup);
  }

  @Override
  @Nullable
  protected Condition<? super AnAction> preselectAction() {
    LafManager lafMan = LafManager.getInstance();
    return (a) -> ((LafChangeAction)a).myLookAndFeelInfo == lafMan.getCurrentLookAndFeel();
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

  private static class LafChangeAction extends DumbAwareAction {
    private final UIManager.LookAndFeelInfo myLookAndFeelInfo;

    private LafChangeAction(UIManager.LookAndFeelInfo lf, boolean currentLaf) {
      super(lf.getName(), null, getIcon(currentLaf));
      myLookAndFeelInfo = lf;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (!Registry.is("ide.instant.theme.switch")) {
        switchLafAndUpdateUI(LafManager.getInstance(), myLookAndFeelInfo, false);
      }
    }

    @Nullable
    private static Icon getIcon(boolean currentLaf) {
      return Registry.is("ide.instant.theme.switch") ? null : currentLaf ? AllIcons.Actions.Forward : ourNotCurrentAction;
    }
  }
}

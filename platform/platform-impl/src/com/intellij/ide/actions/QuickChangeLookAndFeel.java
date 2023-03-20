// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.ThemesListProvider;
import com.intellij.ide.ui.laf.darcula.DarculaInstaller;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.Alarm;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class QuickChangeLookAndFeel extends QuickSwitchSchemeAction {
  private final Alarm switchAlarm = new Alarm();

  @Override
  protected void fillActions(Project project, @NotNull DefaultActionGroup group, @NotNull DataContext dataContext) {
    UIManager.LookAndFeelInfo initialLaf = LafManager.getInstance().getCurrentLookAndFeel();

    for (List<UIManager.LookAndFeelInfo> list : ThemesListProvider.getInstance().getShownThemes()) {
      if (group.getChildrenCount() > 0) group.addSeparator();
      for (UIManager.LookAndFeelInfo lf : list) group.add(new LafChangeAction(lf, initialLaf == lf));
    }

    group.addSeparator();
    group.add(new ShowPluginsWithSearchOptionAction(IdeBundle.message("laf.action.install.theme"), "/tag:Theme") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        switchLafAndUpdateUI(LafManager.getInstance(), initialLaf, false);
        super.actionPerformed(e);
      }
    });
  }

  @Override
  protected void showPopup(AnActionEvent e, ListPopup popup) {
    UIManager.LookAndFeelInfo initialLaf = LafManager.getInstance().getCurrentLookAndFeel();

    switchAlarm.cancelAllRequests();
    if (Registry.is("ide.instant.theme.switch")) {
      popup.addListSelectionListener(event -> {
        Object item = ((JList<?>)event.getSource()).getSelectedValue();
        if (item instanceof AnActionHolder) {
          AnAction anAction = ((AnActionHolder)item).getAction();
          if (anAction instanceof LafChangeAction) {
            switchAlarm.cancelAllRequests();
            switchAlarm.addRequest(() -> {
              LafChangeAction action = (LafChangeAction)anAction;
              switchLafAndUpdateUI(LafManager.getInstance(), action.myLookAndFeelInfo, false);
            }, Registry.get("ide.instant.theme.switch.delay").asInteger());
          }
        }
      });
    }

    popup.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        switchAlarm.cancelAllRequests();
        if (Registry.is("ide.instant.theme.switch") && !event.isOk()) {
          switchLafAndUpdateUI(LafManager.getInstance(), initialLaf, false);
        }
      }
    });

    super.showPopup(e, popup);
  }

  @Override
  @Nullable
  protected Condition<? super AnAction> preselectAction() {
    LafManager lafMan = LafManager.getInstance();
    return (a) -> (a instanceof LafChangeAction) && ((LafChangeAction)a).myLookAndFeelInfo == lafMan.getCurrentLookAndFeel();
  }

  public static void switchLafAndUpdateUI(@NotNull final LafManager lafMan, @NotNull UIManager.LookAndFeelInfo lf, boolean async) {
    switchLafAndUpdateUI(lafMan, lf, async, false);
  }

  public static void switchLafAndUpdateUI(@NotNull final LafManager lafMan, @NotNull UIManager.LookAndFeelInfo lf, boolean async, boolean force) {
    UIManager.LookAndFeelInfo cur = lafMan.getCurrentLookAndFeel();
    if (!force && cur == lf) return;
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
    LafManager lafManager = LafManager.getInstance();
    return lafManager.getInstalledLookAndFeels().length > 1 && !lafManager.getAutodetect();
  }

  private static final class LafChangeAction extends DumbAwareAction {
    private final UIManager.LookAndFeelInfo myLookAndFeelInfo;

    private LafChangeAction(UIManager.LookAndFeelInfo lf, boolean currentLaf) {
      //noinspection HardCodedStringLiteral
      super(lf.getName(), null, getIcon(currentLaf));
      myLookAndFeelInfo = lf;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      switchLafAndUpdateUI(LafManager.getInstance(), myLookAndFeelInfo, false);
    }

    @Nullable
    private static Icon getIcon(boolean currentLaf) {
      return Registry.is("ide.instant.theme.switch") ? null : currentLaf ? AllIcons.Actions.Forward : ourNotCurrentAction;
    }
  }
}

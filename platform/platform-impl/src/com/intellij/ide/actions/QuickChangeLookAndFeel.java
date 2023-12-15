// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.ThemeListProvider;
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo;
import com.intellij.ide.ui.laf.UiThemeProviderListManager;
import com.intellij.ide.ui.laf.darcula.DarculaInstaller;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.Alarm;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public final class QuickChangeLookAndFeel extends QuickSwitchSchemeAction implements ActionRemoteBehaviorSpecification.Frontend {
  private final Alarm switchAlarm = new Alarm();

  @Override
  protected void fillActions(Project project, @NotNull DefaultActionGroup group, @NotNull DataContext dataContext) {
    UIThemeLookAndFeelInfo initialLaf = LafManager.getInstance().getCurrentUIThemeLookAndFeel();

    for (List<UIThemeLookAndFeelInfo> list : ThemeListProvider.Companion.getInstance().getShownThemes()) {
      if (group.getChildrenCount() > 0) {
        group.addSeparator();
      }
      for (UIThemeLookAndFeelInfo lf : list) {
        group.add(new LafChangeAction(lf, initialLaf == lf));
      }
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
    UIThemeLookAndFeelInfo initialLaf = LafManager.getInstance().getCurrentUIThemeLookAndFeel();

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
  protected @NotNull Condition<? super AnAction> preselectAction() {
    LafManager lafMan = LafManager.getInstance();
    return (a) -> (a instanceof LafChangeAction) && ((LafChangeAction)a).myLookAndFeelInfo == lafMan.getCurrentUIThemeLookAndFeel();
  }

  public static void switchLafAndUpdateUI(@NotNull LafManager lafManager, @NotNull UIManager.LookAndFeelInfo laf, boolean async) {
    switchLafAndUpdateUI(lafManager, (UIThemeLookAndFeelInfo)laf, async, false, false);
  }

  public static void switchLafAndUpdateUI(@NotNull LafManager lafManager, @NotNull UIThemeLookAndFeelInfo laf, boolean async) {
    switchLafAndUpdateUI(lafManager, laf, async, false, false);
  }

  /**
   * @deprecated use {@link #switchLafAndUpdateUI(LafManager, UIManager.LookAndFeelInfo, boolean)} instead
   */
  @Deprecated
  public static void switchLafAndUpdateUI(final @NotNull LafManager lafMan,
                                          @NotNull UIManager.LookAndFeelInfo lf,
                                          boolean async,
                                          boolean force) {
    switchLafAndUpdateUI(lafMan, (UIThemeLookAndFeelInfo)lf, async, force, false);
  }

  @ApiStatus.Internal
  public static void switchLafAndUpdateUI(final @NotNull LafManager lafManager,
                                          @NotNull UIThemeLookAndFeelInfo lf,
                                          boolean async,
                                          boolean force,
                                          boolean lockEditorScheme) {
    UIThemeLookAndFeelInfo cur = lafManager.getCurrentUIThemeLookAndFeel();
    if (!force && cur == lf) return;
    ChangeLAFAnimator animator = Registry.is("ide.intellij.laf.enable.animation") ? ChangeLAFAnimator.showSnapshot() : null;

    final boolean wasDarcula = StartupUiUtil.isUnderDarcula();
    lafManager.setCurrentLookAndFeel(lf, lockEditorScheme);

    Runnable updater = () -> {
      // a twist not to updateUI twice: here, and in DarculaInstaller
      // double updateUI shall be avoided and causes NPE in some components (HelpView)
      Ref<Boolean> updated = Ref.create(false);
      Disposable disposable = Disposer.newDisposable();
      ApplicationManager.getApplication().getMessageBus().connect(disposable)
        .subscribe(LafManagerListener.TOPIC, source -> updated.set(true));
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
          lafManager.updateUI();
        }
        if (animator != null) {
          animator.hideSnapshotWithAnimation();
        }
      }
    };
    if (async) {
      SwingUtilities.invokeLater(updater);
    }
    else {
      updater.run();
    }
  }

  @Override
  protected boolean isEnabled() {
    return UiThemeProviderListManager.Companion.getInstance().getLaFListSize() > 1 && !LafManager.getInstance().getAutodetect();
  }

  private static final class LafChangeAction extends DumbAwareAction {
    private final UIThemeLookAndFeelInfo myLookAndFeelInfo;

    private LafChangeAction(UIThemeLookAndFeelInfo laf, boolean currentLaf) {
      super(laf.getName(), null, getIcon(currentLaf));
      myLookAndFeelInfo = laf;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      switchLafAndUpdateUI(LafManager.getInstance(), myLookAndFeelInfo, false);
    }

    private static @Nullable Icon getIcon(boolean currentLaf) {
      return Registry.is("ide.instant.theme.switch") ? null : currentLaf ? AllIcons.Actions.Forward : ourNotCurrentAction;
    }
  }
}

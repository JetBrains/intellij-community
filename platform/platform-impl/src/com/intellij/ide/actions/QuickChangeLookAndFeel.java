// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.TargetUIType;
import com.intellij.ide.ui.ThemeListProvider;
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo;
import com.intellij.ide.ui.laf.UiThemeProviderListManager;
import com.intellij.ide.ui.laf.darcula.DarculaInstaller;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.colors.Groups;
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
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.icons.IconUtilKt;
import com.intellij.ui.popup.ActionPopupOptions;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.list.PopupListElementRenderer;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

public final class QuickChangeLookAndFeel extends QuickSwitchSchemeAction implements ActionRemoteBehaviorSpecification.Frontend {

  @Override
  protected void fillActions(Project project, @NotNull DefaultActionGroup group, @NotNull DataContext dataContext) {
    UIThemeLookAndFeelInfo initialLaf = LafManager.getInstance().getCurrentUIThemeLookAndFeel();

    for (Groups.GroupInfo<UIThemeLookAndFeelInfo> groupInfo : ThemeListProvider.Companion.getInstance().getShownThemes().getInfos()) {
      if (group.getChildrenCount() > 0) {
        group.addSeparator();
      }
      for (UIThemeLookAndFeelInfo lf : groupInfo.getItems()) {
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
  protected @NotNull ListPopup createPopup(AnActionEvent e, DefaultActionGroup group, JBPopupFactory.ActionSelectionAid aid) {
    UiThemeProviderListManager themeManager = UiThemeProviderListManager.Companion.getInstance();
    List<UIThemeLookAndFeelInfo> islandThemes = themeManager.getBundledThemeListForTargetUI(TargetUIType.ISLANDS);

    if (WelcomeFrame.getInstance() == null &&
        ContainerUtil.exists(group.getChildren(e), action -> action instanceof LafChangeAction lafAction &&
                                                             (lafAction.myLookAndFeelInfo.isRestartRequired() ||
                                                              islandThemes.contains(lafAction.myLookAndFeelInfo)))) {
      return new PopupFactoryImpl.ActionGroupPopup(null, getPopupTitle(e), group, e.getDataContext(),
                                                   myActionPlace == null ? ActionPlaces.POPUP : myActionPlace, new PresentationFactory(),
                                                   ActionPopupOptions.forAid(aid, true, -1, preselectAction()), null) {
        @Override
        protected ListCellRenderer<?> getListElementRenderer() {
          JLabel icon1 = new JLabel();
          JLabel icon2 = new JLabel();

          List<Groups.@NotNull GroupInfo<@NotNull UIThemeLookAndFeelInfo>> infos =
            ThemeListProvider.Companion.getInstance().getShownThemes().getInfos();

          return new PopupListElementRenderer(this) {
            @Override
            protected JComponent layoutComponent(JComponent middleItemComponent) {
              NonOpaquePanel subPanel = new NonOpaquePanel(new BorderLayout());
              subPanel.add(icon1, BorderLayout.WEST);
              subPanel.add(icon2, BorderLayout.EAST);
              icon1.setBorder(JBUI.Borders.emptyLeft(10));
              icon2.setBorder(JBUI.Borders.emptyLeft(5));

              NonOpaquePanel panel = new NonOpaquePanel(new BorderLayout());
              panel.add(middleItemComponent);
              panel.add(subPanel, BorderLayout.EAST);

              return super.layoutComponent(panel);
            }

            @Override
            protected void customizeComponent(JList list, Object value, boolean isSelected) {
              super.customizeComponent(list, value, isSelected);

              icon1.setIcon(null);
              icon2.setIcon(null);
              myRendererComponent.setToolTipText(null);

              if (value instanceof PopupFactoryImpl.ActionItem item) {
                AnAction action = item.getAction();
                if (action instanceof LafChangeAction lafAction) {
                  checkRestartRequired(lafAction, infos, islandThemes, isSelected, icon1, icon2);
                }
              }
            }
          };
        }
      };
    }
    return super.createPopup(e, group, aid);
  }

  private static boolean checkRestartRequired(@NotNull LafChangeAction lafAction,
                                              @NotNull List<Groups.GroupInfo<UIThemeLookAndFeelInfo>> infos,
                                              @NotNull List<UIThemeLookAndFeelInfo> islandThemes,
                                              boolean isSelected,
                                              @Nullable JLabel icon1,
                                              @Nullable JLabel icon2) {
    UIThemeLookAndFeelInfo currentLaf = LafManager.getInstance().getCurrentUIThemeLookAndFeel();

    if (lafAction.myLookAndFeelInfo.isRestartRequired()) {
      if (icon1 != null) {
        icon1.setIcon(AllIcons.General.Beta);
      }

      if (!isSelected) {
        Groups.GroupInfo<@NotNull UIThemeLookAndFeelInfo> group = ContainerUtil.find(infos, info -> ContainerUtil.find(
          info.getItems(), element -> element.getId().equals(lafAction.myLookAndFeelInfo.getId())) != null);
        if (group != null &&
            ContainerUtil.find(group.getItems(), element -> element.getId().equals(currentLaf.getId())) == null) {
          if (icon2 != null) {
            icon2.setIcon(IconUtilKt.getDisabledIcon(AllIcons.Actions.Restart, null));
          }
          return true;
        }
      }
    }
    else if (!isSelected && currentLaf.isRestartRequired()) {
      if (icon2 != null) {
        icon2.setIcon(IconUtilKt.getDisabledIcon(AllIcons.Actions.Restart, null));
      }
      return true;
    }
    if (icon1 != null && islandThemes.contains(lafAction.myLookAndFeelInfo)) {
      icon1.setIcon(AllIcons.General.Beta);
    }
    return false;
  }

  @Override
  protected void showPopup(AnActionEvent e, ListPopup popup) {
    ApplicationManager.getApplication().getService(QuickChangeLookAndFeelService.class)
      .preparePopup(popup);

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

  @ApiStatus.Internal
  public static void switchLafAndUpdateUI(@NotNull LafManager lafManager,
                                          @NotNull UIThemeLookAndFeelInfo laf,
                                          boolean async,
                                          boolean force,
                                          boolean lockEditorScheme) {
    QuickChangeLookAndFeelService.switchLafAndUpdateUI(lafManager, laf, async, force, lockEditorScheme);
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
      LafManager.getInstance().checkRestart();
    }

    private static @Nullable Icon getIcon(boolean currentLaf) {
      return Registry.is("ide.instant.theme.switch") ? null : currentLaf ? AllIcons.Actions.Forward : ourNotCurrentAction;
    }
  }

  @Service(Service.Level.APP)
  static final class QuickChangeLookAndFeelService {
    private final Alarm switchAlarm;

    QuickChangeLookAndFeelService(CoroutineScope cs) {
      switchAlarm = new Alarm(cs, Alarm.ThreadToUse.SWING_THREAD);
    }

    void preparePopup(ListPopup popup) {
      UIThemeLookAndFeelInfo initialLaf = LafManager.getInstance().getCurrentUIThemeLookAndFeel();

      List<Groups.@NotNull GroupInfo<@NotNull UIThemeLookAndFeelInfo>> infos =
        ThemeListProvider.Companion.getInstance().getShownThemes().getInfos();

      switchAlarm.cancelAllRequests();
      if (Registry.is("ide.instant.theme.switch")) {
        popup.addListSelectionListener(event -> {
          Object item = ((JList<?>)event.getSource()).getSelectedValue();
          if (item instanceof AnActionHolder) {
            AnAction anAction = ((AnActionHolder)item).getAction();
            if (anAction instanceof LafChangeAction action &&
                !checkRestartRequired(action, infos, Collections.emptyList(), false, null, null)) {
              switchAlarm.cancelAllRequests();
              switchAlarm.addRequest(() -> {
                switchLafAndUpdateUI(LafManager.getInstance(), action.myLookAndFeelInfo);
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
            switchLafAndUpdateUI(LafManager.getInstance(), initialLaf);
          }
        }
      });
    }

    static void switchLafAndUpdateUI(@NotNull LafManager lafManager, @NotNull UIThemeLookAndFeelInfo laf) {
      switchLafAndUpdateUI(lafManager, laf, false, false, false);
    }

    static void switchLafAndUpdateUI(final @NotNull LafManager lafManager,
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
  }
}

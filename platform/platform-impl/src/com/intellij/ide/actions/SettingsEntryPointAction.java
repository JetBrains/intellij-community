// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginStateListener;
import com.intellij.ide.plugins.PluginStateManager;
import com.intellij.ide.plugins.newui.PluginUpdatesService;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.updateSettings.impl.CheckForUpdateResult;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.PluginUpdateDialog;
import com.intellij.openapi.updateSettings.impl.UpdateInfoDialog;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager;
import com.intellij.ui.GotItTooltip;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class SettingsEntryPointAction extends AnAction implements DumbAware, RightAlignedToolbarAction, AnAction.TransparentUpdate,
                                                                  TooltipDescriptionProvider, CustomComponentAction {
  private Icon myIcon;

  public SettingsEntryPointAction() {
    initPluginsListeners();
  }

  @Override
  public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    ActionButton button = new ActionButton(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);

    UiNotifyConnector.doWhenFirstShown(button, () -> {
      Disposable disposable = Disposer.newDisposable();

      Balloon balloon = createGotItTooltip(disposable).showAt(Balloon.Position.below, button, component -> {
        PropertiesComponent.getInstance().setValue(GotItTooltip.PROPERTY_PREFIX + ".settings.entry.point", "0");
        return new Point(component.getWidth() / 2, component.getHeight());
      });

      if (balloon == null) {
        Disposer.dispose(disposable);
      }
      else {
        new MyListener(button.getParent(), balloon, disposable);
      }
    });

    return button;
  }

  private static class MyListener extends ComponentAdapter implements HierarchyListener {
    private final Component myComponent;
    private final Balloon myBalloon;
    private final Disposable myDisposable;

    private MyListener(@NotNull Component component, @NotNull Balloon balloon, @NotNull Disposable disposable) {
      myComponent = component;
      myBalloon = balloon;
      myDisposable = disposable;

      myComponent.addComponentListener(this);
      myComponent.addHierarchyListener(this);
    }

    @Override
    public void componentResized(ComponentEvent e) {
      handle();
    }

    @Override
    public void componentMoved(ComponentEvent e) {
      handle();
    }

    @Override
    public void componentHidden(ComponentEvent e) {
      handle();
    }

    @Override
    public void hierarchyChanged(HierarchyEvent e) {
      handle();
    }

    private void handle() {
      if (myComponent.isShowing() && !myBalloon.isDisposed()) {
        myBalloon.revalidate();
      }
      else {
        myComponent.removeComponentListener(this);
        myComponent.removeHierarchyListener(this);
        Disposer.dispose(myDisposable);
      }
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myIcon = AllIcons.General.GearPlain;
    // XXX update toolbar action

    ListPopup popup = createMainPopup(e.getDataContext());
    popup.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        myIcon = null;
        // XXX update toolbar action
      }
    });

    InputEvent inputEvent = e.getInputEvent();
    if (inputEvent == null) {
      popup.showInFocusCenter();
    }
    else {
      Component component = inputEvent.getComponent();
      if (component instanceof ActionButtonComponent) {
        popup.showUnderneathOf(component);
      }
      else {
        popup.showInCenterOf(component);
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setDescription(SettingsEntryPointAction::getActionTooltip);
    presentation.setIcon(myIcon == null ? getActionIcon() : myIcon);
  }

  @NotNull
  private static ListPopup createMainPopup(@NotNull DataContext context) {
    DefaultActionGroup group = new DefaultActionGroup();

    if (myPlatformUpdateInfo != null) {
      group.add(new DumbAwareAction(IdeBundle.message("settings.entry.point.update.ide.action",
                                                      ApplicationNamesInfo.getInstance().getFullProductName(),
                                                      myPlatformUpdateInfo.getNewBuild().getVersion()),
                                    null, AllIcons.Ide.Notification.IdeUpdate) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          if (myPlatformUpdateInfo.getPatches() == null ||
              (!SystemInfo.isWindows && !Files.isWritable(Paths.get(PathManager.getHomePath())))) {
            new UpdateInfoDialog(myPlatformUpdateInfo.getUpdatedChannel(), myPlatformUpdateInfo.getNewBuild(), null, true, null,
                                 myIncompatiblePlugins).show();
          }
          else {
            CheckForUpdateResult platformUpdateInfo = myPlatformUpdateInfo;
            newPlatformUpdate(null, null);

            ActionCallback callback = new ActionCallback().doWhenRejected(() -> {
              ApplicationManager.getApplication().invokeLater(() -> {
                newPlatformUpdate(platformUpdateInfo, null);
              }, ModalityState.any());
            });

            UpdateInfoDialog.downloadPatchAndRestart(platformUpdateInfo.getNewBuild(), platformUpdateInfo.getUpdatedChannel(),
                                                     platformUpdateInfo.getPatches(), null, null, callback);
          }
        }
      });
    }
    if (myUpdatedPlugins != null) {
      int size = myUpdatedPlugins.size();

      String name = size == 1
                    ? IdeBundle.message("settings.entry.point.update.plugin.action", myUpdatedPlugins.iterator().next().getPluginName())
                    : IdeBundle.message("settings.entry.point.update.plugins.action");
      group.add(new DumbAwareAction(name, null, AllIcons.Ide.Notification.PluginUpdate) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          new PluginUpdateDialog(e.getProject(), myUpdatedPlugins, myCustomRepositoryPlugins).show();
        }
      });
      if (size > 1) {
        StringBuilder result = new StringBuilder();
        int count = 0;

        for (PluginDownloader plugin : myUpdatedPlugins) {
          if (result.length() > 0) {
            result.append(", ");
          }
          result.append(plugin.getPluginName());
          count++;
          if (count == 2) {
            int delta = size - count;
            if (delta > 0) {
              result.append(" ").append(IdeBundle.message("settings.entry.point.update.plugins.more.action", delta));
            }
            break;
          }
        }

        @NlsSafe String actionName = result.toString();
        group.add(new AnAction(actionName) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
          }

          @Override
          public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(false);
          }
        });
      }
    }

    group.addSeparator();

    ActionGroup templateGroup = (ActionGroup)ActionManager.getInstance().getAction("SettingsEntryPointGroup");
    for (AnAction child : templateGroup.getChildren(null)) {
      group.add(child);
    }

    return JBPopupFactory.getInstance().createActionGroupPopup(null, group, context, JBPopupFactory.ActionSelectionAid.MNEMONICS, true);
  }

  @NotNull
  private static GotItTooltip createGotItTooltip(@NotNull Disposable disposable) {
    return new GotItTooltip("settings.entry.point", IdeBundle.message("settings.entry.point.got.it.popup"), disposable);
  }

  private static PluginUpdatesService myUpdatesService;
  private static PluginStateListener myPluginStateListener;

  private static void initPluginsListeners() {
    if (myUpdatesService == null) {
      myUpdatesService = PluginUpdatesService.connectWithUpdates(descriptors -> {
        List<PluginDownloader> downloaders = new ArrayList<>();
        try {
          for (IdeaPluginDescriptor descriptor : descriptors) {
            downloaders.add(PluginDownloader.createDownloader(descriptor));
          }
        }
        catch (IOException e) {
          PluginManagerCore.getLogger().error(e);
        }
        newPluginsUpdate(downloaders.isEmpty() ? null : downloaders, null);
      });
    }
    if (myPluginStateListener == null) {
      PluginStateManager.addStateListener(myPluginStateListener = new PluginStateListener() {
        @Override
        public void install(@NotNull IdeaPluginDescriptor descriptor) {
          if (myUpdatedPlugins != null) {
            PluginId pluginId = descriptor.getPluginId();
            List<PluginDownloader> updatedPlugins =
              ContainerUtil.filter(myUpdatedPlugins, downloader -> !pluginId.equals(downloader.getId()));
            if (myUpdatedPlugins.size() != updatedPlugins.size()) {
              newPluginsUpdate(updatedPlugins.isEmpty() ? null : updatedPlugins, myCustomRepositoryPlugins);
            }
          }
        }

        @Override
        public void uninstall(@NotNull IdeaPluginDescriptor descriptor) {
        }
      });
    }
  }

  private static CheckForUpdateResult myPlatformUpdateInfo;
  private static Collection<IdeaPluginDescriptor> myIncompatiblePlugins;

  private static Collection<PluginDownloader> myUpdatedPlugins;
  private static Collection<IdeaPluginDescriptor> myCustomRepositoryPlugins;

  public static void newPlatformUpdate(@Nullable CheckForUpdateResult platformUpdateInfo,
                                       @Nullable Collection<IdeaPluginDescriptor> incompatiblePlugins) {
    myPlatformUpdateInfo = platformUpdateInfo;
    myIncompatiblePlugins = incompatiblePlugins;
    updateAction();
  }

  public static void newPluginsUpdate(@Nullable Collection<PluginDownloader> updatedPlugins,
                                      @Nullable Collection<IdeaPluginDescriptor> customRepositoryPlugins) {
    myUpdatedPlugins = updatedPlugins;
    myCustomRepositoryPlugins = customRepositoryPlugins;
    updateAction();
  }

  private static void updateAction() {
    if (isAvailableInStatusBar()) {
      updateWidgets();
    }
    // XXX update toolbar action
  }

  private static @NotNull @Nls String getActionTooltip() {
    return myPlatformUpdateInfo == null && myUpdatedPlugins == null
           ? IdeBundle.message("settings.entry.point.tooltip")
           : IdeBundle.message("settings.entry.point.update.tooltip");
  }

  private static @NotNull Icon getActionIcon() {
    if (myPlatformUpdateInfo != null) {
      return AllIcons.Ide.Notification.IdeUpdate;
    }
    if (myUpdatedPlugins != null) {
      return AllIcons.Ide.Notification.PluginUpdate;
    }
    return AllIcons.General.GearPlain;
  }

  private static UISettingsListener mySettingsListener;

  private static void initUISettingsListener() {
    if (mySettingsListener == null) {
      mySettingsListener = uiSettings -> updateWidgets();
      ApplicationManager.getApplication().getMessageBus().connect().subscribe(UISettingsListener.TOPIC, mySettingsListener);
    }
  }

  private static void updateWidgets() {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      project.getService(StatusBarWidgetsManager.class).updateWidget(StatusBarManager.class);
      IdeFrame frame = WindowManager.getInstance().getIdeFrame(project);
      if (frame != null) {
        StatusBar statusBar = frame.getStatusBar();
        if (statusBar != null) {
          statusBar.updateWidget(WIDGET_ID);
        }
      }
    }
  }

  private static boolean isAvailableInStatusBar() {
    initUISettingsListener();

    UISettings settings = UISettings.getInstance();
    return !settings.getShowMainToolbar() && !settings.getShowToolbarInNavigationBar();
  }

  private static final String WIDGET_ID = "settingsEntryPointWidget";

  public static class StatusBarManager implements StatusBarWidgetFactory {
    @Override
    public @NonNls @NotNull String getId() {
      return WIDGET_ID;
    }

    @Override
    public @Nls @NotNull String getDisplayName() {
      return IdeBundle.message("settings.entry.point.tooltip");
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
      return isAvailableInStatusBar();
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
      return new MyStatusBarWidget();
    }

    @Override
    public void disposeWidget(@NotNull StatusBarWidget widget) {
      Disposer.dispose(widget);
    }

    @Override
    public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
      return isAvailableInStatusBar();
    }

    @Override
    public boolean isConfigurable() {
      return false;
    }
  }

  private static class MyStatusBarWidget implements StatusBarWidget, StatusBarWidget.IconPresentation {
    private StatusBar myStatusBar;
    private Icon myIcon;

    private MyStatusBarWidget() {
      initPluginsListeners();
    }

    @Override
    public @NonNls @NotNull String ID() {
      return WIDGET_ID;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
      myStatusBar = statusBar;

      JComponent component = ((IdeStatusBarImpl)statusBar).getWidgetComponent(WIDGET_ID);
      if (component != null) {
        createGotItTooltip(this).showAt(Balloon.Position.above, component, c -> new Point(c.getWidth() / 2, 0));
      }
    }

    @Override
    public @Nullable WidgetPresentation getPresentation() {
      return this;
    }

    @Override
    public @Nullable @NlsContexts.Tooltip String getTooltipText() {
      return getActionTooltip();
    }

    @Override
    public @Nullable Consumer<MouseEvent> getClickConsumer() {
      return event -> {
        myIcon = AllIcons.General.GearPlain;
        myStatusBar.updateWidget(WIDGET_ID);

        Component component = event.getComponent();
        ListPopup popup = createMainPopup(DataManager.getInstance().getDataContext(component));
        popup.addListener(new JBPopupListener() {
          @Override
          public void beforeShown(@NotNull LightweightWindowEvent event) {
            Point location = component.getLocationOnScreen();
            Dimension size = popup.getSize();
            popup.setLocation(new Point(location.x + component.getWidth() - size.width, location.y - size.height));
          }

          @Override
          public void onClosed(@NotNull LightweightWindowEvent event) {
            myIcon = null;
            myStatusBar.updateWidget(WIDGET_ID);
          }
        });
        popup.show(component);
      };
    }

    @Override
    public @Nullable Icon getIcon() {
      return myIcon == null ? getActionIcon() : myIcon;
    }

    @Override
    public void dispose() {
    }
  }
}
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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.updateSettings.impl.CheckForUpdateResult;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.PluginUpdateDialog;
import com.intellij.openapi.updateSettings.impl.UpdateInfoDialog;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager;
import com.intellij.ui.AnActionButton;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class SettingsEntryPointAction extends AnAction implements DumbAware, RightAlignedToolbarAction,
                                                                  AnAction.TransparentUpdate, TooltipDescriptionProvider {
  public SettingsEntryPointAction() {
    initPluginsListeners();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    resetActionIcon();

    ListPopup popup = createMainPopup(e.getDataContext());

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
    presentation.setText("");
    presentation.setDescription(getActionTooltip());
    presentation.setIcon(getActionIcon());

    for (AnAction child : getTemplateActions()) {
      child.update(e);
    }
  }

  private static AnAction @NotNull [] getTemplateActions() {
    ActionGroup templateGroup = (ActionGroup)ActionManager.getInstance().getAction("SettingsEntryPointGroup");
    return templateGroup == null ? EMPTY_ARRAY : templateGroup.getChildren(null);
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
            new UpdateInfoDialog(e.getProject(), myPlatformUpdateInfo.getUpdatedChannel(), myPlatformUpdateInfo.getNewBuild(), null, true,
                                 null, myIncompatiblePlugins).show();
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
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setEnabled(myEnableUpdateAction);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          PluginUpdateDialog dialog = new PluginUpdateDialog(e.getProject(), myUpdatedPlugins, myCustomRepositoryPlugins);
          dialog.setFinishCallback(() -> setEnableUpdateAction(true));
          setEnableUpdateAction(false);

          if (!dialog.showAndGet()) {
            setEnableUpdateAction(true);
          }
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

    for (AnAction child : getTemplateActions()) {
      if (child instanceof Separator) {
        group.add(child);
      }
      else {
        String text = child.getTemplateText();
        if (text != null && !text.endsWith("...")) {
          AnActionButton button = new AnActionButton.AnActionButtonWrapper(child.getTemplatePresentation(), child) {
            @Override
            public void updateButton(@NotNull AnActionEvent e) {
              getDelegate().update(e);
              e.getPresentation().setText(e.getPresentation().getText() + "...");
            }

            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
              super.actionPerformed(new AnActionEvent(e.getInputEvent(), e.getDataContext(), e.getPlace(),
                                                      getDelegate().getTemplatePresentation(), e.getActionManager(), e.getModifiers()));
            }
          };
          button.setShortcut(child.getShortcutSet());
          group.add(button);
        }
        else {
          group.add(child);
        }
      }
    }

    return JBPopupFactory.getInstance().createActionGroupPopup(null, group, context, JBPopupFactory.ActionSelectionAid.MNEMONICS, true);
  }

  private static PluginUpdatesService myUpdatesService;
  private static PluginStateListener myPluginStateListener;

  private static void initPluginsListeners() {
    if (myUpdatesService == null) {
      myUpdatesService = PluginUpdatesService.connectWithUpdates(descriptors -> {
        if (ContainerUtil.isEmpty(descriptors)) {
          newPluginsUpdate(null, null);
          return;
        }
        List<PluginDownloader> downloaders = new ArrayList<>();
        try {
          for (IdeaPluginDescriptor descriptor : descriptors) {
            if (!PluginUpdateDialog.isIgnored(descriptor)) {
              downloaders.add(PluginDownloader.createDownloader(descriptor));
            }
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
          removePluginsUpdate(Collections.singleton(descriptor));
        }

        @Override
        public void uninstall(@NotNull IdeaPluginDescriptor descriptor) {
        }
      });
    }
  }

  private static CheckForUpdateResult myPlatformUpdateInfo;
  private static Collection<IdeaPluginDescriptor> myIncompatiblePlugins;
  private static boolean myShowPlatformUpdateIcon;

  private static Collection<PluginDownloader> myUpdatedPlugins;
  private static Collection<IdeaPluginDescriptor> myCustomRepositoryPlugins;
  private static boolean myShowPluginsUpdateIcon;
  private static boolean myEnableUpdateAction = true;

  private static void setEnableUpdateAction(boolean value) {
    myEnableUpdateAction = value;
  }

  public static void newPlatformUpdate(@Nullable CheckForUpdateResult platformUpdateInfo,
                                       @Nullable Collection<IdeaPluginDescriptor> incompatiblePlugins) {
    myPlatformUpdateInfo = platformUpdateInfo;
    myIncompatiblePlugins = incompatiblePlugins;
    myShowPlatformUpdateIcon = platformUpdateInfo != null;
    updateAction();
  }

  public static void newPluginsUpdate(@Nullable Collection<PluginDownloader> updatedPlugins,
                                      @Nullable Collection<IdeaPluginDescriptor> customRepositoryPlugins) {
    myUpdatedPlugins = updatedPlugins;
    myCustomRepositoryPlugins = customRepositoryPlugins;
    myShowPluginsUpdateIcon = updatedPlugins != null;
    updateAction();
  }

  public static void removePluginsUpdate(@NotNull Collection<IdeaPluginDescriptor> descriptors) {
    if (myUpdatedPlugins != null) {
      List<PluginDownloader> updatedPlugins =
        ContainerUtil.filter(myUpdatedPlugins, downloader -> {
          PluginId pluginId = downloader.getId();
          return ContainerUtil.find(descriptors, descriptor -> descriptor.getPluginId().equals(pluginId)) == null;
        });
      if (myUpdatedPlugins.size() != updatedPlugins.size()) {
        newPluginsUpdate(updatedPlugins.isEmpty() ? null : updatedPlugins, myCustomRepositoryPlugins);
      }
    }
  }

  private static void updateAction() {
    if (isAvailableInStatusBar()) {
      updateWidgets();
    }
  }

  private static @NotNull @Nls String getActionTooltip() {
    return myPlatformUpdateInfo == null && myUpdatedPlugins == null
           ? IdeBundle.message("settings.entry.point.tooltip")
           : IdeBundle.message("settings.entry.point.update.tooltip");
  }

  private static void resetActionIcon() {
    myShowPlatformUpdateIcon = myShowPluginsUpdateIcon = false;
  }

  private static @NotNull Icon getActionIcon() {
    if (myShowPlatformUpdateIcon) {
      return AllIcons.Ide.Notification.IdeUpdate;
    }
    if (myShowPluginsUpdateIcon) {
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
        resetActionIcon();
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
        });
        popup.show(component);
      };
    }

    @Override
    public @Nullable Icon getIcon() {
      return getActionIcon();
    }

    @Override
    public void dispose() {
    }
  }
}
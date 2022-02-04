// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.ToolbarSettings;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.BadgeIcon;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.popup.PopupState;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public final class SettingsEntryPointAction extends DumbAwareAction implements RightAlignedToolbarAction, TooltipDescriptionProvider {
  private final PopupState<JBPopup> myPopupState = PopupState.forPopup();

  public SettingsEntryPointAction() {
    super(IdeBundle.messagePointer("settings.entry.point.tooltip"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    resetActionIcon();

    if (myPopupState.isHidden() && !myPopupState.isRecentlyHidden()) {
      ListPopup popup = createMainPopup(e.getDataContext());
      myPopupState.prepareToShow(popup);
      PopupUtil.showForActionButtonEvent(popup, e);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    if (e.isFromActionToolbar()) presentation.setText("");
    presentation.setDescription(getActionTooltip());
    presentation.setIcon(getActionIcon());
  }

  private static AnAction @NotNull [] getTemplateActions() {
    ActionGroup templateGroup = (ActionGroup)ActionManager.getInstance().getAction("SettingsEntryPointGroup");
    return templateGroup == null ? EMPTY_ARRAY : templateGroup.getChildren(null);
  }

  @NotNull
  private static ListPopup createMainPopup(@NotNull DataContext context) {
    List<AnAction> appActions = new ArrayList<>();
    List<AnAction> pluginActions = new ArrayList<>();

    for (ActionProvider provider : ActionProvider.EP_NAME.getExtensionList()) {
      for (UpdateAction action : provider.getUpdateActions(context)) {
        Presentation presentation = action.getTemplatePresentation();
        if (action.isIdeUpdate()) {
          presentation.setIcon(AllIcons.Ide.Notification.IdeUpdate);
          appActions.add(action);
        }
        else {
          presentation.setIcon(AllIcons.Ide.Notification.PluginUpdate);
          pluginActions.add(action);
        }
        action.markAsRead();
      }
    }

    DefaultActionGroup group = new DefaultActionGroup(appActions);
    group.addAll(pluginActions);

    if (group.getChildrenCount() == 0) {
      resetActionIcon();
    }
    else {
      group.addSeparator();
    }

    for (AnAction child : getTemplateActions()) {
      if (child instanceof Separator) {
        group.add(child);
      }
      else {
        String text = child.getTemplateText();
        if (text != null && !(text.endsWith("...") || text.endsWith("…") )) {
          AnActionButton button = new AnActionButton.AnActionButtonWrapper(child.getTemplatePresentation(), child) {
            @Override
            public void updateButton(@NotNull AnActionEvent e) {
              getDelegate().update(e);
              e.getPresentation().setText(e.getPresentation().getText() + "…");
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

    return JBPopupFactory.getInstance()
      .createActionGroupPopup(null, group, context, JBPopupFactory.ActionSelectionAid.MNEMONICS, true);
  }

  private static boolean ourShowPlatformUpdateIcon;
  private static boolean ourShowPluginsUpdateIcon;

  public static void updateState() {
    resetActionIcon();

    loop:
    for (ActionProvider provider : ActionProvider.EP_NAME.getExtensionList()) {
      for (UpdateAction action : provider.getUpdateActions(DataContext.EMPTY_CONTEXT)) {
        if (action.isNewAction()) {
          if (action.isIdeUpdate()) {
            ourShowPlatformUpdateIcon = true;
          }
          else {
            ourShowPluginsUpdateIcon = true;
          }
          if (ourShowPlatformUpdateIcon && ourShowPluginsUpdateIcon) {
            break loop;
          }
        }
      }
    }

    if (isAvailableInStatusBar()) {
      updateWidgets();
    }
  }

  private static @NotNull @Nls String getActionTooltip() {
    boolean updates = ourShowPlatformUpdateIcon || ourShowPluginsUpdateIcon;
    if (!updates) {
      for (ActionProvider provider : ActionProvider.EP_NAME.getExtensionList()) {
        if (!provider.getUpdateActions(DataContext.EMPTY_CONTEXT).isEmpty()) {
          updates = true;
          break;
        }
      }
    }
    return IdeBundle.message(updates ? "settings.entry.point.with.updates.tooltip" : "settings.entry.point.tooltip");
  }

  private static void resetActionIcon() {
    ourShowPlatformUpdateIcon = ourShowPluginsUpdateIcon = false;
  }

  private static @NotNull Icon getActionIcon() {
    if (ourShowPlatformUpdateIcon) {
      return ExperimentalUI.isNewUI()
             ? new BadgeIcon(AllIcons.General.GearPlain, JBUI.CurrentTheme.IconBadge.WARNING)
             : AllIcons.Ide.Notification.IdeUpdate;
    }
    if (ourShowPluginsUpdateIcon) {
      return ExperimentalUI.isNewUI()
             ? new BadgeIcon(AllIcons.General.GearPlain, JBUI.CurrentTheme.IconBadge.INFORMATION)
             : AllIcons.Ide.Notification.PluginUpdate;
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
    UISettings uiSettings = UISettings.getInstance();
    ToolbarSettings toolbarSettings = ToolbarSettings.getInstance();
    return !uiSettings.getShowMainToolbar() &&
           !uiSettings.getShowNavigationBar() &&
           !(toolbarSettings.isEnabled() && toolbarSettings.isVisible());
  }

  private static final String WIDGET_ID = "settingsEntryPointWidget";

  public static class StatusBarManager implements StatusBarWidgetFactory {
    @Override
    public @NotNull String getId() {
      return WIDGET_ID;
    }

    @Override
    public @Nls @NotNull String getDisplayName() {
      return IdeBundle.message("settings.entry.point.widget.name");
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
  }

  private static class MyStatusBarWidget implements StatusBarWidget, StatusBarWidget.IconPresentation {
    private final PopupState<JBPopup> myPopupState = PopupState.forPopup();
    private StatusBar myStatusBar;

    @Override
    public @NotNull String ID() {
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

        if (!myPopupState.isHidden() || myPopupState.isRecentlyHidden()) {
          return;
        }

        Component component = event.getComponent();
        ListPopup popup = createMainPopup(DataManager.getInstance().getDataContext(component));
        myPopupState.prepareToShow(popup);
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
    public void dispose() { }
  }

  public interface ActionProvider {
    ExtensionPointName<ActionProvider> EP_NAME = new ExtensionPointName<>("com.intellij.settingsEntryPointActionProvider");

    @NotNull Collection<UpdateAction> getUpdateActions(@NotNull DataContext context);
  }

  public static abstract class UpdateAction extends DumbAwareAction {
    private boolean myNewAction = true;

    protected UpdateAction() {
    }

    protected UpdateAction(@Nullable @NlsActions.ActionText String text) {
      super(text);
    }

    public boolean isIdeUpdate() {
      return false;
    }

    public boolean isRestartRequired() {
      return false;
    }

    public boolean isNewAction() {
      return myNewAction;
    }

    public void markAsRead() {
      myNewAction = false;
    }
  }
}
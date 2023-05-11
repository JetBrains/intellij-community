// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.ToolbarSettings;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.BadgeIconSupplier;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.IconManager;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.PopupListElementRenderer;
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
  private static final BadgeIconSupplier GEAR_ICON = new BadgeIconSupplier(AllIcons.General.GearPlain);
  private static final Icon NEW_UI_ICON =
    IconManager.getInstance().withIconBadge(AllIcons.General.GearPlain, JBUI.CurrentTheme.IconBadge.NEW_UI);
  private static final BadgeIconSupplier IDE_UPDATE_ICON = new BadgeIconSupplier(AllIcons.Ide.Notification.IdeUpdate);
  private static final BadgeIconSupplier PLUGIN_UPDATE_ICON = new BadgeIconSupplier(AllIcons.Ide.Notification.PluginUpdate);

  public SettingsEntryPointAction() {
    super(IdeBundle.messagePointer("settings.entry.point.tooltip"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    resetActionIcon();

    ListPopup popup = createMainPopup(e.getDataContext(), e.getInputEvent().getComponent());
    PopupUtil.showForActionButtonEvent(popup, e);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    if (e.isFromActionToolbar()) presentation.setText("");
    presentation.setDescription(getActionTooltip());
    presentation.setIcon(getActionIcon());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static AnAction @NotNull [] getTemplateActions() {
    ActionGroup templateGroup = (ActionGroup)ActionManager.getInstance().getAction("SettingsEntryPointGroup");
    return templateGroup == null ? EMPTY_ARRAY : templateGroup.getChildren(null);
  }

  @NotNull
  private static ListPopup createMainPopup(@NotNull DataContext context, Component component) {
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
        if (text != null && !(text.endsWith("...") || text.endsWith("…")) && !(child instanceof NoDots)) {
          AnActionButton button = new AnActionButton.AnActionButtonWrapper(child.getTemplatePresentation(), child) {
            @Override
            public void updateButton(@NotNull AnActionEvent e) {
              getDelegate().update(e);
              e.getPresentation().setText(e.getPresentation().getText() + "…");
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

    Project project = CommonDataKeys.PROJECT.getData(context);
    if (project == null) {
      return JBPopupFactory.getInstance()
        .createActionGroupPopup(null, group, context, JBPopupFactory.ActionSelectionAid.MNEMONICS, true);
    }

    ListPopupStep<?> step = JBPopupFactory.getInstance().createActionsStep(
      group, context, null, false, true, null, component, true, 0, false);
    return JBPopupFactory.getInstance()
      .createListPopup(project, step, renderer -> {
        return new SettingsPopupListElementRenderer<>(((PopupListElementRenderer<?>) renderer).getPopup());
      });
  }

  private static boolean ourShowPlatformUpdateIcon;
  private static boolean ourShowPluginsUpdateIcon;
  private static boolean ourNewUiIcon = calculateOurNewUiIcon();

  public static void updateState() {
    resetActionIcon();

    ourNewUiIcon = calculateOurNewUiIcon();

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

  private static boolean calculateOurNewUiIcon() {
    PropertiesComponent propertyComponent = PropertiesComponent.getInstance();
    return !ExperimentalUI.isNewUI() && !propertyComponent.getBoolean(ExperimentalUI.NEW_UI_USED_PROPERTY)
           && ExperimentalUI.getPromotionDaysCount() < 14;
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
    if (updates) {
      return IdeBundle.message("settings.entry.point.with.updates.tooltip");
    }
    return IdeBundle.message(ourNewUiIcon ? "settings.entry.point.newUi.tooltip" : "settings.entry.point.tooltip");
  }

  private static void resetActionIcon() {
    ourShowPlatformUpdateIcon = false;
    ourShowPluginsUpdateIcon = false;
    ourNewUiIcon = false;
  }

  private static @NotNull Icon getActionIcon() {
    if (ourShowPlatformUpdateIcon) {
      return ExperimentalUI.isNewUI()
             ? GEAR_ICON.getWarningIcon()
             : getCustomizedIcon(IDE_UPDATE_ICON);
    }
    if (ourShowPluginsUpdateIcon) {
      return ExperimentalUI.isNewUI()
             ? GEAR_ICON.getInfoIcon()
             : getCustomizedIcon(PLUGIN_UPDATE_ICON);
    }
    if (ourNewUiIcon) {
      return NEW_UI_ICON;
    }

    return getCustomizedIcon(GEAR_ICON);
  }

  private static @NotNull Icon getCustomizedIcon(@NotNull BadgeIconSupplier supplier) {
    for (IconCustomizer customizer : IconCustomizer.EP_NAME.getExtensionList()) {
      Icon icon = customizer.getCustomIcon(supplier);
      if (icon != null) return icon;
    }
    return supplier.getOriginalIcon();
  }

  /**
   * Allows to modify a base icon provided by {@link BadgeIconSupplier}. The icon of the first extension which returns a non-null value
   * will be used.
   */
  public interface IconCustomizer {
    ExtensionPointName<IconCustomizer> EP_NAME = new ExtensionPointName<>("com.intellij.settingsEntryPointIconCustomizer");

    /**
     * Returns a customized icon optionally based on the given {@link BadgeIconSupplier}. For example, {@code supplier.getInfoIcon()}.
     *
     * @param supplier The supplier to use for a base icon.
     *
     * @return A customized icon using {@link BadgeIconSupplier} or an alternative (custom) icon.
     */
    @Nullable Icon getCustomIcon(@NotNull BadgeIconSupplier supplier);
  }

  /**
   * Marker interface to suppress automatic dots "..." addition after action name.
   */
  public interface NoDots {
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
           !ExperimentalUI.isNewUI() &&
           !(toolbarSettings.isAvailable() && toolbarSettings.isVisible());
  }

  private static final String WIDGET_ID = "settingsEntryPointWidget";

  static final class StatusBarManager implements StatusBarWidgetFactory {
    @Override
    public @NotNull String getId() {
      return WIDGET_ID;
    }

    @Override
    public @NotNull String getDisplayName() {
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
    public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
      return isAvailableInStatusBar();
    }
  }

  private static final class MyStatusBarWidget implements StatusBarWidget, StatusBarWidget.IconPresentation {
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
    public @NotNull WidgetPresentation getPresentation() {
      return this;
    }

    @Override
    public @NlsContexts.Tooltip @NotNull String getTooltipText() {
      return getActionTooltip();
    }

    @Override
    public @NotNull Consumer<MouseEvent> getClickConsumer() {
      return event -> {
        resetActionIcon();
        myStatusBar.updateWidget(WIDGET_ID);

        Component component = event.getComponent();
        ListPopup popup = createMainPopup(DataManager.getInstance().getDataContext(component), component);
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
    public @NotNull Icon getIcon() {
      return getActionIcon();
    }
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

  private static class SettingsPopupListElementRenderer<E> extends PopupListElementRenderer<E> {

    private SettingsPopupListElementRenderer(ListPopupImpl aPopup) {
      super(aPopup);
    }

    @Override
    protected void customizeComponent(JList<? extends E> list, E value, boolean isSelected) {
      super.customizeComponent(list, value, isSelected);

      myTextLabel.setHorizontalTextPosition(SwingConstants.LEFT);
      myTextLabel.setIconTextGap(JBUI.scale(6));
      boolean enableNewUi = value instanceof AnActionHolder actionHolder && actionHolder.getAction() instanceof EnableNewUiAction;
      myTextLabel.setIcon(enableNewUi ? AllIcons.General.Beta : null);
    }
  }
}

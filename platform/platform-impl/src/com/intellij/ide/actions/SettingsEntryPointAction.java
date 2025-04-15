// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.PluginManagementPolicy;
import com.intellij.ide.ui.NewUiUtilKt;
import com.intellij.ide.ui.ToolbarSettings;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.CeProcessCanceledException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager;
import com.intellij.ui.BadgeIconSupplier;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.IconManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.popup.ActionPopupOptions;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * @author Alexander Lobas
 */
public final class SettingsEntryPointAction extends ActionGroup
  implements CustomComponentAction, Toggleable, DumbAware, RightAlignedToolbarAction, TooltipDescriptionProvider {

  private static final Logger LOG = Logger.getInstance(SettingsEntryPointAction.class);

  private static final BadgeIconSupplier GEAR_ICON = new BadgeIconSupplier(AllIcons.General.GearPlain);
  private static final Icon NEW_UI_ICON =
    IconManager.getInstance().withIconBadge(AllIcons.General.GearPlain, JBUI.CurrentTheme.IconBadge.NEW_UI);
  private static final BadgeIconSupplier IDE_UPDATE_ICON = new BadgeIconSupplier(AllIcons.Ide.Notification.IdeUpdate);
  private static final BadgeIconSupplier PLUGIN_UPDATE_ICON = new BadgeIconSupplier(AllIcons.Ide.Notification.PluginUpdate);

  public SettingsEntryPointAction() {
    getTemplatePresentation().putClientProperty(ActionUtil.ALWAYS_VISIBLE_GROUP, true);
  }

  @Override
  public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    boolean newUI = ExperimentalUI.isNewUI() && ActionPlaces.MAIN_TOOLBAR.equals(place);
    return new ActionButton(this, presentation, place,
                            newUI ? ActionToolbar.experimentalToolbarMinimumButtonSize() : ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
      {
        putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, IdeBundle.message("settings.entry.point.tooltip"));
      }
      @Override
      protected void paintButtonLook(Graphics g) {
        Icon icon = getIcon();
        if (icon instanceof ComboIcon comboIcon) {
          comboIcon.paintButton(this, getButtonLook(), g);
        }
        else {
          super.paintButtonLook(g);
        }
      }
    };
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    resetActionIcon();

    JBPopup popup = createPopup(e);
    PopupUtil.showForActionButtonEvent(popup, e);
  }

  @ApiStatus.Internal
  public @NotNull JBPopup createPopup(@NotNull AnActionEvent e) {
    JBPopup popup = createMainPopup(this, e.getDataContext(), e.getPlace());
    PopupUtil.addToggledStateListener(popup, e.getPresentation());
    return popup;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setPerformGroup(true);
    e.getPresentation().setPopupGroup(true);
    e.getPresentation().putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true);
    Presentation presentation = e.getPresentation();
    if (e.isFromActionToolbar()) {
      presentation.setText("");
    }
    presentation.setDescription(getActionTooltip());
    presentation.setIconSupplier(SettingsEntryPointAction::getActionIcon);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent event) {
    if (event == null) return AnAction.EMPTY_ARRAY;

    DataContext context = event.getDataContext();

    List<AnAction> result = new ArrayList<>();
    List<AnAction> appActions = new ArrayList<>();
    List<AnAction> pluginActions = new ArrayList<>();
    boolean showPluginsUpdates = isShowPluginsUpdates();

    for (ActionProvider provider : ActionProvider.EP_NAME.getExtensionList()) {
      try {
        for (UpdateAction action : provider.getUpdateActions(context)) {
          Presentation presentation = action.getTemplatePresentation();
          if (action.isIdeUpdate()) {
            presentation.setIcon(AllIcons.Ide.Notification.IdeUpdate);
            appActions.add(action);
          }
          else {
            if (!showPluginsUpdates && action.isPluginUpdate()) {
              continue;
            }
            presentation.setIcon(AllIcons.Ide.Notification.PluginUpdate);
            pluginActions.add(action);
          }
          action.markAsRead();
        }
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    result.addAll(appActions);
    result.addAll(pluginActions);

    if (result.isEmpty()) {
      resetActionIcon();
    }
    ContainerUtil.addIfNotNull(result, ActionManager.getInstance().getAction("UpdateEntryPointGroup"));
    result.add(Separator.getInstance());
    ContainerUtil.addIfNotNull(result, ActionManager.getInstance().getAction("SettingsEntryPointGroup"));

    if (ExperimentalUI.isNewUI() &&
        ContainerUtil.exists(event.getPlace().split("@"), place -> place.equals(ActionPlaces.MAIN_TOOLBAR))) {
      result.add(Separator.getInstance());
      AnAction customizeMainToolbarAction = ActionManager.getInstance().getAction("CustomizeMainToolbarGroup");
      ContainerUtil.addIfNotNull(result, customizeMainToolbarAction);
    }

    if (ExperimentalUI.isNewUI()) {
      for (ActionProvider provider : ActionProvider.EP_NAME.getExtensionList()) {
        try {
          result.addAll(provider.getLastActions(context));
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
    return result.toArray(AnAction.EMPTY_ARRAY);
  }

  private static @NotNull ListPopup createMainPopup(@NotNull ActionGroup group, @NotNull DataContext context, @Nullable String eventPlace) {
    boolean hasLastActions = ContainerUtil.find(
      ActionProvider.EP_NAME.getExtensionList(), o -> !o.getLastActions(context).isEmpty()) != null;

    ListPopup popup;
    String place = "SettingsEntryPoint";
    if (eventPlace != null) {
      place += "@" + eventPlace;
    }
    place = ActionPlaces.getPopupPlace(place);

    if (hasLastActions && ExperimentalUI.isNewUI()) {
      popup = new MyPopup(group, context, new PresentationFactory(), place);
    }
    else {
      popup = JBPopupFactory.getInstance().createActionGroupPopup(
        null, group, context, null, true, place);
    }

    popup.setShowSubmenuOnHover(true);

    return popup;
  }

  private static class MyPopup extends PopupFactoryImpl.ActionGroupPopup {

    final @NotNull PresentationFactory myPresentationFactory;

    MyPopup(@NotNull ActionGroup group, @NotNull DataContext context, @NotNull PresentationFactory presentationFactory, @NotNull String place) {
      super(null, null, group, context, place, presentationFactory,
            ActionPopupOptions.showDisabled(), null);
      myPresentationFactory = presentationFactory;
    }

    @Override
    protected JComponent createContent() {
      JComponent content = super.createContent();
      var list = getList();
      list.setBorder(JBUI.Borders.emptyTop(JBUI.CurrentTheme.Popup.bodyTopInsetNoHeader()));

      var listener = new MouseAdapter() {
        LastAction myLastMouseAction = null;

        @Override
        public void mouseMoved(MouseEvent e) {
          int index = list.locationToIndex(e.getPoint());
          var model = list.getModel();
          if (index >= 0 && index < model.getSize()) {
            Object value = model.getElementAt(index);
            if (value instanceof PopupFactoryImpl.ActionItem item) {
              AnAction action = item.getAction();
              if (action instanceof CustomComponentAction && action instanceof LastAction lastAction) {
                lastAction.handleMouseMove(e);
                myLastMouseAction = lastAction;
                return;
              }
            }
          }
          mouseExited(e);
        }

        @Override
        public void mouseExited(MouseEvent e) {
          if (myLastMouseAction != null) {
            myLastMouseAction.handleMouseExit(e);
            myLastMouseAction = null;
          }
        }
      };

      list.addMouseListener(listener);
      list.addMouseMotionListener(listener);

      ClientProperty.put(list, LastAction.POPUP, this);

      return content;
    }

    @Override
    protected ListCellRenderer<?> getListElementRenderer() {
      //noinspection unchecked
      ListCellRenderer<Object> renderer = (ListCellRenderer<Object>)super.getListElementRenderer();
      return (list, value, index, isSelected, cellHasFocus) -> {
        if (value instanceof PopupFactoryImpl.ActionItem item) {
          AnAction action = item.getAction();
          Presentation presentation = myPresentationFactory.getPresentation(action);
          if (action instanceof LastAction && action instanceof CustomComponentAction customComponent) {
            return customComponent.createCustomComponent(presentation, "");
          }
          //noinspection DialogTitleCapitalization
          String text = item.getText();
          //noinspection DialogTitleCapitalization
          String secondText = presentation.getClientProperty(LastAction.SECOND_TEXT);
          if (secondText != null) {
            JBLabel label = new JBLabel(presentation.getIcon());
            label.setBorder(JBUI.Borders.emptyRight(JBUI.CurrentTheme.ActionsList.elementIconGap() - 2));

            JPanel panel = new OpaquePanel(new BorderLayout(), isSelected
                                                               ? JBUI.CurrentTheme.ManagedIde.MENU_ITEM_HOVER
                                                               : JBUI.CurrentTheme.Advertiser.background()) {
              @Override
              public AccessibleContext getAccessibleContext() {
                return label.getAccessibleContext();
              }
            };

            float leftRightInset = JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET.getUnscaled();
            Insets innerInsets = ((JBInsets)JBUI.CurrentTheme.Popup.Selection.innerInsets()).getUnscaled();
            panel.setBorder(JBUI.Borders.empty(12, (int)(leftRightInset + innerInsets.left), 12, 14));

            JPanel iconPanel = new NonOpaquePanel(new BorderLayout());
            iconPanel.add(label, BorderLayout.NORTH);
            panel.add(iconPanel, BorderLayout.WEST);

            JPanel lines = new NonOpaquePanel(new BorderLayout(0, JBUI.scale(2)));
            lines.add(new JBLabel(text), BorderLayout.NORTH);

            JLabel secondLine = new JBLabel(secondText);
            secondLine.setForeground(JBUI.CurrentTheme.Advertiser.foreground());
            lines.add(secondLine, BorderLayout.SOUTH);

            panel.add(lines);

            if (index > 0) {
              //noinspection unchecked
              PopupFactoryImpl.ActionItem prevIndex =
                ((ListPopupStep<PopupFactoryImpl.ActionItem>)getStep()).getValues().get(index - 1);
              AnAction prevAction = prevIndex.getAction();
              if (!(prevAction instanceof LastAction)) {
                JPanel wrapper = new OpaquePanel(new BorderLayout(), list.getBackground()) {
                  @Override
                  public AccessibleContext getAccessibleContext() {
                    return label.getAccessibleContext();
                  }
                };
                wrapper.setBorder(JBUI.Borders.emptyTop(JBUI.CurrentTheme.Popup.bodyBottomInsetNoAd()));
                wrapper.add(panel);
                return wrapper;
              }
            }

            return panel;
          }
        }
        return renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      };
    }
  }

  private static boolean ourShowPlatformUpdateIcon;
  private static boolean ourShowPluginsUpdateIcon;
  private static boolean ourNewUiIcon = calculateOurNewUiIcon();

  public static void updateState() {
    resetActionIcon();

    ourNewUiIcon = calculateOurNewUiIcon();

    boolean showPluginsUpdates = isShowPluginsUpdates();

    loop:
    for (ActionProvider provider : ActionProvider.EP_NAME.getExtensionList()) {
      try {
        for (UpdateAction action : provider.getUpdateActions(DataContext.EMPTY_CONTEXT)) {
          if (action.isNewAction()) {
            if (action.isIdeUpdate()) {
              ourShowPlatformUpdateIcon = true;
            }
            else {
              if (!showPluginsUpdates && action.isPluginUpdate()) {
                continue;
              }
              ourShowPluginsUpdateIcon = true;
            }
            if (ourShowPlatformUpdateIcon && ourShowPluginsUpdateIcon) {
              break loop;
            }
          }
        }
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    if (isAvailableInStatusBar()) {
      updateWidgets();
    }
  }

  private static boolean isShowPluginsUpdates() {
    return !PluginManagementPolicy.getInstance().isPluginAutoUpdateAllowed() ||
           !UpdateSettings.getInstance().getState().isPluginsAutoUpdateEnabled();
  }

  private static boolean calculateOurNewUiIcon() {
    return !ExperimentalUI.isNewUI() && !ExperimentalUI.Companion.isNewUiUsedOnce() && NewUiUtilKt.getNewUiPromotionDaysCount() < 14;
  }

  private static @NotNull @Nls String getActionTooltip() {
    boolean updates = ourShowPlatformUpdateIcon || ourShowPluginsUpdateIcon;
    if (!updates) {
      boolean showPluginsUpdates = isShowPluginsUpdates();

      for (ActionProvider provider : ActionProvider.EP_NAME.getExtensionList()) {
        try {
          Collection<UpdateAction> actions = provider.getUpdateActions(DataContext.EMPTY_CONTEXT);
          if (!showPluginsUpdates) {
            actions = actions.stream().filter(action -> !action.isPluginUpdate()).toList();
          }
          if (!actions.isEmpty()) {
            updates = true;
            break;
          }
        }
        catch (ProcessCanceledException pce) {
          throw pce;
        }
        catch (CancellationException ex) {
          throw new CeProcessCanceledException(ex);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
    String message = updates
                     ? IdeBundle.message("settings.entry.point.with.updates.tooltip")
                     : IdeBundle.message(ourNewUiIcon ? "settings.entry.point.newUi.tooltip" : "settings.entry.point.tooltip");

    List<String> tooltips =
      new ArrayList<>(ContainerUtil.mapNotNull(ActionProvider.EP_NAME.getExtensionList(), provider -> provider.getTooltip()));
    if (!tooltips.isEmpty()) {
      tooltips.add(message);

      return "<ul style='margin-top:0;margin-bottom:0;padding-top:0;padding-bottom:0;'>" + //NON-NLS
             StringUtil.join(tooltips, element -> "<li>" + element + "</li>", "") +
             "</ul>";
    }

    return message;
  }

  private static void resetActionIcon() {
    ourShowPlatformUpdateIcon = false;
    ourShowPluginsUpdateIcon = false;
    ourNewUiIcon = false;
  }

  private static @NotNull Icon calculateActionIcon() {
    if (ourShowPlatformUpdateIcon) {
      return ExperimentalUI.isNewUI() ? GEAR_ICON.getWarningIcon() : getCustomizedIcon(IDE_UPDATE_ICON);
    }
    if (ourShowPluginsUpdateIcon) {
      return ExperimentalUI.isNewUI() ? GEAR_ICON.getInfoIcon() : getCustomizedIcon(PLUGIN_UPDATE_ICON);
    }
    if (ourNewUiIcon) {
      return NEW_UI_ICON;
    }

    return getCustomizedIcon(GEAR_ICON);
  }

  private static @NotNull Icon getActionIcon() {
    Icon firstIcon = calculateActionIcon();
    if (ExperimentalUI.isNewUI()) {
      Icon secondIcon = getSecondIcon();
      if (secondIcon != null) {
        return new ComboIcon(firstIcon, secondIcon);
      }
    }
    return firstIcon;
  }

  private static class ComboIcon implements Icon {
    private final Icon myFirstIcon;
    private final Icon mySecondIcon;

    private ComboIcon(@NotNull Icon firstIcon, @NotNull Icon secondIcon) {
      myFirstIcon = firstIcon;
      mySecondIcon = secondIcon;
    }

    @Override
    public void paintIcon(@NotNull Component component, @NotNull Graphics g, int x, int y) {
      throw new UnsupportedOperationException();
    }

    public void paintButton(@NotNull ActionButton button, @NotNull ActionButtonLook look, @NotNull Graphics g) {
      Graphics2D g2 = (Graphics2D)g.create();

      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

        int radius = JBUI.scale(12);
        boolean compactMode = UISettings.getInstance().getCompactMode();
        Insets insets = button.getInsets();
        if (compactMode) {
          int compact = JBUI.scale(3);
          insets.set(insets.top + compact, insets.left + compact, insets.bottom + compact, insets.right + compact);
        }
        Dimension size = button.getSize();
        JBInsets.removeFrom(size, insets);

        g2.setColor(JBUI.CurrentTheme.ManagedIde.getBadgeBackground(button.getPopState() != ActionButtonComponent.NORMAL));
        g2.fillRoundRect(insets.left, insets.top, size.width, size.height, radius, radius);

        g2.setColor(JBUI.CurrentTheme.ManagedIde.BADGE_BORDER);
        g2.drawRoundRect(insets.left, insets.top, size.width, size.height - 1, radius, radius);

        int offset = JBUI.scale(compactMode ? 3 : 6);
        int iconSize = JBUI.scale(20);
        int height = button.getHeight();
        look.paintIcon(g2, button, myFirstIcon, insets.left + offset + (iconSize - myFirstIcon.getIconWidth()) / 2,
                       (height - myFirstIcon.getIconHeight()) / 2);
        look.paintIcon(g2, button, mySecondIcon, insets.left + offset + iconSize + offset + (iconSize - mySecondIcon.getIconWidth()) / 2,
                       (height - mySecondIcon.getIconHeight()) / 2);
      }
      finally {
        g2.dispose();
      }
    }

    @Override
    public int getIconWidth() {
      return JBUI.scale(UISettings.getInstance().getCompactMode() ? 52 : 58);
    }

    @Override
    public int getIconHeight() {
      return JBUI.scale(30);
    }
  }

  private static @NotNull Icon getCustomizedIcon(@NotNull BadgeIconSupplier supplier) {
    for (IconCustomizer customizer : IconCustomizer.EP_NAME.getExtensionList()) {
      try {
        Icon icon = customizer.getCustomIcon(supplier);
        if (icon != null) return icon;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    return supplier.getOriginalIcon();
  }

  private static @Nullable Icon getSecondIcon() {
    for (IconCustomizer customizer : IconCustomizer.EP_NAME.getExtensionList()) {
      try {
        Icon icon = customizer.getSecondIcon();
        if (icon != null) {
          return icon;
        }
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    return null;
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
     * @return A customized icon using {@link BadgeIconSupplier} or an alternative (custom) icon.
     */
    @Nullable Icon getCustomIcon(@NotNull BadgeIconSupplier supplier);

    default @Nullable Icon getSecondIcon() {
      return null;
    }
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
        ListPopup popup = createMainPopup(
          (ActionGroup)ActionManager.getInstance().getAction("SettingsEntryPoint"),
          DataManager.getInstance().getDataContext(component), null);
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

    default @NotNull Collection<LastAction> getLastActions(@NotNull DataContext context) {
      return Collections.emptyList();
    }

    default @Nls @Nullable String getTooltip() {
      return null;
    }
  }

  public abstract static class UpdateAction extends DumbAwareAction {
    private boolean myNewAction = true;

    protected UpdateAction() {
    }

    protected UpdateAction(@Nullable @NlsActions.ActionText String text) {
      super(text);
    }

    public boolean isIdeUpdate() {
      return false;
    }

    public boolean isPluginUpdate() {
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

  public abstract static class LastAction extends DumbAwareAction {
    protected LastAction() {
    }

    protected LastAction(@Nullable @NlsActions.ActionText String text) {
      super(text);
    }

    protected LastAction(@Nullable @NlsActions.ActionText String text,
                         @Nullable @NlsActions.ActionDescription String description,
                         @Nullable Icon icon) {
      super(text, description, icon);
    }

    public @NotNull @NlsActions.ActionText String getSecondText() {
      return "";
    }

    protected void handleMouseMove(@NotNull MouseEvent event) {
    }

    protected void handleMouseExit(@NotNull MouseEvent event) {
    }

    public static final Key<JBPopup> POPUP = Key.create("JBPopup");

    public static final Key<@NlsActions.ActionText String> SECOND_TEXT = Key.create("SECOND_TEXT");
  }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.util.Producer;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.*;
import java.util.function.Function;

import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.util.containers.ContainerUtil.*;

abstract class SelectionBasedPluginModelAction<C extends JComponent, D extends IdeaPluginDescriptor> extends DumbAwareAction {

  protected final @NotNull MyPluginModel myPluginModel;
  protected final boolean myShowShortcut;
  private final @NotNull List<? extends C> mySelection;
  private final @NotNull Function<? super C, ? extends D> myPluginDescriptor;

  protected SelectionBasedPluginModelAction(@NotNull @Nls String text,
                                            @NotNull MyPluginModel pluginModel,
                                            boolean showShortcut,
                                            @NotNull List<? extends C> selection,
                                            @NotNull Function<? super C, ? extends D> pluginDescriptor) {
    super(text);

    myPluginModel = pluginModel;
    myShowShortcut = showShortcut;
    mySelection = selection;
    myPluginDescriptor = pluginDescriptor;
  }

  protected final void setShortcutSet(@NotNull ShortcutSet shortcutSet,
                                      boolean show) {
    setShortcutSet(show ? shortcutSet : CustomShortcutSet.EMPTY);
  }

  protected final @NotNull Map<C, D> getSelection() {
    LinkedHashMap<C, D> map = new LinkedHashMap<>();
    for (C component : mySelection) {
      D descriptor = myPluginDescriptor.apply(component);
      if (descriptor != null) {
        map.put(component, descriptor);
      }
    }
    return Collections.unmodifiableMap(map);
  }

  protected final @NotNull Collection<? extends D> getAllDescriptors() {
    return getSelection().values();
  }

  static final class EnableDisableAction<C extends JComponent> extends SelectionBasedPluginModelAction<C, IdeaPluginDescriptor> {

    private static final CustomShortcutSet SHORTCUT_SET = new CustomShortcutSet(KeyEvent.VK_SPACE);

    private final @NotNull PluginEnableDisableAction myAction;

    EnableDisableAction(@NotNull MyPluginModel pluginModel,
                        @NotNull PluginEnableDisableAction action,
                        boolean showShortcut,
                        @NotNull List<? extends C> selection,
                        @NotNull Function<? super C, ? extends IdeaPluginDescriptor> pluginDescriptor) {
      super(action.getPresentableText(),
            pluginModel,
            showShortcut,
            selection,
            pluginDescriptor);

      myAction = action;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Collection<? extends IdeaPluginDescriptor> descriptors = getAllDescriptors();
      Set<PluginId> pluginIds = map2SetNotNull(descriptors, IdeaPluginDescriptor::getPluginId);
      List<PluginEnabledState> states = map(pluginIds, myPluginModel::getState);

      boolean allEnabled = all(states, PluginEnabledState.ENABLED::equals);
      boolean isForceEnableAll = myAction == PluginEnableDisableAction.ENABLE_GLOBALLY &&
                                 !allEnabled;

      boolean disabled = pluginIds.isEmpty() ||
                         !all(states, myAction::isApplicable) ||
                         myAction == PluginEnableDisableAction.DISABLE_GLOBALLY &&
                         exists(pluginIds, myPluginModel::isRequiredPluginForProject);

      boolean enabled = !disabled;
      e.getPresentation().setEnabledAndVisible(isForceEnableAll || enabled);

      boolean isForceDisableAll = myAction == PluginEnableDisableAction.DISABLE_GLOBALLY && allEnabled;
      setShortcutSet(SHORTCUT_SET, myShowShortcut && (isForceEnableAll || isForceDisableAll));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myPluginModel.setEnabledState(getAllDescriptors(), myAction);
    }
  }

  static final class UninstallAction<C extends JComponent> extends SelectionBasedPluginModelAction<C, IdeaPluginDescriptorImpl> {

    private static final ShortcutSet SHORTCUT_SET;

    static {
      ShortcutSet deleteShortcutSet = EventHandler.getShortcuts(IdeActions.ACTION_EDITOR_DELETE);
      SHORTCUT_SET = deleteShortcutSet != null ?
                     deleteShortcutSet :
                     new CustomShortcutSet(EventHandler.DELETE_CODE);
    }

    private final @NotNull JComponent myUiParent;

    UninstallAction(@NotNull MyPluginModel pluginModel,
                    boolean showShortcut,
                    @NotNull JComponent uiParent,
                    @NotNull List<? extends C> selection,
                    @NotNull Function<? super C, ? extends IdeaPluginDescriptor> pluginDescriptor) {
      super(IdeBundle.message("plugins.configurable.uninstall"),
            pluginModel,
            showShortcut,
            selection,
            pluginDescriptor.andThen(descriptor -> descriptor instanceof IdeaPluginDescriptorImpl ?
                                                   (IdeaPluginDescriptorImpl)descriptor :
                                                   null));

      myUiParent = uiParent;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Collection<? extends IdeaPluginDescriptorImpl> descriptors = getAllDescriptors();

      boolean disabled = descriptors.isEmpty() ||
                         exists(descriptors, IdeaPluginDescriptor::isBundled) ||
                         exists(descriptors, myPluginModel::isUninstalled);
      e.getPresentation().setEnabledAndVisible(!disabled);

      setShortcutSet(SHORTCUT_SET, myShowShortcut);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Map<C, IdeaPluginDescriptorImpl> selection = getSelection();
      if (!askToUninstall(getUninstallAllMessage(selection.values()), myUiParent)) {
        return;
      }

      ApplicationInfoEx applicationInfo = ApplicationInfoEx.getInstanceEx();
      for (Map.Entry<C, IdeaPluginDescriptorImpl> entry : selection.entrySet()) {
        IdeaPluginDescriptorImpl descriptor = entry.getValue();
        List<IdeaPluginDescriptorImpl> dependents = MyPluginModel.getDependents(descriptor,
                                                                                applicationInfo, PluginManagerCore.buildPluginIdMap());

        if (dependents.isEmpty() ||
            askToUninstall(getUninstallDependentsMessage(descriptor, dependents), entry.getKey())) {
          myPluginModel.uninstallAndUpdateUi(descriptor);
        }
      }
    }

    private static @NotNull @Nls String getUninstallAllMessage(@NotNull Collection<IdeaPluginDescriptorImpl> descriptors) {
      return descriptors.size() == 1 ?
             IdeBundle.message("prompt.uninstall.plugin", descriptors.iterator().next().getName()) :
             IdeBundle.message("prompt.uninstall.several.plugins", descriptors.size());
    }

    private static @NotNull @Nls String getUninstallDependentsMessage(@NotNull IdeaPluginDescriptorImpl descriptor,
                                                                      @NotNull List<? extends IdeaPluginDescriptor> dependents) {
      String listOfDeps = join(dependents,
                               plugin -> "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;" + plugin.getName(),
                               "<br>");
      String message = IdeBundle.message("dialog.message.following.plugin.depend.on",
                                         dependents.size(),
                                         descriptor.getName(),
                                         listOfDeps);
      return XmlStringUtil.wrapInHtml(message);
    }

    private static boolean askToUninstall(@NotNull @Nls String message,
                                          @NotNull JComponent parentComponent) {
      return MessageDialogBuilder
        .yesNo(IdeBundle.message("title.plugin.uninstall"),
               message)
        .ask(parentComponent);
    }
  }

  static <C extends JComponent> void addActionsTo(@NotNull DefaultActionGroup group,
                                                  @NotNull Function<? super @NotNull PluginEnableDisableAction, @NotNull EnableDisableAction<C>> createEnableDisableAction,
                                                  @NotNull Producer<? extends @NotNull UninstallAction<C>> createUninstallAction) {
    PluginEnableDisableAction[] actions = PluginEnableDisableAction.values();
    for (int i = 0; i < actions.length; i++) {
      group.add(createEnableDisableAction.apply(actions[i]));
      if ((i + 1) % 3 == 0) {
        group.addSeparator();
      }
    }
    group.add(createUninstallAction.produce());
  }

  static <C extends JComponent> @NotNull JComponent createGearButton(@NotNull Function<? super @NotNull PluginEnableDisableAction, @NotNull EnableDisableAction<C>> createEnableDisableAction,
                                                                     @NotNull Producer<? extends @NotNull UninstallAction<C>> createUninstallAction) {
    DefaultActionGroup result = new DefaultActionGroup();
    addActionsTo(result,
                 createEnableDisableAction,
                 createUninstallAction);

    return TabbedPaneHeaderComponent.createToolbar(result,
                                                   IdeBundle.message("plugin.settings.link.title"),
                                                   AllIcons.General.GearHover);
  }

  static <C extends JComponent> @NotNull OptionButtonController<C> createOptionButton(@NotNull Function<? super @NotNull PluginEnableDisableAction, @NotNull EnableDisableAction<C>> createEnableDisableAction,
                                                                                      @NotNull Producer<? extends @NotNull UninstallAction<C>> createUninstallAction) {
    return new OptionButtonController<C>(createEnableDisableAction.apply(PluginEnableDisableAction.ENABLE_GLOBALLY),
                                      createEnableDisableAction.apply(PluginEnableDisableAction.DISABLE_GLOBALLY),
                                      createUninstallAction.produce());
  }

  static class OptionButtonController<C extends JComponent> implements ActionListener {
    public final JBOptionButton button = new OptionButton();
    public final JButton bundledButton = new JButton();

    private final EnableDisableAction<C> myEnableAction;
    private final EnableDisableAction<C> myDisableAction;
    private EnableDisableAction<C> myCurrentAction;

    OptionButtonController(@NotNull EnableDisableAction<C> enableAction,
                           @NotNull EnableDisableAction<C> disableAction,
                           @NotNull UninstallAction<C> uninstallAction) {
      myEnableAction = enableAction;
      myDisableAction = disableAction;
      button.setAction(new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          OptionButtonController.this.actionPerformed(null);
        }
      });

      button.setOptions(new Action[]{new AbstractAction(uninstallAction.getTemplateText()) {
        @Override
        public void actionPerformed(ActionEvent e) {
          uninstallAction.actionPerformed(AnActionEvent.createFromDataContext("", null, DataContext.EMPTY_CONTEXT));
        }
      }});

      bundledButton.setOpaque(false);
      bundledButton.addActionListener(this);
    }

    public void update() {
      Presentation presentation = new Presentation();
      AnActionEvent event = AnActionEvent.createFromDataContext("", presentation, DataContext.EMPTY_CONTEXT);

      myEnableAction.update(event);
      myCurrentAction = presentation.isEnabledAndVisible() ? myEnableAction : myDisableAction;

      String text = myCurrentAction.getTemplateText();
      button.getAction().putValue(Action.NAME, text);
      bundledButton.setText(text);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myCurrentAction.actionPerformed(AnActionEvent.createFromDataContext("", null, DataContext.EMPTY_CONTEXT));
    }
  }

  private static class OptionButton extends JBOptionButton {
    private final JButton myBaseline = new JButton();

    OptionButton() {
      super(null, null);

      setAddSeparator(false);
      setSelectFirstItem(false);
      setPopupBackgroundColor(UIUtil.getListBackground());
      setShowPopupYOffset(-2);

      setPopupHandler(popup -> {
        Dimension size = new Dimension(popup.getSize());
        Insets insets = getInsets();
        size.width = getWidth() - insets.left - insets.right;
        popup.setSize(size);
        return null;
      });
    }

    @Override
    public int getBaseline(int width, int height) {
      myBaseline.setText(getText());
      myBaseline.setSize(getSize());
      return myBaseline.getBaseline(width, height);
    }
  }
}
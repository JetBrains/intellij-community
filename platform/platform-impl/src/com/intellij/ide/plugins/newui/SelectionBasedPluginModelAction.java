// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.util.Producer;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

abstract class SelectionBasedPluginModelAction<C extends JComponent> extends DumbAwareAction {

  protected final @NotNull PluginModelFacade pluginModelFacade;
  protected final boolean myShowShortcut;
  private final @NotNull List<? extends C> mySelection;
  private final @NotNull Function<? super C, PluginUiModel> myPluginDescriptorGetter;

  protected SelectionBasedPluginModelAction(@NotNull @Nls String text,
                                            @NotNull PluginModelFacade pluginModel,
                                            boolean showShortcut,
                                            @NotNull List<? extends C> selection,
                                            @NotNull Function<? super C, PluginUiModel> pluginModelGetter) {
    super(text);

    pluginModelFacade = pluginModel;
    myShowShortcut = showShortcut;
    mySelection = selection;
    myPluginDescriptorGetter = pluginModelGetter;
  }

  protected final void setShortcutSet(@NotNull ShortcutSet shortcutSet,
                                      boolean show) {
    setShortcutSet(show ? shortcutSet : CustomShortcutSet.EMPTY);
  }

  protected final @NotNull Map<C, PluginUiModel> getSelection() {
    LinkedHashMap<C, PluginUiModel> map = new LinkedHashMap<>();
    for (C component : mySelection) {
      PluginUiModel descriptor = myPluginDescriptorGetter.apply(component);
      if (descriptor != null) {
        map.put(component, descriptor);
      }
    }
    return Collections.unmodifiableMap(map);
  }

  protected final @NotNull Collection<PluginUiModel> getAllDescriptors() {
    return getSelection().values();
  }

  static final class EnableDisableAction<C extends JComponent> extends SelectionBasedPluginModelAction<C> {

    private static final CustomShortcutSet SHORTCUT_SET = new CustomShortcutSet(KeyEvent.VK_SPACE);

    private final @NotNull PluginEnableDisableAction myAction;
    private final @NotNull Runnable myOnFinishAction;

    EnableDisableAction(@NotNull PluginModelFacade pluginModel,
                        @NotNull PluginEnableDisableAction action,
                        boolean showShortcut,
                        @NotNull List<? extends C> selection,
                        @NotNull Function<? super C, PluginUiModel> pluginModelGetter,
                        @NotNull Runnable onFinishAction) {
      super(action.getPresentableText(),
            pluginModel,
            showShortcut,
            selection,
            pluginModelGetter);

      myAction = action;
      myOnFinishAction = onFinishAction;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Collection<PluginUiModel> descriptors = getAllDescriptors();
      List<PluginEnabledState> states = map(descriptors, pluginModelFacade::getState);

      boolean allEnabled = all(states, PluginEnabledState.ENABLED::equals);
      boolean isForceEnableAll = myAction == PluginEnableDisableAction.ENABLE_GLOBALLY &&
                                 !allEnabled;

      boolean disabled = descriptors.isEmpty() ||
                         !all(states, myAction::isApplicable) ||
                         myAction == PluginEnableDisableAction.DISABLE_GLOBALLY &&
                         exists(descriptors, pluginModelFacade::isPluginRequiredForProject);

      boolean enabled = !disabled;
      e.getPresentation().setEnabledAndVisible(isForceEnableAll || enabled);

      if(pluginModelFacade.hasPluginRequiresUltimateButItsDisabled(descriptors)) {
        e.getPresentation().setEnabled(false);
      }

      boolean isForceDisableAll = myAction == PluginEnableDisableAction.DISABLE_GLOBALLY && allEnabled;
      setShortcutSet(SHORTCUT_SET, myShowShortcut && (isForceEnableAll || isForceDisableAll));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      pluginModelFacade.setEnabledState(getAllDescriptors(), myAction);
      myOnFinishAction.run();
    }
  }

  static final class UninstallAction<C extends JComponent> extends SelectionBasedPluginModelAction<C> {

    private static final ShortcutSet SHORTCUT_SET;

    static {
      ShortcutSet deleteShortcutSet = EventHandler.getShortcuts(IdeActions.ACTION_EDITOR_DELETE);
      SHORTCUT_SET = deleteShortcutSet != null ?
                     deleteShortcutSet :
                     new CustomShortcutSet(EventHandler.DELETE_CODE);
    }

    private final @NotNull JComponent myUiParent;
    private final @NotNull Runnable myOnFinishAction;
    private final boolean myDynamicTitle;

    UninstallAction(@NotNull PluginModelFacade pluginModel,
                    boolean showShortcut,
                    @NotNull JComponent uiParent,
                    @NotNull List<? extends C> selection,
                    @NotNull Function<? super C, ? extends PluginUiModel> pluginModelGetter,
                    @NotNull Runnable onFinishAction) {
      //noinspection unchecked
      super(IdeBundle.message(isBundledUpdate(selection, (Function<Object, PluginUiModel>)pluginModelGetter, pluginModel)
                              ? "plugins.configurable.uninstall.bundled.update"
                              : "plugins.configurable.uninstall"),
            pluginModel,
            showShortcut,
            selection,
            pluginModelGetter.andThen(model -> PluginUiModel.getDescriptorOrNull(model) instanceof IdeaPluginDescriptorImpl ?
                                               model : null));

      myUiParent = uiParent;
      myOnFinishAction = onFinishAction;
      myDynamicTitle = selection.size() == 1 && pluginModelGetter.apply(selection.iterator().next()) == null;
    }

    private static boolean isBundledUpdate(@NotNull List<?> selection, Function<Object, @Nullable PluginUiModel> pluginDescriptor,
                                           @NotNull PluginModelFacade pluginModelFacade) {
      for (Object o : selection) {
        PluginUiModel uiModel = pluginDescriptor.apply(o);
        if (!pluginModelFacade.isBundledUpdate(uiModel)) {
          return false;
        }
      }
      return true;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Collection<PluginUiModel> descriptors = getAllDescriptors();

      if (myDynamicTitle) {
        e.getPresentation().setText(IdeBundle.message(
          descriptors.size() == 1 && pluginModelFacade.isBundledUpdate(descriptors.iterator().next())
          ? "plugins.configurable.uninstall.bundled.update"
          : "plugins.configurable.uninstall"));
      }

      boolean disabled = descriptors.isEmpty() ||
                         exists(descriptors, PluginUiModel::isBundled) ||
                         exists(descriptors, pluginModelFacade::isUninstalled);
      e.getPresentation().setEnabledAndVisible(!disabled);

      setShortcutSet(SHORTCUT_SET, myShowShortcut);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Map<C, PluginUiModel> selection = getSelection();

      List<PluginUiModel> toDeleteWithAsk = new ArrayList<>();
      List<PluginUiModel> toDelete = new ArrayList<>();

      Map<PluginUiModel, List<PluginUiModel>> dependentsMap = pluginModelFacade.getDependents(selection.values());
      for (Map.Entry<C, PluginUiModel> entry : selection.entrySet()) {
        PluginUiModel model = entry.getValue();
        List<PluginUiModel> dependents = dependentsMap.get(model);
        if (dependents.isEmpty()) {
          toDeleteWithAsk.add(model);
        }
        else {
          boolean bundledUpdate = pluginModelFacade.isBundledUpdate(model);
          if (askToUninstall(getUninstallDependentsMessage(model, dependents, bundledUpdate), entry.getKey(), bundledUpdate)) {
            toDelete.add(model);
          }
        }
      }

      boolean runFinishAction = false;

      if (!toDeleteWithAsk.isEmpty()) {
        boolean bundledUpdate = toDeleteWithAsk.size() == 1 && pluginModelFacade.isBundledUpdate(toDeleteWithAsk.get(0));
        if (askToUninstall(getUninstallAllMessage(toDeleteWithAsk, bundledUpdate), myUiParent, bundledUpdate)) {
          for (PluginUiModel descriptor : toDeleteWithAsk) {
            pluginModelFacade.uninstallAndUpdateUi(descriptor);
          }
          runFinishAction = true;
        }
      }

      for (PluginUiModel descriptor : toDelete) {
        pluginModelFacade.uninstallAndUpdateUi(descriptor);
      }

      if (runFinishAction || !toDelete.isEmpty()) {
        myOnFinishAction.run();
      }
    }

    private static @NotNull
    @Nls String getUninstallAllMessage(@NotNull Collection<PluginUiModel> descriptors, boolean bundledUpdate) {
      if (descriptors.size() == 1) {
        PluginUiModel descriptor = descriptors.iterator().next();
        return IdeBundle.message("prompt.uninstall.plugin", descriptor.getName(), bundledUpdate ? 1 : 0);
      }
      return IdeBundle.message("prompt.uninstall.several.plugins", descriptors.size());
    }

    private static @NotNull @Nls String getUninstallDependentsMessage(@NotNull PluginUiModel descriptor,
                                                                      @NotNull List<PluginUiModel> dependents,
                                                                      boolean bundledUpdate) {
      String listOfDeps = join(dependents,
                               plugin -> "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;" + plugin.getName(),
                               "<br>");
      String message = IdeBundle.message("dialog.message.following.plugin.depend.on",
                                         dependents.size(),
                                         descriptor.getName(),
                                         listOfDeps,
                                         bundledUpdate ? 1 : 0);
      return XmlStringUtil.wrapInHtml(message);
    }

    private static boolean askToUninstall(@NotNull @Nls String message, @NotNull JComponent parentComponent, boolean bundledUpdate) {
      return MessageDialogBuilder.yesNo(IdeBundle.message("title.plugin.uninstall", bundledUpdate ? 1 : 0), message).ask(parentComponent);
    }
  }

  static <C extends JComponent> void addActionsTo(@NotNull DefaultActionGroup group,
                                                  @NotNull Function<? super @NotNull PluginEnableDisableAction, @NotNull EnableDisableAction<C>> createEnableDisableAction,
                                                  @NotNull Producer<@NotNull UninstallAction<C>> createUninstallAction) {
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
                                                                     @NotNull Producer<@NotNull UninstallAction<C>> createUninstallAction) {
    DefaultActionGroup result = new DefaultActionGroup();
    addActionsTo(result,
                 createEnableDisableAction,
                 createUninstallAction);

    return TabbedPaneHeaderComponent.createToolbar(result,
                                                   IdeBundle.message("plugin.settings.link.title"),
                                                   AllIcons.General.GearHover);
  }

  static <C extends JComponent> @NotNull OptionButtonController<C> createOptionButton(@NotNull Function<? super @NotNull PluginEnableDisableAction, @NotNull EnableDisableAction<C>> createEnableDisableAction,
                                                                                      @NotNull Producer<@NotNull UninstallAction<C>> createUninstallAction) {
    return new OptionButtonController<>(createEnableDisableAction.apply(PluginEnableDisableAction.ENABLE_GLOBALLY),
                                        createEnableDisableAction.apply(PluginEnableDisableAction.DISABLE_GLOBALLY),
                                        createUninstallAction.produce());
  }

  static final class OptionButtonController<C extends JComponent> implements ActionListener {
    public final JBOptionButton button = new OptionButton();
    public final JButton bundledButton = new JButton();
    final JButton uninstallButton = new JButton();

    private final EnableDisableAction<C> myEnableAction;
    private final EnableDisableAction<C> myDisableAction;
    private final UninstallAction<C> myUninstallAction;
    private final AbstractAction myUninstallButton;
    private EnableDisableAction<C> myCurrentAction;

    OptionButtonController(@NotNull EnableDisableAction<C> enableAction,
                           @NotNull EnableDisableAction<C> disableAction,
                           @NotNull UninstallAction<C> uninstallAction) {
      myEnableAction = enableAction;
      myDisableAction = disableAction;
      myUninstallAction = uninstallAction;
      button.setAction(new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          OptionButtonController.this.actionPerformed(null);
        }
      });

      myUninstallButton = new AbstractAction(uninstallAction.getTemplateText()) {
        @Override
        public void actionPerformed(ActionEvent e) {
          myUninstallAction.actionPerformed(AnActionEvent.createFromDataContext("", null, DataContext.EMPTY_CONTEXT));
        }
      };
      button.setOptions(new Action[]{myUninstallButton});

      bundledButton.setOpaque(false);
      bundledButton.addActionListener(this);

      uninstallButton.setOpaque(false);
      uninstallButton.setAction(myUninstallButton);
    }

    public void update() {
      Presentation presentation = new Presentation();
      AnActionEvent event = AnActionEvent.createFromDataContext("", presentation, DataContext.EMPTY_CONTEXT);

      myEnableAction.update(event);
      myCurrentAction = presentation.isEnabledAndVisible() ? myEnableAction : myDisableAction;

      String text = myCurrentAction.getTemplateText();
      button.getAction().putValue(Action.NAME, text);
      bundledButton.setText(text);

      presentation = new Presentation();
      event = AnActionEvent.createFromDataContext("", presentation, DataContext.EMPTY_CONTEXT);
      myUninstallAction.update(event);
      myUninstallButton.putValue(Action.NAME, presentation.getText());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myCurrentAction.actionPerformed(AnActionEvent.createFromDataContext("", null, DataContext.EMPTY_CONTEXT));
    }
  }

  private static final class OptionButton extends JBOptionButton {
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
        int oldWidth = size.width;
        int newWidth = getWidth() - insets.left - insets.right;
        if (oldWidth <= newWidth || newWidth / (double)oldWidth > 0.85) {
          size.width = newWidth;
        }
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

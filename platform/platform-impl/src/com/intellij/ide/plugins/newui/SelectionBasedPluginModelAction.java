// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.newui.buttons.OptionButton;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.util.Producer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.*;
import java.util.function.Function;

import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.util.containers.ContainerUtil.*;

abstract class SelectionBasedPluginModelAction<C extends JComponent> extends DumbAwareAction {

  protected final @NotNull PluginModelFacade myPluginModelFacade;
  protected final boolean myShowShortcut;
  private final @NotNull List<? extends C> mySelection;
  private final @NotNull Function<? super C, PluginUiModel> myPluginDescriptorGetter;

  protected SelectionBasedPluginModelAction(@NotNull @Nls String text,
                                            @NotNull PluginModelFacade pluginModelFacade,
                                            boolean showShortcut,
                                            @NotNull List<? extends C> selection,
                                            @NotNull Function<? super C, PluginUiModel> pluginModelGetter) {
    super(text);

    myPluginModelFacade = pluginModelFacade;
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

    EnableDisableAction(@NotNull PluginModelFacade pluginModelFacade,
                        @NotNull PluginEnableDisableAction action,
                        boolean showShortcut,
                        @NotNull List<? extends C> selection,
                        @NotNull Function<? super C, PluginUiModel> pluginModelGetter,
                        @NotNull Runnable onFinishAction) {
      super(action.getPresentableText(),
            pluginModelFacade,
            showShortcut,
            selection,
            pluginModelGetter);

      myAction = action;
      myOnFinishAction = onFinishAction;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Collection<PluginUiModel> descriptors = getAllDescriptors();
      List<PluginEnabledState> states = map(descriptors, myPluginModelFacade::getState);

      boolean allEnabled = all(states, PluginEnabledState.ENABLED::equals);
      boolean isForceEnableAll = myAction == PluginEnableDisableAction.ENABLE_GLOBALLY &&
                                 !allEnabled;

      boolean disabled = descriptors.isEmpty() ||
                         !all(states, myAction::isApplicable) ||
                         myAction == PluginEnableDisableAction.DISABLE_GLOBALLY &&
                         exists(descriptors, myPluginModelFacade::isPluginRequiredForProject);

      boolean enabled = !disabled;
      e.getPresentation().setEnabledAndVisible(isForceEnableAll || enabled);
      if (UiPluginManager.getInstance().hasPluginRequiresUltimateButItsDisabled(map(descriptors, PluginUiModel::getPluginId))) {
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
      myPluginModelFacade.setEnabledState(getAllDescriptors(), myAction);
      myOnFinishAction.run();
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

  static <C extends JComponent> @NotNull OptionButtonController<C> createOptionButton(@NotNull Function<? super @NotNull PluginEnableDisableAction, @NotNull EnableDisableAction<C>> createEnableDisableAction,
                                                                                      @NotNull UninstallAction<C> uninstallAction) {
    return new OptionButtonController<>(createEnableDisableAction.apply(PluginEnableDisableAction.ENABLE_GLOBALLY),
                                        createEnableDisableAction.apply(PluginEnableDisableAction.DISABLE_GLOBALLY),
                                        uninstallAction);
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
      boolean isEnableAction = presentation.isEnabledAndVisible();
      myCurrentAction = isEnableAction ? myEnableAction : myDisableAction;

      if (!isEnableAction) {
        myDisableAction.update(event);
        if (!presentation.isEnabledAndVisible()) {
          myCurrentAction = myEnableAction;
        }
      }

      String text = myCurrentAction.getTemplateText();
      button.getAction().putValue(Action.NAME, text);
      bundledButton.setText(text);

      presentation = new Presentation();
      event = AnActionEvent.createFromDataContext("", presentation, DataContext.EMPTY_CONTEXT);
      myUninstallAction.update(event);
      myUninstallButton.putValue(Action.NAME, presentation.getText());
    }

    public void setOptions(List<AnAction> options) {
      button.setOptions(options);
    }

    public void setText(@Nls String text) {
      button.getAction().putValue(Action.NAME, text);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myCurrentAction.actionPerformed(AnActionEvent.createFromDataContext("", null, DataContext.EMPTY_CONTEXT));
    }
  }
}

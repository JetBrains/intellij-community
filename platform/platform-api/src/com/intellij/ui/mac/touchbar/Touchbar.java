// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.OptionAction;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.components.JBOptionButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

//
// Public API for assigning touchbar actions to ui-component
//
public class Touchbar {
  public static @Nullable ActionGroup getActions(@NotNull JComponent component) {
    return ComponentUtil.getClientProperty(component, ACTION_GROUP_KEY);
  }

  //
  // Use setActions to link action-group with JComponent.
  // Linked group will be shown in touchbar when JComponent gains focus.
  //

  public static void setActions(@NotNull JComponent component, @Nullable ActionGroup group) {
    if (!SystemInfo.isMac || ApplicationManager.getApplication() == null) return;
    ComponentUtil.putClientProperty(component, ACTION_GROUP_KEY, group);
  }
  public static void setActions(@NotNull JComponent component, @Nullable AnAction action) {
    setActions(component,action == null ? null : new DefaultActionGroup(action));
  }
  public static void setActions(@NotNull JComponent component, @NotNull String actionId) {
    setActions(component, ActionManager.getInstance().getAction(actionId));
  }
  public static void addActions(@NotNull JComponent component, @Nullable ActionGroup group) {
    if (!SystemInfo.isMac || ApplicationManager.getApplication() == null) return;
    ActionGroup old = ComponentUtil.getClientProperty(component, ACTION_GROUP_KEY);
    if (old == null) {
      setActions(component, group);
    } else if (old instanceof DefaultActionGroup && group != null) {
      ((DefaultActionGroup)old).addAll(group);
    }
  }

  //
  // The same as setActions, but internally generates action-group wrapper from the set of JButtons
  //

  public static void setButtonActions(@NotNull JComponent component, JButton button) {
    setButtonActions(component, Collections.singletonList(button));
  }
  public static void setButtonActions(@NotNull JComponent component, JButton[] buttons) {
    setButtonActions(component, Arrays.asList(buttons));
  }
  public static void setButtonActions(@NotNull JComponent component, Collection<JButton> buttons) {
    setButtonActions(component, buttons, null, null);
  }
  public static void setButtonActions(@NotNull JComponent component, Collection<JButton> buttons, Collection<JButton> principal, JButton defaultButton) {
    setButtonActions(component, buttons, principal, defaultButton, null);
  }
  public static void setButtonActions(@NotNull JComponent component,
                                      Collection<JButton> buttons,
                                      Collection<JButton> principal,
                                      JButton defaultButton,
                                      @Nullable ActionGroup extraActions) {
    if (!SystemInfo.isMac || ApplicationManager.getApplication() == null) return;

    ActionManagerEx.doWithLazyActionManager(instance -> {
      DefaultActionGroup result = new DefaultActionGroup();
      if (buttons != null) {
        result.add(buildActionsFromButtons(buttons, defaultButton, false));
      }
      if (extraActions != null) {
        result.add(extraActions);
      }
      if (principal != null) {
        result.add(buildActionsFromButtons(principal, defaultButton, true));
      }

      setActions(component, result);
    });
  }
  public static void addButtonAction(@NotNull JComponent component, JButton button) {
    if (!SystemInfo.isMac || ApplicationManager.getApplication() == null) return;
    ActionGroup old = ComponentUtil.getClientProperty(component, ACTION_GROUP_KEY);
    if (old == null) {
      setButtonActions(component, button);
    } else if (old instanceof DefaultActionGroup) {
      final AnAction anAct = _createActionFromButton(button.getAction(), button, false);
      if (anAct != null)
        ((DefaultActionGroup)old).add(anAct);
    }
  }

  //
  // Private
  //

  private static final Key<ActionGroup> ACTION_GROUP_KEY = Key.create("Touchbar.ActionGroup.key");
  private static final boolean EXPAND_OPTION_BUTTONS = Boolean.getBoolean("Touchbar.expand.option.button");

  private static @NotNull DefaultActionGroup buildActionsFromButtons(Collection<JButton> buttons,
                                                                     JButton defaultButton,
                                                                     boolean isPrincipal) {
    final DefaultActionGroup result = new DefaultActionGroup();

    if (EXPAND_OPTION_BUTTONS) {
      DefaultActionGroup options = null;
      for (JButton jb : buttons) {
        if (jb instanceof JBOptionButton) {
          final JBOptionButton ob = (JBOptionButton)jb;
          final Action[] opts = ob.getOptions();
          if (opts != null) {
            for (Action a : opts) {
              if (a == null) continue;
              final AnAction anAct = _createActionFromButton(a, ob, true);
              if (anAct == null) continue;
              if (options == null) options = new DefaultActionGroup();
              options.add(anAct);
            }
          }
        }
      }
      if (options != null)
        result.add(options);
    }

    for (JButton jb : buttons) {
      final AnAction anAct = _createActionFromButton(jb.getAction(), jb, false);
      if (anAct != null) {
        if (jb == defaultButton) {
          TouchbarActionCustomizations.setDefault(anAct, true);
        }
        result.add(anAct);
      }
    }

    if (isPrincipal) {
      TouchbarActionCustomizations.setPrincipal(result, true);
    }

    return result;
  }

  private static AnAction _createActionFromButton(@Nullable Action action,
                                                  @NotNull JButton button,
                                                  boolean useTextFromAction /*for optional buttons*/) {
    Object anAct = action == null ? null : action.getValue(OptionAction.AN_ACTION);
    if (anAct == null) {
      anAct = new DumbAwareAction() {
        {
          setEnabledInModalContext(true);
          if (useTextFromAction) {
            final Object name = action == null ? button.getText() : action.getValue(Action.NAME);
            getTemplatePresentation().setText(name instanceof String ? (String)name : "");
          }
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          if (action == null) {
            button.doClick();
            return;
          }
          // also can be used something like: ApplicationManager.getApplication().invokeLater(() -> jb.doClick(), ms)
          action.actionPerformed(new ActionEvent(button, ActionEvent.ACTION_PERFORMED, null));
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setEnabled(action == null ? button.isEnabled() : action.isEnabled());
          if (!useTextFromAction) {
            e.getPresentation().setText(DialogWrapper.extractMnemonic(button.getText()).second);
          }
        }
      };
    }
    if (!(anAct instanceof AnAction)) {
      return null;
    }
    TouchbarActionCustomizations.setComponent((AnAction)anAct, button).setShowText(true).setShowImage(false);
    return (AnAction)anAct;
  }
}

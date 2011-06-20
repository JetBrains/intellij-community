/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class AddRemoveUpDownPanel extends JPanel {
  public static enum Buttons {
    ADD, REMOVE, UP, DOWN;

    Icon getIcon() {
      switch (this) {
        case ADD: return PlatformIcons.ADD_BIG;
        case REMOVE: return PlatformIcons.REMOVE_BIG;
        case UP: return PlatformIcons.UP_BIG;
        case DOWN: return PlatformIcons.DOWN_BIG;
      }
      return null;
    }


    TableActionButton createButton(final Listener listener) {
      return new TableActionButton(this, listener);
    }

    public String getText() {
      return StringUtil.capitalize(name().toLowerCase());
    }

    public void performAction(Listener listener) {
      switch (this) {
        case ADD:
          listener.doAdd();
          break;
        case REMOVE:
          listener.doRemove();
          break;
        case UP:
          listener.doUp();
          break;
        case DOWN:
          listener.doDown();
          break;
      }
    }
  }
  public interface Listener {
    void doAdd();
    void doRemove();
    void doUp();
    void doDown();
  }

  private Map<Buttons, TableActionButton> myButtons = new HashMap<Buttons, TableActionButton>();

  public AddRemoveUpDownPanel(Listener listener, @Nullable JComponent contentPane,
                              @Nullable AnAction[] additionalActions, Buttons... buttons) {
    super(new VerticalFlowLayout(VerticalFlowLayout.TOP));
    AnAction[] actions = new AnAction[buttons.length];
    for (int i = 0; i < buttons.length; i++) {
      Buttons button = buttons[i];
      final TableActionButton b = button.createButton(listener);
      actions[i] = b;
      myButtons.put(button, b);
      final Shortcut shortcut = b.getShortcut();
      if (contentPane != null && shortcut != null) {
        b.registerCustomShortcutSet(new CustomShortcutSet(shortcut), contentPane);
      }
    }
    if (additionalActions != null && additionalActions.length > 0) {
      final ArrayList<AnAction> allActions = new ArrayList<AnAction>(Arrays.asList(actions));
      allActions.addAll(Arrays.asList(additionalActions));
      actions = allActions.toArray(new AnAction[allActions.size()]);
      for (final AnAction action : additionalActions) {
        if (action instanceof ShortcutProvider && contentPane != null) {
          final Shortcut shortcut = ((ShortcutProvider)action).getShortcut();
          if (shortcut != null) {
            action.registerCustomShortcutSet(new CustomShortcutSet(shortcut), contentPane);
          }
        }
      }
    }
    add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, new DefaultActionGroup(actions), false).getComponent());
  }

  public void setEnabled(Buttons button, boolean enabled) {
    final TableActionButton b = myButtons.get(button);
    if (b != null) {
      b.setEnabled(enabled);
    }
  }

  public AddRemoveUpDownPanel(Listener listener, @Nullable JComponent contentPane, @Nullable AnAction[] additionalActions) {
    this(listener, contentPane, additionalActions,  Buttons.ADD, Buttons.REMOVE, Buttons.UP, Buttons.DOWN);
  }

  static class TableActionButton extends AnAction implements ShortcutProvider {
    private boolean enabled = true;
    private final Buttons myButton;
    private final Listener myListener;

    TableActionButton(Buttons button, Listener listener) {
      super(button.getText(), button.getText(), button.getIcon());
      myButton = button;
      myListener = listener;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myButton.performAction(myListener);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(enabled);
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    @Override
    public Shortcut getShortcut() {
      switch (myButton) {
        case ADD: return KeyboardShortcut.fromString("alt A");
        case REMOVE: return KeyboardShortcut.fromString("alt DELETE");
        case UP: return KeyboardShortcut.fromString("alt UP");
        case DOWN: return KeyboardShortcut.fromString("alt DOWN");
      }
      return null;
    }
  }
}

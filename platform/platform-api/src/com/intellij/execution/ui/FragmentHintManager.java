// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.EmptyAction;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FragmentHintManager {
  private final List<SettingsEditorFragment<?, ?>> myFragments = new ArrayList<>();
  private final @NotNull Consumer<? super String> myHintConsumer;
  private final String myDefaultHint;

  public FragmentHintManager(@NotNull Consumer<? super @NlsContexts.DialogMessage String> hintConsumer,
                             @NlsContexts.DialogMessage @Nullable String defaultHint,
                             @NotNull Disposable disposable) {
    myHintConsumer = hintConsumer;
    myDefaultHint = defaultHint;
    hintConsumer.consume(defaultHint);

    AWTEventListener listener = event -> processKeyEvent((KeyEvent)event);
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.KEY_EVENT_MASK);
    Disposer.register(disposable, () -> Toolkit.getDefaultToolkit().removeAWTEventListener(listener));
  }

  public void registerFragments(Collection<? extends SettingsEditorFragment<?, ?>> fragments) {
    fragments.forEach(fragment -> registerFragment(fragment));
  }

  public void registerFragment(SettingsEditorFragment<?, ?> fragment) {
    myFragments.add(fragment);
    for (JComponent jComponent : fragment.getAllComponents()) {
      registerComponent(fragment, getComponent(jComponent));
    }
  }

  private void registerComponent(SettingsEditorFragment<?, ?> fragment, JComponent component) {
    component.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        showHint(fragment, component);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        showHint(null, component);
      }
    });

    component.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        showHint(fragment, component);
      }

      @Override
      public void focusLost(FocusEvent e) {
        showHint(null, component);
      }
    });
  }

  private static JComponent getComponent(JComponent component) {
    if (component instanceof LabeledComponent) {
      component = ((LabeledComponent<?>)component).getComponent();
    }
    if (component instanceof RawCommandLineEditor) {
      component = ((RawCommandLineEditor)component).getEditorField();
    }
    if (component instanceof ComponentWithBrowseButton) {
      component = ((ComponentWithBrowseButton<?>)component).getChildComponent();
    }
    return component;
  }

  private void showHint(@Nullable SettingsEditorFragment<?, ?> fragment, @Nullable JComponent component) {
    String hint = myDefaultHint;
    if (fragment != null) {
      hint = fragment.getHint(component);
    }
    else {
      fragment = ContainerUtil.find(myFragments, f -> f.getEditorComponent().hasFocus());
      if (fragment != null) {
        hint = fragment.getHint(component);
      }
    }
    if (fragment != null) {
      String text = getShortcutText(fragment);
      if (text != null) {
        hint = hint == null ? text : hint + ". " + text;
      }
    }
    myHintConsumer.consume(hint == null ? myDefaultHint : hint);
  }

  private static @Nullable @NlsSafe String getShortcutText(@NotNull SettingsEditorFragment<?, ?> fragment) {
    ShortcutSet shortcut = ActionUtil.getMnemonicAsShortcut(new EmptyAction(fragment.getName(), null, null));
    if (shortcut != null && shortcut.getShortcuts().length > 0) {
      return KeymapUtil.getShortcutsText(new Shortcut[]{ArrayUtil.getLastElement(shortcut.getShortcuts())});
    }
    return null;
  }

  private void processKeyEvent(KeyEvent keyEvent) {
    if (keyEvent.getKeyCode() != KeyEvent.VK_ALT) return;
    if (keyEvent.getID() == KeyEvent.KEY_PRESSED) {
      for (SettingsEditorFragment<?, ?> fragment : myFragments) {
        JComponent component = fragment.getComponent();
        if (fragment.isSelected() && fragment.getName() != null && component.getRootPane() != null) {
          JComponent hintComponent = createHintComponent(fragment);
          Rectangle rect = component.getVisibleRect();
          RelativePoint point = new RelativePoint(component, new Point(rect.x + rect.width - hintComponent.getPreferredSize().width,
                                                                       rect.y - hintComponent.getPreferredSize().height + 5));
          HintManager.getInstance().showHint(hintComponent, point, HintManager.HIDE_BY_ANY_KEY, -1);
        }
      }
    }
    else if (keyEvent.getID() == KeyEvent.KEY_RELEASED) {
      HintManager.getInstance().hideAllHints();
    }
  }

  private static JComponent createHintComponent(SettingsEditorFragment<?, ?> fragment) {
    SimpleColoredComponent component = new SimpleColoredComponent();
    component.append(fragment.getName().replace("\u001b", ""), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    String shortcutText = getShortcutText(fragment);
    if (shortcutText != null) {
      String last = StringUtil.last(shortcutText, 1, false).toString();
      component.append(" " + StringUtil.trimEnd(shortcutText, last), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      component.append(last);
    }
    return component;
  }
}

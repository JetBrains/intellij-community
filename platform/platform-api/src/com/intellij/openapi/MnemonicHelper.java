/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi;

import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ComponentTreeWatcher;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.DialogUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Automatically locates &amp; characters in texts of buttons and labels on a component or dialog,
 * registers the mnemonics for those characters and removes them from the control text.
 *
 * @author lesya
 * @since 5.1
 */
public class MnemonicHelper extends ComponentTreeWatcher {
  private static final MnemonicContainerListener LISTENER = new MnemonicContainerListener();
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.MnemonicHelper");
  private Map<Integer, String> myMnemonics = null;

  public static final PropertyChangeListener TEXT_LISTENER = new PropertyChangeListener() {
   public void propertyChange(PropertyChangeEvent evt) {
     final Object source = evt.getSource();
     //noinspection SSBasedInspection
     SwingUtilities.invokeLater(new Runnable() {
       // It is needed to process this event later,
       // because the method is invoked from the setText method
       // before Swing updates mnemonics
       @Override
       public void run() {
         if (source instanceof AbstractButton) {
           // see javax.swing.AbstractButton.setText
           DialogUtil.registerMnemonic(((AbstractButton)source));
         } else if (source instanceof JLabel) {
           // javax.swing.JLabel.setText
           DialogUtil.registerMnemonic(((JLabel)source), null);
         }
       }
     });
   }
  };
  @NonNls public static final String TEXT_CHANGED_PROPERTY = "text";

  /**
   * @see #init(Component)
   * @deprecated do not use this object as a tree watcher
   */
  @Deprecated
  public MnemonicHelper() {
    super(ArrayUtil.EMPTY_CLASS_ARRAY);
  }

  protected void processComponent(Component parentComponent) {
    if (parentComponent instanceof AbstractButton) {
      final AbstractButton abstractButton = ((AbstractButton)parentComponent);
      abstractButton.addPropertyChangeListener(AbstractButton.TEXT_CHANGED_PROPERTY, TEXT_LISTENER);
      DialogUtil.registerMnemonic(abstractButton);
      checkForDuplicateMnemonics(abstractButton);
      fixMacMnemonicKeyStroke(abstractButton, null);
    } else if (parentComponent instanceof JLabel) {
      final JLabel jLabel = ((JLabel)parentComponent);
      jLabel.addPropertyChangeListener(TEXT_CHANGED_PROPERTY, TEXT_LISTENER);
      DialogUtil.registerMnemonic(jLabel, null);
      checkForDuplicateMnemonics(jLabel);
      fixMacMnemonicKeyStroke(jLabel, "release"); // "release" only is OK for labels
    }
  }

  private static void fixMacMnemonicKeyStroke(JComponent component, String type) {
    if (SystemInfo.isMac && Registry.is("ide.mac.alt.mnemonic.without.ctrl")) {
      // hack to make component's mnemonic work for ALT+KEY_CODE on Macs.
      // Default implementation uses ALT+CTRL+KEY_CODE (see BasicLabelUI).
      InputMap inputMap = component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
      if (inputMap != null) {
        KeyStroke[] strokes = inputMap.allKeys();
        if (strokes != null) {
          int mask = KeyEvent.ALT_MASK | KeyEvent.CTRL_MASK;
          for (KeyStroke stroke : strokes) {
            if (mask == (mask & stroke.getModifiers())) {
              inputMap.put(getKeyStrokeWithoutCtrlModifier(stroke), type != null ? type : inputMap.get(stroke));
            }
          }
        }
      }
    }
  }

  private static KeyStroke getKeyStrokeWithoutCtrlModifier(KeyStroke stroke) {
    try {
      Method method = AWTKeyStroke.class.getDeclaredMethod("getCachedStroke", char.class, int.class, int.class, boolean.class);
      method.setAccessible(true);
      int modifiers = stroke.getModifiers() & ~InputEvent.CTRL_MASK & ~InputEvent.CTRL_DOWN_MASK;
      return (KeyStroke)method.invoke(null, stroke.getKeyChar(), stroke.getKeyCode(), modifiers, stroke.isOnKeyRelease());
    }
    catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  protected void unprocessComponent(Component component) {
  }

  public void checkForDuplicateMnemonics(JLabel label) {
    if (!Registry.is("ide.checkDuplicateMnemonics")) return;
    checkForDuplicateMnemonics(label.getDisplayedMnemonic(), label.getText());
  }

  public void checkForDuplicateMnemonics(AbstractButton button) {
    if (!Registry.is("ide.checkDuplicateMnemonics")) return;
    checkForDuplicateMnemonics(button.getMnemonic(), button.getText());
  }

  public void checkForDuplicateMnemonics(int mnemonic, String text) {
    if (mnemonic == 0) return;
    if (myMnemonics == null) myMnemonics = new HashMap();
    final String other = myMnemonics.get(Integer.valueOf(mnemonic));
    if (other != null && !other.equals(text)) {
      LOG.error("conflict: multiple components with mnemonic '" + (char)mnemonic + "' seen on '" + text + "' and '" + other + "'");
    }
    myMnemonics.put(Integer.valueOf(mnemonic), text);
  }

  /**
   * Creates shortcut for mnemonic replacing standard Alt+Letter to Ctrl+Alt+Letter on Mac with jdk version newer than 6
   * @param ch mnemonic letter
   * @return shortcut for mnemonic
   */
  public static CustomShortcutSet createShortcut(char ch) {
    Character mnemonic = Character.valueOf(ch);
    String shortcut = SystemInfo.isMac && SystemInfo.isJavaVersionAtLeast("1.7") ?
                      "control alt pressed " + mnemonic :
                      "alt pressed " + mnemonic;
    return CustomShortcutSet.fromString(shortcut);
  }

  /**
   * Initializes mnemonics support for the specified component and for its children if needed.
   *
   * @param component the root component of the hierarchy
   */
  public static void init(Component component) {
    if (Registry.is("ide.mnemonic.helper.old") || Registry.is("ide.checkDuplicateMnemonics")) {
      new MnemonicHelper().register(component);
    }
    else {
      LISTENER.addTo(component);
    }
  }
}

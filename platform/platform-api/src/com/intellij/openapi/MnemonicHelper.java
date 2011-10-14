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
package com.intellij.openapi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ComponentTreeWatcher;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.DialogUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.MnemonicHelper");
  private Map<Integer, String> myMnemonics = null;

  public static final PropertyChangeListener TEXT_LISTENER = new PropertyChangeListener() {
   public void propertyChange(PropertyChangeEvent evt) {
     final Object source = evt.getSource();
     if (source instanceof AbstractButton) {
       DialogUtil.registerMnemonic(((AbstractButton)source));
     } else if (source instanceof JLabel) {
       DialogUtil.registerMnemonic(((JLabel)source), null);
     }
   }
  };
  @NonNls public static final String TEXT_CHANGED_PROPERTY = "text";

  public MnemonicHelper() {
    super(ArrayUtil.EMPTY_CLASS_ARRAY);
  }

  protected void processComponent(Component parentComponent) {
    if (parentComponent instanceof AbstractButton) {
      final AbstractButton abstractButton = ((AbstractButton)parentComponent);
      abstractButton.addPropertyChangeListener(AbstractButton.TEXT_CHANGED_PROPERTY, TEXT_LISTENER);
      DialogUtil.registerMnemonic(abstractButton);
      checkForDuplicateMnemonics(abstractButton);
    } else if (parentComponent instanceof JLabel) {
      final JLabel jLabel = ((JLabel)parentComponent);
      jLabel.addPropertyChangeListener(TEXT_CHANGED_PROPERTY, TEXT_LISTENER);
      DialogUtil.registerMnemonic(jLabel, null);
      checkForDuplicateMnemonics(jLabel);
      if (SystemInfo.isMac) {
        // hack to make Labels mnemonic work for ALT+KEY_CODE on Macs.
        // Default implementation uses ALT+CTRL+KEY_CODE (see BasicLabelUI).
        final InputMap inputMap = jLabel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        if (inputMap != null) {
          final KeyStroke[] strokes = inputMap.allKeys();
          if (strokes != null) {
            for (KeyStroke stroke : strokes) {
              final int m = stroke.getModifiers();
              // to be sure if default mnemonic exist
              if (((m & KeyEvent.ALT_MASK) == KeyEvent.ALT_MASK) && ((m & KeyEvent.CTRL_MASK) == KeyEvent.CTRL_MASK)) {
                inputMap.put(KeyStroke.getKeyStroke(stroke.getKeyCode(), KeyEvent.ALT_MASK), "release"); // "release" only is OK
              }
            }
          }
        }
      }
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
}

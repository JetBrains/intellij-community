/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.Patches;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;

/**
 * @author Sergey.Malenkov
 */
abstract class MnemonicWrapper<T extends Component> implements Runnable, PropertyChangeListener {
  public static MnemonicWrapper getWrapper(Component component) {
    if (component == null || component.getClass().getName().equals("com.intellij.openapi.wm.impl.StripeButton")) {
      return null;
    }
    for (PropertyChangeListener listener : component.getPropertyChangeListeners()) {
      if (listener instanceof MnemonicWrapper) {
        MnemonicWrapper wrapper = (MnemonicWrapper)listener;
        wrapper.run(); // update mnemonics immediately
        return wrapper;
      }
    }
    if (component instanceof JMenuItem) {
      return null; // TODO: new MenuWrapper((JMenuItem)component);
    }
    if (component instanceof AbstractButton) {
      return new ButtonWrapper((AbstractButton)component);
    }
    if (component instanceof JLabel) {
      return new LabelWrapper((JLabel)component);
    }
    return null;
  }

  final T myComponent; // direct access from inner classes
  private final String myTextProperty;
  private final String myCodeProperty;
  private final String myIndexProperty;
  private int myCode;
  private int myIndex;
  private boolean myFocusable;
  private boolean myEvent;
  private Runnable myRunnable;

  private MnemonicWrapper(T component, String text, String code, String index) {
    myComponent = component;
    myTextProperty = text;
    myCodeProperty = code;
    myIndexProperty = index;
    if (!updateText()) {
      // assume that it is already set
      myCode = getMnemonicCode();
      myIndex = getMnemonicIndex();
    }
    myFocusable = isFocusable();
    myComponent.addPropertyChangeListener(this);
    run(); // update mnemonics immediately
  }

  @Override
  public final void run() {
    boolean disabled = isDisabled();
    try {
      myEvent = true;
      setMnemonicCode(disabled ? KeyEvent.VK_UNDEFINED : myCode);
      setMnemonicIndex(disabled ? -1 : myIndex);
      Component component = getFocusableComponent();
      if (component != null) {
        component.setFocusable(disabled || myFocusable);
      }
    }
    finally {
      myEvent = false;
      myRunnable = null;
    }
  }

  @Override
  public final void propertyChange(PropertyChangeEvent event) {
    if (!myEvent) {
      String property = event.getPropertyName();
      if (myTextProperty.equals(property)) {
        if (updateText()) {
          updateRequest();
        }
      }
      else if (myCodeProperty.equals(property)) {
        myCode = getMnemonicCode();
        updateRequest();
      }
      else if (myIndexProperty.equals(property)) {
        myIndex = getMnemonicIndex();
        updateRequest();
      }
      else if ("focusable".equals(property) || "labelFor".equals(property)) {
        myFocusable = isFocusable();
        updateRequest();
      }
    }
  }

  private boolean updateText() {
    String text = getText();
    if (text != null) {
      int code = KeyEvent.VK_UNDEFINED;
      int index = -1;
      int length = text.length();
      StringBuilder sb = new StringBuilder(length);
      for (int i = 0; i < length; i++) {
        char ch = text.charAt(i);
        if (ch != UIUtil.MNEMONIC) {
          sb.append(ch);
        }
        else if (i + 1 < length) {
          code = getExtendedKeyCodeForChar(text.charAt(i + 1));
          index = sb.length();
        }
      }
      if (code != KeyEvent.VK_UNDEFINED) {
        try {
          myEvent = true;
          setText(sb.toString());
        }
        finally {
          myEvent = false;
        }
        myCode = code;
        myIndex = index;
        return true;
      }
    }
    return false;
  }

  private void updateRequest() {
    if (myRunnable == null) {
      myRunnable = this; // run once
      SwingUtilities.invokeLater(this);
    }
  }

  private boolean isFocusable() {
    Component component = getFocusableComponent();
    return component == null || component.isFocusable();
  }

  Component getFocusableComponent() {
    return myComponent;
  }

  boolean isDisabled() {
    return UISettings.getShadowInstance().DISABLE_MNEMONICS_IN_CONTROLS;
  }

  abstract String getText();

  abstract void setText(String text);

  abstract int getMnemonicCode();

  abstract void setMnemonicCode(int code);

  abstract int getMnemonicIndex();

  abstract void setMnemonicIndex(int index);

  static KeyStroke fixMacKeyStroke(KeyStroke stroke, InputMap map, int code, boolean onKeyRelease, String action) {
    if (stroke != null && code != stroke.getKeyCode()) {
      map.remove(stroke);
      stroke = null;
    }
    if (stroke == null && code != KeyEvent.VK_UNDEFINED) {
      stroke = KeyStroke.getKeyStroke(code, InputEvent.ALT_MASK | InputEvent.ALT_DOWN_MASK, onKeyRelease);
      map.put(stroke, action);
    }
    return stroke;
  }

  // TODO: HACK because of Java7 required:
  // replace later with KeyEvent.getExtendedKeyCodeForChar(ch)
  private static int getExtendedKeyCodeForChar(int ch) {
    //noinspection ConstantConditions
    assert Patches.USE_REFLECTION_TO_ACCESS_JDK7;
    try {
      Method method = KeyEvent.class.getMethod("getExtendedKeyCodeForChar", int.class);
      if (!method.isAccessible()) {
        method.setAccessible(true);
      }
      return (Integer)method.invoke(KeyEvent.class, ch);
    }
    catch (Exception exception) {
      if (ch >= 'a' && ch <= 'z') {
        ch -= ('a' - 'A');
      }
      return ch;
    }
  }

  private static class MenuWrapper extends ButtonWrapper {
    private MenuWrapper(AbstractButton component) {
      super(component);
    }

    @Override
    boolean isDisabled() {
      return UISettings.getShadowInstance().DISABLE_MNEMONICS;
    }
  }

  private static class ButtonWrapper extends MnemonicWrapper<AbstractButton> {
    private KeyStroke myStrokePressed;
    private KeyStroke myStrokeReleased;

    private ButtonWrapper(AbstractButton component) {
      super(component, "text", "mnemonic", "displayedMnemonicIndex");
    }

    @Override
    String getText() {
      return myComponent.getText();
    }

    @Override
    void setText(String text) {
      myComponent.setText(text);
    }

    @Override
    int getMnemonicCode() {
      return myComponent.getMnemonic();
    }

    @Override
    void setMnemonicCode(int code) {
      myComponent.setMnemonic(code);
      if (SystemInfo.isMac && Registry.is("ide.mac.alt.mnemonic.without.ctrl")) {
        InputMap map = myComponent.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        if (map != null) {
          myStrokePressed = fixMacKeyStroke(myStrokePressed, map, code, false, "pressed");
          myStrokeReleased = fixMacKeyStroke(myStrokeReleased, map, code, true, "released");
        }
      }
    }

    @Override
    int getMnemonicIndex() {
      return myComponent.getDisplayedMnemonicIndex();
    }

    @Override
    void setMnemonicIndex(int index) {
      myComponent.setDisplayedMnemonicIndex(index);
    }
  }

  private static class LabelWrapper extends MnemonicWrapper<JLabel> {
    private KeyStroke myStrokeRelease;

    private LabelWrapper(JLabel component) {
      super(component, "text", "displayedMnemonic", "displayedMnemonicIndex");
    }

    @Override
    String getText() {
      return myComponent.getText();
    }

    @Override
    void setText(String text) {
      myComponent.setText(text);
    }

    @Override
    int getMnemonicCode() {
      return myComponent.getDisplayedMnemonic();
    }

    @Override
    void setMnemonicCode(int code) {
      myComponent.setDisplayedMnemonic(code);
      if (SystemInfo.isMac && Registry.is("ide.mac.alt.mnemonic.without.ctrl")) {
        InputMap map = myComponent.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        if (map != null) {
          myStrokeRelease = fixMacKeyStroke(myStrokeRelease, map, code, true, "release");
        }
      }
    }

    @Override
    int getMnemonicIndex() {
      return myComponent.getDisplayedMnemonicIndex();
    }

    @Override
    void setMnemonicIndex(int index) {
      myComponent.setDisplayedMnemonicIndex(index);
    }

    @Override
    Component getFocusableComponent() {
      return myComponent.getLabelFor();
    }
  }
}

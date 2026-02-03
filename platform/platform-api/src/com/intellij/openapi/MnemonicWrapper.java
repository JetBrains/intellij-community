// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi;

import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.ui.ClientProperty;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

abstract class MnemonicWrapper<T extends JComponent> implements Runnable, PropertyChangeListener {

  public static MnemonicWrapper<?> getWrapper(Component component) {
    if (component == null || ClientProperty.isTrue(component, MnemonicHelper.DISABLE_MNEMONIC_PROCESSING)) {
      return null;
    }
    for (PropertyChangeListener listener : component.getPropertyChangeListeners()) {
      if (listener instanceof MnemonicWrapper<?> wrapper) {
        wrapper.run(); // update mnemonics immediately
        return wrapper;
      }
    }
    if (component instanceof JMenuItem) {
      return new MenuWrapper((AbstractButton)component);
    }
    if (component instanceof AbstractButton) {
      return new ButtonWrapper((AbstractButton)component);
    }
    if (component instanceof JLabel) {
      return new LabelWrapper((JLabel)component);
    }
    return null;
  }

  final @NotNull T myComponent; // direct access from inner classes
  private final String myTextProperty;
  private final String myCodeProperty;
  private final String myIndexProperty;
  private TextWithMnemonic myTextWithMnemonic;
  private boolean myFocusable;
  private boolean myEvent;
  private boolean myMnemonicChanged;
  private boolean myRunScheduled;

  private MnemonicWrapper(@NotNull T component, String text, String code, String index) {
    myComponent = component;
    myTextProperty = text;
    myCodeProperty = code;
    myIndexProperty = index;
    myFocusable = isFocusable();
    myComponent.addPropertyChangeListener(this);
    run(); // update text and mnemonics immediately
  }

  @Override
  public final void run() {
    boolean disabled = !LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred() ||
                       UISettings.getShadowInstance().getDisableMnemonicsInControls();
    try {
      myEvent = true;
      if (myTextWithMnemonic == null) {
        myTextWithMnemonic = createTextWithMnemonic();
      }
      else if (myMnemonicChanged) {
        try {
          myTextWithMnemonic = myTextWithMnemonic.withMnemonicIndex(getMnemonicIndex());
        }
        catch (IndexOutOfBoundsException cause) {
          myTextWithMnemonic = myTextWithMnemonic.withMnemonicIndex(-1);
          String message = "cannot change mnemonic index " + myComponent;
          Logger.getInstance(MnemonicWrapper.class).warn(message, cause);
        }
      }
      // update component text only if changed
      String text = myTextWithMnemonic.getText(!disabled);
      if (!text.equals(Strings.notNullize(getText()))) setText(text);
      // update mnemonic code only if changed
      int code = disabled ? KeyEvent.VK_UNDEFINED : myTextWithMnemonic.getMnemonicCode();
      if (code != getMnemonicCode()) setMnemonicCode(code);
      // update input map to support Alt-based mnemonics
      if (SystemInfo.isMac && Registry.is("ide.mac.alt.mnemonic.without.ctrl", true)) {
        InputMap map = myComponent.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        if (map != null) updateInputMap(map, code);
      }
      // update mnemonic index only if changed
      int index = disabled ? -1 : myTextWithMnemonic.getMnemonicIndex();
      if (index != getMnemonicIndex()) {
        try {
          setMnemonicIndex(index);
        }
        catch (IllegalArgumentException cause) {
          // EA-94674 - IAE: AbstractButton.setDisplayedMnemonicIndex
          StringBuilder sb = new StringBuilder("cannot set mnemonic index ");
          if (myMnemonicChanged) sb.append("if mnemonic changed ");
          String message = sb.append(myComponent).toString();
          Logger.getInstance(MnemonicWrapper.class).warn(message, cause);
        }
      }
      Component component = getFocusableComponent();
      if (component != null) {
        component.setFocusable(disabled || myFocusable);
      }
    }
    finally {
      myEvent = false;
      myMnemonicChanged = false;
      myRunScheduled = false;
    }
  }

  @Override
  public final void propertyChange(PropertyChangeEvent event) {
    if (!myEvent && myTextWithMnemonic != null) {
      String property = event.getPropertyName();
      if (myTextProperty.equals(property)) {
        // it is needed to update text later because
        // this listener is notified before Swing updates mnemonics
        myTextWithMnemonic = null;
        updateRequest();
      }
      else if (myCodeProperty.equals(property)) {
        myMnemonicChanged = true;
        updateRequest();
      }
      else if (myIndexProperty.equals(property)) {
        myMnemonicChanged = true;
        updateRequest();
      }
      else if ("focusable".equals(property) || "labelFor".equals(property)) {
        myFocusable = isFocusable();
        updateRequest();
      }
    }
  }

  private TextWithMnemonic createTextWithMnemonic() {
    String text = getText();
    if (Strings.isEmpty(text)) return TextWithMnemonic.EMPTY;
    TextWithMnemonic mnemonic = TextWithMnemonic.fromMnemonicText(text, false);
    if (mnemonic != null) return mnemonic;
    // assume that it is already set
    int index = getMnemonicIndex();
    return 0 <= index && index < text.length()
           ? TextWithMnemonic.fromPlainTextWithIndex(text, index)
           : TextWithMnemonic.fromPlainText(text, (char)getMnemonicCode());
  }

  private void updateRequest() {
    if (!myRunScheduled) {
      myRunScheduled = true; // run once
      SwingUtilities.invokeLater(this);
    }
  }

  private boolean isFocusable() {
    Component component = getFocusableComponent();
    return component == null || component.isFocusable();
  }

  @Nullable Component getFocusableComponent() {
    return myComponent;
  }

  abstract @Nls String getText();

  abstract void setText(@Nls String text);

  abstract int getMnemonicCode();

  abstract void setMnemonicCode(int code);

  abstract int getMnemonicIndex();

  abstract void setMnemonicIndex(int index);

  abstract void updateInputMap(InputMap map, int code);

  static KeyStroke fixMacKeyStroke(KeyStroke stroke, InputMap map, int code, boolean onKeyRelease, String action) {
    if (stroke != null && code != stroke.getKeyCode()) {
      map.remove(stroke);
      stroke = null;
    }
    if (stroke == null && code != KeyEvent.VK_UNDEFINED) {
      stroke = KeyStroke.getKeyStroke(code, InputEvent.ALT_DOWN_MASK, onKeyRelease);
      map.put(stroke, action);
    }
    return stroke;
  }

  private static final class MenuWrapper extends AbstractButtonWrapper {
    private KeyStroke myStrokePressed;

    private MenuWrapper(@NotNull AbstractButton component) {
      super(component);
    }

    @Override
    void updateInputMap(InputMap map, int code) {
      myStrokePressed = fixMacKeyStroke(myStrokePressed, map, code, false, "selectMenu");
    }
  }

  private static final class ButtonWrapper extends AbstractButtonWrapper {
    private KeyStroke myStrokePressed;
    private KeyStroke myStrokeReleased;

    private ButtonWrapper(@NotNull AbstractButton component) {
      super(component);
    }

    @Override
    void updateInputMap(InputMap map, int code) {
      myStrokePressed = fixMacKeyStroke(myStrokePressed, map, code, false, "pressed");
      myStrokeReleased = fixMacKeyStroke(myStrokeReleased, map, code, true, "released");
    }
  }

  private abstract static class AbstractButtonWrapper extends MnemonicWrapper<AbstractButton> {
    private AbstractButtonWrapper(@NotNull AbstractButton component) {
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

  private static final class LabelWrapper extends MnemonicWrapper<JLabel> {
    private KeyStroke myStrokePress;
    private KeyStroke myStrokeRelease;

    private LabelWrapper(@NotNull JLabel component) {
      super(component, "text", "displayedMnemonic", "displayedMnemonicIndex");
    }

    @Override
    void updateInputMap(InputMap map, int code) {
      myStrokePress = fixMacKeyStroke(myStrokePress, map, code, false, "press");
      myStrokeRelease = fixMacKeyStroke(myStrokeRelease, map, code, true, "release");
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
    @Nullable Component getFocusableComponent() {
      return myComponent.getLabelFor();
    }
  }
}

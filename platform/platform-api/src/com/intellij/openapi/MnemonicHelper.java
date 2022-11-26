// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi;

import com.intellij.openapi.actionSystem.ActionButtonComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.ComponentTreeWatcher;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.DialogUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.InputEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Automatically locates &amp; characters in texts of buttons and labels on a component or dialog,
 * registers the mnemonics for those characters and removes them from the control text.
 *
 * @author lesya
 */
public final class MnemonicHelper extends ComponentTreeWatcher {
  private static final Logger LOG = Logger.getInstance(MnemonicHelper.class);

  public static final Key<Boolean> DISABLE_MNEMONIC_PROCESSING = Key.create("Disable mnemonic processing in the button or the label");
  public static final Key<IntPredicate> MNEMONIC_CHECKER = Key.create("MNEMONIC_CHECKER");

  private static final String TEXT_CHANGED_PROPERTY = "text";

  private static final PropertyChangeListener ourTextPropertyListener = event -> {
    Object source = event.getSource();
    // SwingUtilities.invokeLater is needed to process this event,
    // because the method is invoked from the setText method
    // before Swing updates mnemonics
    if (source instanceof AbstractButton) {
      //noinspection SSBasedInspection //see javax.swing.AbstractButton.setText
      SwingUtilities.invokeLater(() -> DialogUtil.registerMnemonic((AbstractButton)source));
    }
    else if (source instanceof JLabel) {
      //noinspection SSBasedInspection //see javax.swing.JLabel.setText
      SwingUtilities.invokeLater(() -> DialogUtil.registerMnemonic((JLabel)source, null));
    }
  };

  private Map<Integer, String> myMnemonics;

  private MnemonicHelper() {
    super(ArrayUtil.EMPTY_CLASS_ARRAY);
  }

  @Override
  protected void processComponent(Component component) {
    if (component instanceof AbstractButton) {
      component.addPropertyChangeListener(AbstractButton.TEXT_CHANGED_PROPERTY, ourTextPropertyListener);
      DialogUtil.registerMnemonic((AbstractButton)component);
      checkForDuplicateMnemonics((AbstractButton)component);
      fixMacMnemonicKeyStroke((JComponent)component, null);
    }
    else if (component instanceof JLabel) {
      component.addPropertyChangeListener(TEXT_CHANGED_PROPERTY, ourTextPropertyListener);
      DialogUtil.registerMnemonic((JLabel)component, null);
      checkForDuplicateMnemonics((JLabel)component);
      fixMacMnemonicKeyStroke((JComponent)component, "release"); // "release" only is OK for labels
    }
    else if (component instanceof ActionButtonComponent) {
      fixMacMnemonicKeyStroke((JComponent)component, null);
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
          int mask = InputEvent.ALT_MASK | InputEvent.CTRL_MASK;
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

  @Override
  protected void unprocessComponent(Component component) {
  }

  public void checkForDuplicateMnemonics(JLabel label) {
    if (!isCheckingDuplicateMnemonicsEnabled()) {
      return;
    }
    checkForDuplicateMnemonics(label.getDisplayedMnemonic(), label.getText());
  }

  public void checkForDuplicateMnemonics(AbstractButton button) {
    if (!isCheckingDuplicateMnemonicsEnabled()) {
      return;
    }
    checkForDuplicateMnemonics(button.getMnemonic(), button.getText());
  }

  public void checkForDuplicateMnemonics(int mnemonic, String text) {
    if (mnemonic == 0) return;
    if (myMnemonics == null) myMnemonics = new HashMap<>();
    final String other = myMnemonics.get(mnemonic);
    if (other != null && !other.equals(text)) {
      LOG.error("conflict: multiple components with mnemonic '" + (char)mnemonic + "' seen on '" + text + "' and '" + other + "'");
    }
    myMnemonics.put(mnemonic, text);
  }

  /**
   * Initializes mnemonics support for the specified component and for its children if needed.
   *
   * @param component the root component of the hierarchy
   */
  public static void init(Component component) {
    if (isCheckingDuplicateMnemonicsEnabled()) {
      new MnemonicHelper().register(component);
    }
    else {
      ourMnemonicFixer.addTo(component);
    }
  }

  private static boolean isCheckingDuplicateMnemonicsEnabled() {
    return Boolean.getBoolean("ide.checkDuplicateMnemonics");
  }

  public static boolean hasMnemonic(@Nullable Component component, int keyCode) {
    if (component instanceof AbstractButton) {
      return ((AbstractButton)component).getMnemonic() == keyCode;
    }
    if (component instanceof JLabel) {
      return ((JLabel)component).getDisplayedMnemonic() == keyCode;
    }
    IntPredicate checker = ClientProperty.get(component, MNEMONIC_CHECKER);
    return checker != null && checker.test(keyCode);
  }

  private static final MnemonicFixer ourMnemonicFixer = new MnemonicFixer();

  private static final class MnemonicFixer implements ContainerListener {
    void addTo(Component component) {
      for (Component c : UIUtil.uiTraverser(component)) {
        if (c instanceof Container) {
          ((Container)c).addContainerListener(this);
        }
        if (c instanceof ActionButtonComponent) {
          assert c instanceof JComponent;
          fixMacMnemonicKeyStroke((JComponent)c, null);
        }
        MnemonicWrapper.getWrapper(c);
      }
    }

    void removeFrom(Component component) {
      for (Container c : UIUtil.uiTraverser(component).filter(Container.class)) {
        c.removeContainerListener(this);
      }
    }

    @Override
    public void componentAdded(ContainerEvent event) {
      addTo(event.getChild());
    }

    @Override
    public void componentRemoved(ContainerEvent event) {
      removeFrom(event.getChild());
    }
  }

  @MagicConstant(flagsFromClass = InputEvent.class)
  public static int getFocusAcceleratorKeyMask() {
    //noinspection MagicConstant
    return SystemInfo.isMac ? ActionEvent.ALT_MASK | ActionEvent.CTRL_MASK : ActionEvent.ALT_MASK;
  }

  public static void registerMnemonicAction(@NotNull JComponent component, int mnemonic) {
    InputMap map = component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    int mask = getFocusAcceleratorKeyMask();
    if (component instanceof AbstractButton) {
      map.put(KeyStroke.getKeyStroke(mnemonic, mask, false), "pressed");
      map.put(KeyStroke.getKeyStroke(mnemonic, mask, true), "released");
      map.put(KeyStroke.getKeyStroke(mnemonic, 0, true), "released");
    }
    else if (component instanceof JLabel) {
      map.put(KeyStroke.getKeyStroke(mnemonic, mask, true), "released");
    }
    else if (component instanceof ActionButtonComponent) {
      map.put(KeyStroke.getKeyStroke(mnemonic, mask, false), "doClick");
    }
  }
}

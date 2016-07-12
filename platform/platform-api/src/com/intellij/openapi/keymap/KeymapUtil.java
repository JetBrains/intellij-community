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
package com.intellij.openapi.keymap;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

public class KeymapUtil {

  @NonNls private static final String APPLE_LAF_AQUA_LOOK_AND_FEEL_CLASS_NAME = "apple.laf.AquaLookAndFeel";
  @NonNls private static final String GET_KEY_MODIFIERS_TEXT_METHOD = "getKeyModifiersText";
  @NonNls private static final String CANCEL_KEY_TEXT = "Cancel";
  @NonNls private static final String BREAK_KEY_TEXT = "Break";
  @NonNls private static final String SHIFT = "shift";
  @NonNls private static final String CONTROL = "control";
  @NonNls private static final String CTRL = "ctrl";
  @NonNls private static final String META = "meta";
  @NonNls private static final String ALT = "alt";
  @NonNls private static final String ALT_GRAPH = "altGraph";
  @NonNls private static final String DOUBLE_CLICK = "doubleClick";

  private static final Set<Integer> ourTooltipKeys = new HashSet<Integer>();
  private static final Set<Integer> ourOtherTooltipKeys = new HashSet<Integer>();
  private static RegistryValue ourTooltipKeysProperty;

  private KeymapUtil() {
  }

  public static String getShortcutText(@NotNull Shortcut shortcut) {
    String s = "";

    if (shortcut instanceof KeyboardShortcut) {
      KeyboardShortcut keyboardShortcut = (KeyboardShortcut)shortcut;

      String acceleratorText = getKeystrokeText(keyboardShortcut.getFirstKeyStroke());
      if (!acceleratorText.isEmpty()) {
        s = acceleratorText;
      }

      acceleratorText = getKeystrokeText(keyboardShortcut.getSecondKeyStroke());
      if (!acceleratorText.isEmpty()) {
        s += ", " + acceleratorText;
      }
    }
    else if (shortcut instanceof MouseShortcut) {
      s = getMouseShortcutText((MouseShortcut)shortcut);
    }
    else if (shortcut instanceof KeyboardModifierGestureShortcut) {
      final KeyboardModifierGestureShortcut gestureShortcut = (KeyboardModifierGestureShortcut)shortcut;
      s = gestureShortcut.getType() == KeyboardGestureAction.ModifierType.dblClick ? "Press, release and hold " : "Hold ";
      s += getKeystrokeText(gestureShortcut.getStroke());
    }
    else {
      throw new IllegalArgumentException("unknown shortcut class: " + shortcut.getClass().getCanonicalName());
    }
    return s;
  }

  public static Icon getShortcutIcon(Shortcut shortcut) {
    if (shortcut instanceof KeyboardShortcut) {
      return AllIcons.General.KeyboardShortcut;
    }
    else if (shortcut instanceof MouseShortcut) {
      return AllIcons.General.MouseShortcut;
    }
    else {
      throw new IllegalArgumentException("unknown shortcut class: " + shortcut);
    }
  }

  public static String getMouseShortcutText(@NotNull MouseShortcut shortcut) {
    if (shortcut instanceof PressureShortcut) return shortcut.toString();
    return getMouseShortcutText(shortcut.getButton(), shortcut.getModifiers(), shortcut.getClickCount());
  }

  /**
   * @param button        target mouse button
   * @param modifiers     modifiers used within the target click
   * @param clickCount    target clicks count
   * @return string representation of passed mouse shortcut.
   */
  public static String getMouseShortcutText(int button, @JdkConstants.InputEventMask int modifiers, int clickCount) {
    String resource;
    if (button == MouseShortcut.BUTTON_WHEEL_UP) {
      resource = "mouse.wheel.rotate.up.shortcut.text";
    }
    else if (button == MouseShortcut.BUTTON_WHEEL_DOWN) {
      resource = "mouse.wheel.rotate.down.shortcut.text";
    }
    else if (clickCount < 2) {
      resource = "mouse.click.shortcut.text";
    }
    else if (clickCount < 3) {
      resource = "mouse.double.click.shortcut.text";
    } else {
      throw new IllegalStateException("unknown clickCount: " + clickCount);
    }
    return KeyMapBundle.message(resource, getModifiersText(mapNewModifiers(modifiers)), button);
  }

  @JdkConstants.InputEventMask
  private static int mapNewModifiers(@JdkConstants.InputEventMask int modifiers) {
    if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
      modifiers |= InputEvent.SHIFT_MASK;
    }
    if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) {
      modifiers |= InputEvent.ALT_MASK;
    }
    if ((modifiers & InputEvent.ALT_GRAPH_DOWN_MASK) != 0) {
      modifiers |= InputEvent.ALT_GRAPH_MASK;
    }
    if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
      modifiers |= InputEvent.CTRL_MASK;
    }
    if ((modifiers & InputEvent.META_DOWN_MASK) != 0) {
      modifiers |= InputEvent.META_MASK;
    }

    return modifiers;
  }

  public static String getKeystrokeText(KeyStroke accelerator) {
    if (accelerator == null) return "";
    if (SystemInfo.isMac) {
      return MacKeymapUtil.getKeyStrokeText(accelerator);
    }
    String acceleratorText = "";
    int modifiers = accelerator.getModifiers();
    if (modifiers > 0) {
      acceleratorText = getModifiersText(modifiers);
    }

    final int code = accelerator.getKeyCode();
    String keyText = SystemInfo.isMac ? MacKeymapUtil.getKeyText(code) : KeyEvent.getKeyText(code);
    // [vova] this is dirty fix for bug #35092
    if(CANCEL_KEY_TEXT.equals(keyText)){
      keyText = BREAK_KEY_TEXT;
    }

    acceleratorText += keyText;
    return acceleratorText.trim();
  }

  private static String getModifiersText(@JdkConstants.InputEventMask int modifiers) {
    if (SystemInfo.isMac) {
      //try {
      //  Class appleLaf = Class.forName(APPLE_LAF_AQUA_LOOK_AND_FEEL_CLASS_NAME);
      //  Method getModifiers = appleLaf.getMethod(GET_KEY_MODIFIERS_TEXT_METHOD, int.class, boolean.class);
      //  return (String)getModifiers.invoke(appleLaf, modifiers, Boolean.FALSE);
      //}
      //catch (Exception e) {
      //  if (SystemInfo.isMacOSLeopard) {
      //    return getKeyModifiersTextForMacOSLeopard(modifiers);
      //  }
      //
      //  // OK do nothing here.
      //}
      return MacKeymapUtil.getModifiersText(modifiers);
    }

    final String keyModifiersText = KeyEvent.getKeyModifiersText(modifiers);
    if (keyModifiersText.isEmpty()) {
      return keyModifiersText;
    }
    else {
      return keyModifiersText + "+";
    }
  }

  @NotNull
  public static String getFirstKeyboardShortcutText(@NotNull String actionId) {
    Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionId);
    KeyboardShortcut shortcut = ContainerUtil.findInstance(shortcuts, KeyboardShortcut.class);
    return shortcut == null? "" : getShortcutText(shortcut);
  }

  @NotNull
  public static String getFirstKeyboardShortcutText(@NotNull AnAction action) {
    Shortcut[] shortcuts = action.getShortcutSet().getShortcuts();
    KeyboardShortcut shortcut = ContainerUtil.findInstance(shortcuts, KeyboardShortcut.class);
    return shortcut == null ? "" : getShortcutText(shortcut);
  }

  @NotNull
  public static String getPreferredShortcutText(@NotNull Shortcut[] shortcuts) {
    KeyboardShortcut shortcut = ContainerUtil.findInstance(shortcuts, KeyboardShortcut.class);
    return shortcut != null ? getShortcutText(shortcut) :
           shortcuts.length > 0 ? getShortcutText(shortcuts[0]) : "";
  }

  public static String getShortcutsText(Shortcut[] shortcuts) {
    if (shortcuts.length == 0) {
      return "";
    }
    StringBuilder buffer = new StringBuilder();
    for (int i = 0; i < shortcuts.length; i++) {
      Shortcut shortcut = shortcuts[i];
      if (i > 0) {
        buffer.append(' ');
      }
      buffer.append(getShortcutText(shortcut));
    }
    return buffer.toString();
  }

  /**
   * Factory method. It parses passed string and creates <code>MouseShortcut</code>.
   *
   * @param keystrokeString       target keystroke
   * @return                      shortcut for the given keystroke
   * @throws InvalidDataException if <code>keystrokeString</code> doesn't represent valid <code>MouseShortcut</code>.
   */
  public static MouseShortcut parseMouseShortcut(String keystrokeString) throws InvalidDataException {

    if (Registry.is("ide.mac.forceTouch") && keystrokeString.startsWith("Force touch")) {
      return new PressureShortcut(2);
    }

    int button = -1;
    int modifiers = 0;
    int clickCount = 1;
    for (StringTokenizer tokenizer = new StringTokenizer(keystrokeString); tokenizer.hasMoreTokens();) {
      String token = tokenizer.nextToken();
      if (SHIFT.equals(token)) {
        modifiers |= InputEvent.SHIFT_DOWN_MASK;
      }
      else if (CONTROL.equals(token) || CTRL.equals(token)) {
        modifiers |= InputEvent.CTRL_DOWN_MASK;
      }
      else if (META.equals(token)) {
        modifiers |= InputEvent.META_DOWN_MASK;
      }
      else if (ALT.equals(token)) {
        modifiers |= InputEvent.ALT_DOWN_MASK;
      }
      else if (ALT_GRAPH.equals(token)) {
        modifiers |= InputEvent.ALT_GRAPH_DOWN_MASK;
      }
      else if (token.startsWith("button") && token.length() > 6) {
        try {
          button = Integer.parseInt(token.substring(6));
        }
        catch (NumberFormatException e) {
          throw new InvalidDataException("unparseable token: " + token);
        }
      }
      else if (DOUBLE_CLICK.equals(token)) {
        clickCount = 2;
      }
      else {
        throw new InvalidDataException("unknown token: " + token);
      }
    }
    return new MouseShortcut(button, modifiers, clickCount);
  }

  public static String getKeyModifiersTextForMacOSLeopard(@JdkConstants.InputEventMask int modifiers) {
    StringBuilder buf = new StringBuilder();
      if ((modifiers & InputEvent.META_MASK) != 0) {
          buf.append("\u2318");
      }
      if ((modifiers & InputEvent.CTRL_MASK) != 0) {
          buf.append(Toolkit.getProperty("AWT.control", "Ctrl"));
      }
      if ((modifiers & InputEvent.ALT_MASK) != 0) {
          buf.append("\2325");
      }
      if ((modifiers & InputEvent.SHIFT_MASK) != 0) {
          buf.append(Toolkit.getProperty("AWT.shift", "Shift"));
      }
      if ((modifiers & InputEvent.ALT_GRAPH_MASK) != 0) {
          buf.append(Toolkit.getProperty("AWT.altGraph", "Alt Graph"));
      }
      if ((modifiers & InputEvent.BUTTON1_MASK) != 0) {
          buf.append(Toolkit.getProperty("AWT.button1", "Button1"));
      }
      return buf.toString();
  }

  public static boolean isTooltipRequest(KeyEvent keyEvent) {
    if (ourTooltipKeysProperty == null) {
      ourTooltipKeysProperty = Registry.get("ide.forcedShowTooltip");
      ourTooltipKeysProperty.addListener(new RegistryValueListener.Adapter() {
        @Override
        public void afterValueChanged(RegistryValue value) {
          updateTooltipRequestKey(value);
        }
      }, Disposer.get("ui"));

      updateTooltipRequestKey(ourTooltipKeysProperty);
    }

    if (keyEvent.getID() != KeyEvent.KEY_PRESSED) return false;

    for (Integer each : ourTooltipKeys) {
      if ((keyEvent.getModifiers() & each.intValue()) == 0) return false;
    }

    for (Integer each : ourOtherTooltipKeys) {
      if ((keyEvent.getModifiers() & each.intValue()) > 0) return false;
    }

    final int code = keyEvent.getKeyCode();

    return code == KeyEvent.VK_META || code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_ALT;
  }

  private static void updateTooltipRequestKey(RegistryValue value) {
    final String text = value.asString();

    ourTooltipKeys.clear();
    ourOtherTooltipKeys.clear();

    processKey(text.contains("meta"), InputEvent.META_MASK);
    processKey(text.contains("control") || text.contains("ctrl"), InputEvent.CTRL_MASK);
    processKey(text.contains("shift"), InputEvent.SHIFT_MASK);
    processKey(text.contains("alt"), InputEvent.ALT_MASK);

  }

  private static void processKey(boolean condition, int value) {
    if (condition) {
      ourTooltipKeys.add(value);
    } else {
      ourOtherTooltipKeys.add(value);
    }
  }

  public static boolean isEmacsKeymap() {
    return isEmacsKeymap(KeymapManager.getInstance().getActiveKeymap());
  }

  public static boolean isEmacsKeymap(@Nullable Keymap keymap) {
    for (; keymap != null; keymap = keymap.getParent()) {
      if ("Emacs".equalsIgnoreCase(keymap.getName())) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static KeyStroke getKeyStroke(@NotNull final ShortcutSet shortcutSet) {
    final Shortcut[] shortcuts = shortcutSet.getShortcuts();
    if (shortcuts.length == 0 || !(shortcuts[0] instanceof KeyboardShortcut)) return null;
    final KeyboardShortcut shortcut = (KeyboardShortcut)shortcuts[0];
    if (shortcut.getSecondKeyStroke() != null) {
      return null;
    }
    return shortcut.getFirstKeyStroke();
  }

  @NotNull
  public static String createTooltipText(@Nullable String name, @NotNull AnAction action) {
    String toolTipText = name == null ? "" : name;
    while (StringUtil.endsWithChar(toolTipText, '.')) {
      toolTipText = toolTipText.substring(0, toolTipText.length() - 1);
    }
    String shortcutsText = getFirstKeyboardShortcutText(action);
    if (!shortcutsText.isEmpty()) {
      toolTipText += " (" + shortcutsText + ")";
    }
    return toolTipText;
  }

  /**
   * Checks that one of the mouse shortcuts assigned to the provided action has the same modifiers as provided
   */
  public static boolean matchActionMouseShortcutsModifiers(final Keymap activeKeymap,
                                                           @JdkConstants.InputEventMask int modifiers,
                                                           final String actionId) {
    final MouseShortcut syntheticShortcut = new MouseShortcut(MouseEvent.BUTTON1, modifiers, 1);
    for (Shortcut shortcut : activeKeymap.getShortcuts(actionId)) {
      if (shortcut instanceof MouseShortcut) {
        final MouseShortcut mouseShortcut = (MouseShortcut)shortcut;
        if (mouseShortcut.getModifiers() == syntheticShortcut.getModifiers()) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Checks whether mouse event's button and modifiers match a shortcut configured in active keymap for given action id.
   * Only shortcuts having click count of 1 can be matched, mouse event's click count is ignored.
   */
  public static boolean isMouseActionEvent(@NotNull MouseEvent e, @NotNull String actionId) {
    KeymapManager keymapManager = KeymapManager.getInstance();
    if (keymapManager == null) {
      return false;
    }
    Keymap keymap = keymapManager.getActiveKeymap();
    if (keymap == null) {
      return false;
    }
    int button = MouseShortcut.getButton(e);
    int modifiers = e.getModifiersEx();
    if (button == MouseEvent.NOBUTTON && e.getID() == MouseEvent.MOUSE_DRAGGED) {
      // mouse drag events don't have button field set due to some reason
      if ((modifiers & InputEvent.BUTTON1_DOWN_MASK) != 0) {
        button = MouseEvent.BUTTON1;
      } else if ((modifiers & InputEvent.BUTTON2_DOWN_MASK) != 0) {
        button = MouseEvent.BUTTON2;
      }
    }
    String[] actionIds = keymap.getActionIds(new MouseShortcut(button, modifiers, 1));
    if (actionIds == null) {
      return false;
    }
    for (String id : actionIds) {
      if (actionId.equals(id)) {
        return true;
      }
    }
    return false;
  }

  /**
   * @param component    target component to reassign previously mapped action (if any)
   * @param oldKeyStroke previously mapped keystroke (e.g. standard one that you want to use in some different way)
   * @param newKeyStroke new keystroke to be assigned. <code>null</code> value means 'just unregister previously mapped action'
   * @param condition    one of
   *                     <ul>
   *                     <li>JComponent.WHEN_FOCUSED,</li>
   *                     <li>JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT</li>
   *                     <li>JComponent.WHEN_IN_FOCUSED_WINDOW</li>
   *                     <li>JComponent.UNDEFINED_CONDITION</li>
   *                     </ul>
   * @return <code>true</code> if the action is reassigned successfully
   */
  public static boolean reassignAction(@NotNull JComponent component,
                                       @NotNull KeyStroke oldKeyStroke,
                                       @Nullable KeyStroke newKeyStroke,
                                       int condition) {
    ActionListener action = component.getActionForKeyStroke(oldKeyStroke);
    if (action == null) return false;
    if (newKeyStroke != null) {
      component.registerKeyboardAction(action, newKeyStroke, condition);
    }
    component.unregisterKeyboardAction(oldKeyStroke);
    return true;
  }
}

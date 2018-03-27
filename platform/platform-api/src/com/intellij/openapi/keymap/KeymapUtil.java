/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
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
import java.awt.event.*;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

public class KeymapUtil {

  @NonNls private static final String CANCEL_KEY_TEXT = "Cancel";
  @NonNls private static final String BREAK_KEY_TEXT = "Break";
  @NonNls private static final String SHIFT = "shift";
  @NonNls private static final String CONTROL = "control";
  @NonNls private static final String CTRL = "ctrl";
  @NonNls private static final String META = "meta";
  @NonNls private static final String ALT = "alt";
  @NonNls private static final String ALT_GRAPH = "altGraph";
  @NonNls private static final String DOUBLE_CLICK = "doubleClick";

  private static final Set<Integer> ourTooltipKeys = new HashSet<>();
  private static final Set<Integer> ourOtherTooltipKeys = new HashSet<>();
  private static RegistryValue ourTooltipKeysProperty;

  private KeymapUtil() {
  }

  @NotNull
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

  @NotNull
  public static Icon getShortcutIcon(@NotNull Shortcut shortcut) {
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

  @NotNull
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
  @NotNull
  private static String getMouseShortcutText(int button, @JdkConstants.InputEventMask int modifiers, int clickCount) {
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
    }
    else {
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

  @NotNull
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

    acceleratorText += getKeyText(accelerator.getKeyCode());
    return acceleratorText.trim();
  }

  @NotNull
  public static String getKeyText(int code) {
    switch (code) {
      case KeyEvent.VK_BACK_QUOTE:     return "`";
      case KeyEvent.VK_SEPARATOR:      return ",";
      case KeyEvent.VK_DECIMAL:        return ".";
      case KeyEvent.VK_SLASH:          return "/";
      case KeyEvent.VK_BACK_SLASH:     return "\\";
      case KeyEvent.VK_PERIOD:         return ".";
      case KeyEvent.VK_SEMICOLON:      return ";";
      case KeyEvent.VK_CLOSE_BRACKET:  return "]";
      case KeyEvent.VK_OPEN_BRACKET:   return "[";
      case KeyEvent.VK_EQUALS:         return "=";
    }

    String result = SystemInfo.isMac ? MacKeymapUtil.getKeyText(code) : KeyEvent.getKeyText(code);
    // [vova] this is dirty fix for bug #35092
    return CANCEL_KEY_TEXT.equals(result) ? BREAK_KEY_TEXT : result;
  }

  @NotNull
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
    return keyModifiersText.isEmpty() ? keyModifiersText : keyModifiersText + "+";
  }

  @NotNull
  public static ShortcutSet getActiveKeymapShortcuts(@Nullable String actionId) {
    Application application = ApplicationManager.getApplication();
    KeymapManager keymapManager = application == null ? null : application.getComponent(KeymapManager.class);
    if (keymapManager == null || actionId == null) {
      return new CustomShortcutSet(Shortcut.EMPTY_ARRAY);
    }
    return new CustomShortcutSet(keymapManager.getActiveKeymap().getShortcuts(actionId));
  }

  @NotNull
  public static String getFirstKeyboardShortcutText(@NotNull String actionId) {
    Shortcut[] shortcuts = getActiveKeymapShortcuts(actionId).getShortcuts();
    KeyboardShortcut shortcut = ContainerUtil.findInstance(shortcuts, KeyboardShortcut.class);
    return shortcut == null? "" : getShortcutText(shortcut);
  }

  @NotNull
  public static String getFirstKeyboardShortcutText(@NotNull AnAction action) {
    return getFirstKeyboardShortcutText(action.getShortcutSet());
  }

  @NotNull
  public static String getFirstKeyboardShortcutText(@NotNull ShortcutSet set) {
    Shortcut[] shortcuts = set.getShortcuts();
    KeyboardShortcut shortcut = ContainerUtil.findInstance(shortcuts, KeyboardShortcut.class);
    return shortcut == null ? "" : getShortcutText(shortcut);
  }

  @NotNull
  public static String getPreferredShortcutText(@NotNull Shortcut[] shortcuts) {
    KeyboardShortcut shortcut = ContainerUtil.findInstance(shortcuts, KeyboardShortcut.class);
    return shortcut != null ? getShortcutText(shortcut) :
           shortcuts.length > 0 ? getShortcutText(shortcuts[0]) : "";
  }

  @NotNull
  public static String getShortcutsText(@NotNull Shortcut[] shortcuts) {
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
   * Factory method. It parses passed string and creates {@code MouseShortcut}.
   *
   * @param keystrokeString       target keystroke
   * @return                      shortcut for the given keystroke
   * @throws InvalidDataException if {@code keystrokeString} doesn't represent valid {@code MouseShortcut}.
   */
  @NotNull
  public static MouseShortcut parseMouseShortcut(@NotNull String keystrokeString) throws InvalidDataException {
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

  /**
   * @return string representation of passed mouse shortcut. This method should
   *         be used only for serializing of the {@code MouseShortcut}
   */
  @NotNull
  public static String getMouseShortcutString(@NotNull MouseShortcut shortcut) {
    if (Registry.is("ide.mac.forceTouch") && shortcut instanceof PressureShortcut) {
      return "Force touch";
    }

    StringBuilder buffer = new StringBuilder();

    // modifiers
    int modifiers = shortcut.getModifiers();
    if ((InputEvent.SHIFT_DOWN_MASK & modifiers) > 0) {
      buffer.append(SHIFT);
      buffer.append(' ');
    }
    if ((InputEvent.CTRL_DOWN_MASK & modifiers) > 0) {
      buffer.append(CONTROL);
      buffer.append(' ');
    }
    if ((InputEvent.META_DOWN_MASK & modifiers) > 0) {
      buffer.append(META);
      buffer.append(' ');
    }
    if ((InputEvent.ALT_DOWN_MASK & modifiers) > 0) {
      buffer.append(ALT);
      buffer.append(' ');
    }
    if ((InputEvent.ALT_GRAPH_DOWN_MASK & modifiers) > 0) {
      buffer.append(ALT_GRAPH);
      buffer.append(' ');
    }

    // button
    buffer.append("button").append(shortcut.getButton()).append(' ');

    if (shortcut.getClickCount() > 1) {
      buffer.append(DOUBLE_CLICK);
    }
    return buffer.toString().trim(); // trim trailing space (if any)
  }

  @NotNull
  public static String getKeyModifiersTextForMacOSLeopard(@JdkConstants.InputEventMask int modifiers) {
    StringBuilder buf = new StringBuilder();
    if ((modifiers & InputEvent.META_MASK) != 0) {
      buf.append("\u2318");
    }
    if ((modifiers & InputEvent.CTRL_MASK) != 0) {
      buf.append(Toolkit.getProperty("AWT.control", "Ctrl"));
    }
    if ((modifiers & InputEvent.ALT_MASK) != 0) {
      buf.append("\u2325");
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

  public static boolean isTooltipRequest(@NotNull KeyEvent keyEvent) {
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

  private static void updateTooltipRequestKey(@NotNull RegistryValue value) {
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
  public static String createTooltipText(@NotNull String name, @NotNull String actionId) {
    String text = getFirstKeyboardShortcutText(actionId);
    return text.isEmpty() ? name : name + " (" + text + ")";
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
  public static boolean matchActionMouseShortcutsModifiers(@NotNull Keymap activeKeymap,
                                                           @JdkConstants.InputEventMask int modifiers,
                                                           @NotNull String actionId) {
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
   * Creates shortcut corresponding to a single-click event
   */
  @NotNull
  public static MouseShortcut createMouseShortcut(@NotNull MouseEvent e) {
    int button = MouseShortcut.getButton(e);
    int modifiers = e.getModifiersEx();
    if (button == MouseEvent.NOBUTTON && e.getID() == MouseEvent.MOUSE_DRAGGED) {
      // mouse drag events don't have button field set due to some reason
      if ((modifiers & InputEvent.BUTTON1_DOWN_MASK) != 0) {
        button = MouseEvent.BUTTON1;
      }
      else if ((modifiers & InputEvent.BUTTON2_DOWN_MASK) != 0) {
        button = MouseEvent.BUTTON2;
      }
    }
    return new MouseShortcut(button, modifiers, 1);
  }

  /**
   * @param component    target component to reassign previously mapped action (if any)
   * @param oldKeyStroke previously mapped keystroke (e.g. standard one that you want to use in some different way)
   * @param newKeyStroke new keystroke to be assigned. {@code null} value means 'just unregister previously mapped action'
   * @param condition    one of
   *                     <ul>
   *                     <li>JComponent.WHEN_FOCUSED,</li>
   *                     <li>JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT</li>
   *                     <li>JComponent.WHEN_IN_FOCUSED_WINDOW</li>
   *                     <li>JComponent.UNDEFINED_CONDITION</li>
   *                     </ul>
   * @return {@code true} if the action is reassigned successfully
   */
  public static boolean reassignAction(@NotNull JComponent component,
                                       @NotNull KeyStroke oldKeyStroke,
                                       @Nullable KeyStroke newKeyStroke,
                                       int condition) {
    return reassignAction(component, oldKeyStroke, newKeyStroke, condition, true);
  }
  /**
   * @param component    target component to reassign previously mapped action (if any)
   * @param oldKeyStroke previously mapped keystroke (e.g. standard one that you want to use in some different way)
   * @param newKeyStroke new keystroke to be assigned. {@code null} value means 'just unregister previously mapped action'
   * @param condition    one of
   *                     <ul>
   *                     <li>JComponent.WHEN_FOCUSED,</li>
   *                     <li>JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT</li>
   *                     <li>JComponent.WHEN_IN_FOCUSED_WINDOW</li>
   *                     <li>JComponent.UNDEFINED_CONDITION</li>
   *                     </ul>
   * @param muteOldKeystroke if {@code true} old keystroke wouldn't work anymore
   * @return {@code true} if the action is reassigned successfully
   */
  public static boolean reassignAction(@NotNull JComponent component,
                                       @NotNull KeyStroke oldKeyStroke,
                                       @Nullable KeyStroke newKeyStroke,
                                       int condition, boolean muteOldKeystroke) {
    ActionListener action = component.getActionForKeyStroke(oldKeyStroke);
    if (action == null) return false;
    if (newKeyStroke != null) {
      component.registerKeyboardAction(action, newKeyStroke, condition);
    }
    if (muteOldKeystroke) {
      component.registerKeyboardAction(new RedispatchEventAction(component), oldKeyStroke, condition);
    }
    return true;
  }

  private static final class RedispatchEventAction extends AbstractAction {
    private final Component myComponent;

    RedispatchEventAction(@NotNull Component component) {
      myComponent = component;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      AWTEvent event = EventQueue.getCurrentEvent();
      if (event instanceof KeyEvent && event.getSource() == myComponent) {
        Container parent = myComponent.getParent();
        if (parent != null) {
          KeyEvent keyEvent = (KeyEvent)event;
          parent.dispatchEvent(new KeyEvent(parent, event.getID(), ((KeyEvent)event).getWhen(), keyEvent.getModifiers(), keyEvent.getKeyCode(), keyEvent.getKeyChar(), keyEvent
            .getKeyLocation()));
        }
      }
    }
  }
}

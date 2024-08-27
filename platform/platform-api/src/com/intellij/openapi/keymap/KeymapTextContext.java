// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.client.ClientSystemInfo;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NlsSafe;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.StringTokenizer;

public class KeymapTextContext {
  private static final @NonNls String CANCEL_KEY_TEXT = "Cancel";
  private static final @NonNls String BREAK_KEY_TEXT = "Break";
  private static final @NonNls String SHIFT = "shift";
  private static final @NonNls String CONTROL = "control";
  private static final @NonNls String CTRL = "ctrl";
  private static final @NonNls String META = "meta";
  private static final @NonNls String ALT = "alt";
  private static final @NonNls String ALT_GRAPH = "altGraph";
  private static final @NonNls String DOUBLE_CLICK = "doubleClick";

  public @Nls @NotNull String getShortcutText(@NotNull @NonNls String actionId) {
    KeyboardShortcut shortcut = ActionManager.getInstance().getKeyboardShortcut(actionId);
    if (shortcut == null) return KeyMapBundle.message("no.shortcut");
    return getShortcutText(shortcut);
  }

  public @Nls @NotNull String getShortcutText(@NotNull Shortcut shortcut) {
    String s = "";

    if (shortcut instanceof KeyboardShortcut keyboardShortcut) {

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
    else if (shortcut instanceof KeyboardModifierGestureShortcut gestureShortcut) {
      s = gestureShortcut.getType() == KeyboardGestureAction.ModifierType.dblClick ? KeyMapBundle.message("press.release.and.hold")
                                                                                   : KeyMapBundle.message("hold");
      s += " " + getKeystrokeText(gestureShortcut.getStroke());
    }
    else {
      throw new IllegalArgumentException("unknown shortcut class: " + shortcut.getClass().getCanonicalName());
    }
    return s;
  }

  public @Nls @NotNull String getMouseShortcutText(@NotNull MouseShortcut shortcut) {
    if (shortcut instanceof PressureShortcut) return shortcut.toString();  //NON-NLS
    return getMouseShortcutText(shortcut.getButton(), shortcut.getModifiers(), shortcut.getClickCount());
  }

  /**
   * @param button        target mouse button
   * @param modifiers     modifiers used within the target click
   * @param clickCount    target clicks count
   * @return string representation of passed mouse shortcut.
   */
  private @Nls @NotNull String getMouseShortcutText(int button, @JdkConstants.InputEventMask int modifiers, int clickCount) {
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
    return KeyMapBundle.message(resource, getModifiersText(mapNewModifiers(modifiers), true), button);
  }

  @JdkConstants.InputEventMask
  static int mapNewModifiers(@JdkConstants.InputEventMask int modifiers) {
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

  public @NotNull @NlsSafe String getKeystrokeText(KeyStroke accelerator) {
    if (accelerator == null) return "";
    if (isNativeMacShortcuts()) {
      return MacKeymapUtil.getKeyStrokeText(accelerator);
    }
    String acceleratorText = "";
    int modifiers = accelerator.getModifiers();
    if (modifiers > 0) {
      acceleratorText = getModifiersText(modifiers, true);
    }

    int code = accelerator.getKeyCode();
    acceleratorText += isSimplifiedMacShortcuts() ? getSimplifiedMacKeyText(code) : getKeyText(code);
    return acceleratorText.trim();
  }

  public @NotNull String getKeyText(int code) {
    return switch (code) {
      case KeyEvent.VK_BACK_QUOTE    -> "`";
      case KeyEvent.VK_SEPARATOR     -> ",";
      case KeyEvent.VK_DECIMAL       -> ".";
      case KeyEvent.VK_SLASH         -> "/";
      case KeyEvent.VK_BACK_SLASH    -> "\\";
      case KeyEvent.VK_PERIOD        -> ".";
      case KeyEvent.VK_SEMICOLON     -> ";";
      case KeyEvent.VK_CLOSE_BRACKET -> "]";
      case KeyEvent.VK_OPEN_BRACKET  -> "[";
      case KeyEvent.VK_EQUALS        -> "=";
      default -> {
        String result = isNativeMacShortcuts() ? MacKeymapUtil.getKeyText(code) : KeyEvent.getKeyText(code);
        // [vova] this is dirty fix for bug #35092
        yield CANCEL_KEY_TEXT.equals(result) ? BREAK_KEY_TEXT : result;
      }
    };
  }

  private boolean isNativeMacShortcuts() {
    return ClientSystemInfo.isMac() && !isSimplifiedMacShortcuts();
  }

  public boolean isSimplifiedMacShortcuts() {
    return ClientSystemInfo.isMac() && AdvancedSettings.getInstanceIfCreated() != null && AdvancedSettings.getBoolean("ide.macos.disable.native.shortcut.symbols");
  }

  @NotNull String getModifiersText(@JdkConstants.InputEventMask int modifiers, boolean addPlus) {
    if (isNativeMacShortcuts()) {
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

    final String keyModifiersText = isSimplifiedMacShortcuts() ? getSimplifiedMacKeyModifiersText(modifiers)
                                                               : KeyEvent.getKeyModifiersText(modifiers);

    return !keyModifiersText.isEmpty()  && addPlus ? keyModifiersText + "+" : keyModifiersText;
  }

  private static String getSimplifiedMacKeyModifiersText(int modifiers) {
    StringBuilder buf = new StringBuilder();

    if ((modifiers & InputEvent.META_MASK) != 0)      buf.append("Cmd+");
    if ((modifiers & InputEvent.CTRL_MASK) != 0)      buf.append("Ctrl+");
    if ((modifiers & InputEvent.ALT_MASK) != 0)       buf.append("Alt+");
    if ((modifiers & InputEvent.SHIFT_MASK) != 0)     buf.append("Shift+");
    if ((modifiers & InputEvent.ALT_GRAPH_MASK) != 0) buf.append("Alt Graph+");
    if ((modifiers & InputEvent.BUTTON1_MASK) != 0)   buf.append("Button1+");

    if (buf.length() > 0) buf.setLength(buf.length() - 1);

    return buf.toString();
  }

  private String getSimplifiedMacKeyText(int code) {
    return switch (code) {
      case KeyEvent.VK_ENTER -> "Enter";
      case KeyEvent.VK_BACK_SPACE -> "Backspace";
      case KeyEvent.VK_TAB -> "Tab";
      case KeyEvent.VK_CANCEL -> "Cancel";
      case KeyEvent.VK_CLEAR -> "Clear";
      case KeyEvent.VK_COMPOSE -> "Compose";
      case KeyEvent.VK_PAUSE -> "Pause";
      case KeyEvent.VK_CAPS_LOCK -> "Caps Lock";
      case KeyEvent.VK_ESCAPE -> "Escape";
      case KeyEvent.VK_SPACE -> "Space";
      case KeyEvent.VK_PAGE_UP -> "Page Up";
      case KeyEvent.VK_PAGE_DOWN -> "Page Down";
      case KeyEvent.VK_END -> "End";
      case KeyEvent.VK_HOME -> "Home";
      case KeyEvent.VK_LEFT -> "Left";
      case KeyEvent.VK_UP -> "Up";
      case KeyEvent.VK_RIGHT -> "Right";
      case KeyEvent.VK_DOWN -> "Down";
      case KeyEvent.VK_BEGIN -> "Begin";

      // modifiers
      case KeyEvent.VK_SHIFT -> "Shift";
      case KeyEvent.VK_CONTROL -> "Control";
      case KeyEvent.VK_ALT -> "Alt";
      case KeyEvent.VK_META -> "Meta";
      case KeyEvent.VK_ALT_GRAPH -> "Alt Graph";

      // numpad numeric keys handled below
      case KeyEvent.VK_MULTIPLY -> "NumPad *";
      case KeyEvent.VK_ADD -> "NumPad +";
      case KeyEvent.VK_SEPARATOR -> "NumPad ,";
      case KeyEvent.VK_SUBTRACT -> "NumPad -";
      case KeyEvent.VK_DECIMAL -> "NumPad .";
      case KeyEvent.VK_DIVIDE -> "NumPad /";
      case KeyEvent.VK_DELETE -> "Delete";
      case KeyEvent.VK_NUM_LOCK -> "Num Lock";
      case KeyEvent.VK_SCROLL_LOCK -> "Scroll Lock";
      case KeyEvent.VK_WINDOWS -> "Windows";
      case KeyEvent.VK_CONTEXT_MENU -> "Context Menu";
      case KeyEvent.VK_F1 -> "F1";
      case KeyEvent.VK_F2 -> "F2";
      case KeyEvent.VK_F3 -> "F3";
      case KeyEvent.VK_F4 -> "F4";
      case KeyEvent.VK_F5 -> "F5";
      case KeyEvent.VK_F6 -> "F6";
      case KeyEvent.VK_F7 -> "F7";
      case KeyEvent.VK_F8 -> "F8";
      case KeyEvent.VK_F9 -> "F9";
      case KeyEvent.VK_F10 -> "F10";
      case KeyEvent.VK_F11 -> "F11";
      case KeyEvent.VK_F12 -> "F12";
      case KeyEvent.VK_F13 -> "F13";
      case KeyEvent.VK_F14 -> "F14";
      case KeyEvent.VK_F15 -> "F15";
      case KeyEvent.VK_F16 -> "F16";
      case KeyEvent.VK_F17 -> "F17";
      case KeyEvent.VK_F18 -> "F18";
      case KeyEvent.VK_F19 -> "F19";
      case KeyEvent.VK_F20 -> "F20";
      case KeyEvent.VK_F21 -> "F21";
      case KeyEvent.VK_F22 -> "F22";
      case KeyEvent.VK_F23 -> "F23";
      case KeyEvent.VK_F24 -> "F24";
      case KeyEvent.VK_PRINTSCREEN -> "Print Screen";
      case KeyEvent.VK_INSERT -> "Insert";
      case KeyEvent.VK_HELP -> "Help";
      case KeyEvent.VK_KP_UP -> "Up";
      case KeyEvent.VK_KP_DOWN -> "Down";
      case KeyEvent.VK_KP_LEFT -> "Left";
      case KeyEvent.VK_KP_RIGHT -> "Right";
      default -> getKeyText(code);
    };
  }

  /**
   * Factory method. It parses passed string and creates {@code MouseShortcut}.
   *
   * @param keystrokeString       target keystroke
   * @return                      shortcut for the given keystroke
   * @throws InvalidDataException if {@code keystrokeString} doesn't represent valid {@code MouseShortcut}.
   */
  public @NotNull MouseShortcut parseMouseShortcut(@NotNull String keystrokeString) throws InvalidDataException {
    if (keystrokeString.startsWith("Force touch")) {
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
          throw new InvalidDataException("unparsable token: " + token);
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
  public @NotNull String getMouseShortcutString(@NotNull MouseShortcut shortcut) {
    if (shortcut instanceof PressureShortcut) {
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
}

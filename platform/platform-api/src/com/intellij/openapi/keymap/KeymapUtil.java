// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static java.awt.event.InputEvent.ALT_DOWN_MASK;
import static java.awt.event.InputEvent.CTRL_DOWN_MASK;

public final class KeymapUtil {
  private static final KeymapTextContext ourDefaultKeymapTextContext = new KeymapTextContext();

  private static final Set<Integer> ourTooltipKeys = new HashSet<>();
  private static final Set<Integer> ourOtherTooltipKeys = new HashSet<>();
  private static RegistryValue ourTooltipKeysProperty;

  private KeymapUtil() {
  }

  public static @NlsSafe @NotNull String getShortcutText(@NotNull @NonNls String actionId) {
    return ourDefaultKeymapTextContext.getShortcutText(actionId);
  }

  public static @NlsSafe @Nullable String getShortcutTextOrNull(@NotNull @NonNls String actionId) {
    KeyboardShortcut shortcut = ActionManager.getInstance().getKeyboardShortcut(actionId);
    if (shortcut == null) return null;
    return getShortcutText(shortcut);
  }

  public static @NotNull @NlsSafe String getShortcutText(@NotNull Shortcut shortcut) {
    return ourDefaultKeymapTextContext.getShortcutText(shortcut);
  }

  public static @NotNull @NlsSafe String getMouseShortcutText(@NotNull MouseShortcut shortcut) {
    return ourDefaultKeymapTextContext.getMouseShortcutText(shortcut);
  }

  public static @NotNull @NlsSafe String getKeystrokeText(KeyStroke accelerator) {
    return ourDefaultKeymapTextContext.getKeystrokeText(accelerator);
  }

  public static @NotNull String getKeyText(int code) {
    return ourDefaultKeymapTextContext.getKeyText(code);
  }

  public static boolean isSimplifiedMacShortcuts() {
    return ourDefaultKeymapTextContext.isSimplifiedMacShortcuts();
  }

  public static @NotNull ShortcutSet getActiveKeymapShortcuts(@Nullable @NonNls String actionId) {
    KeymapManager keymapManager = actionId == null ? null : KeymapManager.getInstance();
    return keymapManager == null ? new CustomShortcutSet(Shortcut.EMPTY_ARRAY) : getActiveKeymapShortcuts(actionId, keymapManager);
  }

  @ApiStatus.Internal
  public static @NotNull ShortcutSet getActiveKeymapShortcuts(@NotNull @NonNls String actionId, @NotNull KeymapManager keymapManager) {
    return new CustomShortcutSet(keymapManager.getActiveKeymap().getShortcuts(actionId));
  }

  /**
   * @param actionId action to find the shortcut for
   * @return first keyboard shortcut that activates given action in active keymap; null if not found
   */
  public static @Nullable Shortcut getPrimaryShortcut(@Nullable @NonNls String actionId) {
    KeymapManager keymapManager = KeymapManager.getInstance();
    if (keymapManager == null || actionId == null) return null;
    return ArrayUtil.getFirstElement(keymapManager.getActiveKeymap().getShortcuts(actionId));
  }

  public static @NotNull @NlsSafe String getFirstKeyboardShortcutText(@NotNull @NonNls String actionId) {
    for (Shortcut shortcut : getActiveKeymapShortcuts(actionId).getShortcuts()) {
      if (shortcut instanceof KeyboardShortcut) {
        return getShortcutText(shortcut);
      }
    }
    return "";
  }

  public static @NotNull @NlsSafe String getFirstMouseShortcutText(@NotNull @NonNls String actionId) {
    for (Shortcut shortcut : getActiveKeymapShortcuts(actionId).getShortcuts()) {
      if (shortcut instanceof MouseShortcut) {
        return getShortcutText(shortcut);
      }
    }
    return "";
  }

  public static boolean isEventForAction(@NotNull KeyEvent keyEvent, @NotNull @NonNls String actionId) {
    for (Shortcut shortcut : getActiveKeymapShortcuts(actionId).getShortcuts()) {
      if (shortcut instanceof KeyboardShortcut && AWTKeyStroke.getAWTKeyStrokeForEvent(keyEvent) == ((KeyboardShortcut)shortcut).getFirstKeyStroke()) {
        return true;
      }
    }
    return false;
  }

  public static @NotNull @NlsSafe String getFirstKeyboardShortcutText(@NotNull AnAction action) {
    return getFirstKeyboardShortcutText(action.getShortcutSet());
  }

  public static @NotNull @NlsSafe String getFirstKeyboardShortcutText(@NotNull ShortcutSet set) {
    Shortcut[] shortcuts = set.getShortcuts();
    KeyboardShortcut shortcut = ContainerUtil.findInstance(shortcuts, KeyboardShortcut.class);
    return shortcut == null ? "" : getShortcutText(shortcut);
  }

  public static @NotNull @NlsSafe String getPreferredShortcutText(Shortcut @NotNull [] shortcuts) {
    KeyboardShortcut shortcut = ContainerUtil.findInstance(shortcuts, KeyboardShortcut.class);
    return shortcut != null ? getShortcutText(shortcut) :
           shortcuts.length > 0 ? getShortcutText(shortcuts[0]) : "";
  }

  public static @NotNull @NlsSafe String getShortcutsText(Shortcut @NotNull [] shortcuts) {
    if (shortcuts.length == 0) {
      return "";
    }
    return Arrays.stream(shortcuts).map(KeymapUtil::getShortcutText).collect(Collectors.joining(" "));
  }

  /**
   * Factory method. It parses passed string and creates {@code MouseShortcut}.
   *
   * @param keystrokeString       target keystroke
   * @return                      shortcut for the given keystroke
   * @throws InvalidDataException if {@code keystrokeString} doesn't represent valid {@code MouseShortcut}.
   */
  public static @NotNull MouseShortcut parseMouseShortcut(@NotNull String keystrokeString) throws InvalidDataException {
    return ourDefaultKeymapTextContext.parseMouseShortcut(keystrokeString);
  }

  /**
   * Similar to {@link KeyStroke#getKeyStroke(String)} but allows keys in lower case.
   * For example, "control x" is accepted and interpreted as "control X".
   */
  public static @Nullable KeyStroke getKeyStroke(@NotNull String s) {
    KeyStroke result = null;
    try {
      result = KeyStroke.getKeyStroke(s);
    }
    catch (Exception ex) {
      //ok
    }
    if (result == null && s.length() >= 2 && s.charAt(s.length() - 2) == ' ') {
      try {
        String s1 = s.substring(0, s.length() - 1) + Character.toUpperCase(s.charAt(s.length() - 1));
        result = KeyStroke.getKeyStroke(s1);
      }
      catch (Exception ignored) {
      }
    }
    return result;
  }

  /**
   * @return string representation of passed mouse shortcut. This method should
   *         be used only for serializing of the {@code MouseShortcut}
   */
  public static @NotNull String getMouseShortcutString(@NotNull MouseShortcut shortcut) {
    return ourDefaultKeymapTextContext.getMouseShortcutString(shortcut);
  }

  public static boolean isTooltipRequest(@NotNull KeyEvent keyEvent) {
    if (ourTooltipKeysProperty == null) {
      ourTooltipKeysProperty = Registry.get("ide.forcedShowTooltip");
      ourTooltipKeysProperty.addListener(new RegistryValueListener() {
        @Override
        public void afterValueChanged(@NotNull RegistryValue value) {
          updateTooltipRequestKey(value);
        }
      }, ApplicationManager.getApplication());

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

  public static @Nullable KeyStroke getKeyStroke(final @NotNull ShortcutSet shortcutSet) {
    final Shortcut[] shortcuts = shortcutSet.getShortcuts();
    if (shortcuts.length == 0 || !(shortcuts[0] instanceof KeyboardShortcut shortcut)) return null;
    if (shortcut.getSecondKeyStroke() != null) {
      return null;
    }
    return shortcut.getFirstKeyStroke();
  }

  public static @NotNull Collection<KeyStroke> getKeyStrokes(@NotNull ShortcutSet shortcutSet) {
    Shortcut[] shortcuts = shortcutSet.getShortcuts();
    if (shortcuts.length == 0) {
      return Collections.emptySet();
    }
    Set<KeyStroke> result = new HashSet<>();
    for (Shortcut shortcut : shortcuts) {
      if (!(shortcut instanceof KeyboardShortcut kbShortcut)) {
        continue;
      }
      if (kbShortcut.getSecondKeyStroke() != null) {
        continue;
      }
      result.add(kbShortcut.getFirstKeyStroke());
    }
    return result.isEmpty() ? Collections.emptySet() : result;
  }

  public static @NotNull @NlsContexts.Tooltip String createTooltipText(@NotNull @NlsContexts.Tooltip String name, @NotNull @NonNls String actionId) {
    String text = getFirstKeyboardShortcutText(actionId);
    return text.isEmpty() ? name : name + " (" + text + ")";
  }

  public static @NotNull @NlsSafe String createTooltipText(@Nullable String name, @NotNull AnAction action) {
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

  /** @return text representation of the keymap modifiers, like Ctrl+Shift */
  public static @NotNull String getModifiersText(@JdkConstants.InputEventMask int modifiers) {
    return ourDefaultKeymapTextContext.getModifiersText(KeymapTextContext.mapNewModifiers(modifiers), false);
  }

  /**
   * Checks that one of the mouse shortcuts assigned to the provided action has the same modifiers as provided
   */
  public static boolean matchActionMouseShortcutsModifiers(@NotNull Keymap activeKeymap,
                                                           @JdkConstants.InputEventMask int modifiers,
                                                           @NotNull @NonNls String actionId) {
    final MouseShortcut syntheticShortcut = new MouseShortcut(MouseEvent.BUTTON1, modifiers, 1);
    for (Shortcut shortcut : activeKeymap.getShortcuts(actionId)) {
      if (shortcut instanceof MouseShortcut mouseShortcut) {
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
  public static @NotNull MouseShortcut createMouseShortcut(@NotNull MouseEvent e) {
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

  public static @Nullable ShortcutSet filterKeyStrokes(@NotNull ShortcutSet source, KeyStroke...toLeaveOut) {
    List<Shortcut> filtered = new ArrayList<>(Arrays.asList(source.getShortcuts()));
    for (Shortcut shortcut : source.getShortcuts()) {
      if (shortcut instanceof KeyboardShortcut) {
        if (ArrayUtil.find(toLeaveOut, ((KeyboardShortcut)shortcut).getFirstKeyStroke()) != -1) {
          filtered.remove(shortcut);
        }
      }
    }
    return filtered.isEmpty() ? null : new CustomShortcutSet(filtered.toArray(Shortcut.EMPTY_ARRAY));
  }

  /**
   * @deprecated use {@link #getShortcutsForMnemonicChar} or {@link #getShortcutsForMnemonicCode} instead
   */
  @Deprecated
  public static @Nullable CustomShortcutSet getMnemonicAsShortcut(int mnemonic) {
    return getShortcutsForMnemonicCode(mnemonic);
  }

  public static @Nullable CustomShortcutSet getShortcutsForMnemonicChar(char mnemonic) {
    return getShortcutsForMnemonicCode(KeyEvent.getExtendedKeyCodeForChar(mnemonic));
  }

  public static @Nullable CustomShortcutSet getShortcutsForMnemonicCode(int mnemonic) {
    if (mnemonic != KeyEvent.VK_UNDEFINED) {
      KeyboardShortcut ctrlAltShortcut = new KeyboardShortcut(KeyStroke.getKeyStroke(mnemonic, ALT_DOWN_MASK | CTRL_DOWN_MASK), null);
      KeyboardShortcut altShortcut = new KeyboardShortcut(KeyStroke.getKeyStroke(mnemonic, ALT_DOWN_MASK), null);
      CustomShortcutSet shortcutSet;
      if (SystemInfo.isMac) {
        if (Registry.is("ide.mac.alt.mnemonic.without.ctrl")) {
          shortcutSet = new CustomShortcutSet(ctrlAltShortcut, altShortcut);
        } else {
          shortcutSet = new CustomShortcutSet(ctrlAltShortcut);
        }
      } else {
        shortcutSet = new CustomShortcutSet(altShortcut);
      }
      return shortcutSet;
    }
    return null;
  }
}

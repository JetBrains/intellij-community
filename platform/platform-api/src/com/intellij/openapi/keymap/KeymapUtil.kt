// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.util.ui.JdkConstants
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.awt.AWTKeyStroke
import java.awt.Component
import java.awt.EventQueue
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke

object KeymapUtil {
  private val defaultKeymapTextContext = KeymapTextContext()

  private val tooltipKeys: MutableSet<Int> = HashSet<Int>()
  private val otherTooltipKeys: MutableSet<Int> = HashSet<Int>()
  private var tooltipKeysProperty: RegistryValue? = null

  /**
   * Returns the text of some shortcut from the set, giving preference to keyboard shortcuts
   *
   * @param set the shortcut set
   * @return the first keyboard shortcut text, if any, otherwise the first shortcut text, if any, otherwise an empty string
   */
  @NlsSafe
  @JvmStatic
  fun getShortcutText(set: ShortcutSet): @NlsSafe String {
    val keyboardShortcut = getFirstKeyboardShortcutText(set)
    if (!keyboardShortcut.isEmpty()) {
      return keyboardShortcut
    }
    return getShortcutText(set.getShortcuts().firstOrNull() ?: return "")
  }

  @NlsSafe
  @JvmStatic
  fun getShortcutText(@NonNls actionId: @NonNls String): @NlsSafe String {
    if (serviceIfCreated<KeymapManager>() == null) {
      return ""
    }
    return defaultKeymapTextContext.getShortcutText(actionId)
  }

  @NlsSafe
  @JvmStatic
  fun getShortcutTextOrNull(@NonNls actionId: @NonNls String): @NlsSafe String? {
    if (serviceIfCreated<KeymapManager>() == null) {
      return null
    }
    return getShortcutText(ActionManager.getInstance().getKeyboardShortcut(actionId) ?: return null)
  }

  @NlsSafe
  @JvmStatic
  fun getShortcutText(shortcut: Shortcut): @NlsSafe String {
    return defaultKeymapTextContext.getShortcutText(shortcut)
  }

  @NlsSafe
  @JvmStatic
  fun getMouseShortcutText(shortcut: MouseShortcut): @NlsSafe String {
    return defaultKeymapTextContext.getMouseShortcutText(shortcut)
  }

  @NlsSafe
  @JvmStatic
  fun getKeystrokeText(accelerator: KeyStroke?): @NlsSafe String {
    return defaultKeymapTextContext.getKeystrokeText(accelerator)
  }

  @JvmStatic
  fun getKeyText(code: Int): String {
    return defaultKeymapTextContext.getKeyText(code)
  }

  @JvmStatic
  val isSimplifiedMacShortcuts: Boolean
    get() = defaultKeymapTextContext.isSimplifiedMacShortcuts

  @JvmStatic
  fun getActiveKeymapShortcuts(@NonNls actionId: @NonNls String?): ShortcutSet {
    if (actionId != null) {
      val keymapManager = KeymapManager.getInstance()
      if (keymapManager != null) {
        val actionManager = serviceIfCreated<ActionManager>()
        if (actionManager != null) {
          return getActiveKeymapShortcuts(actionId, keymapManager)
        }
      }
    }
    return CustomShortcutSet(*Shortcut.EMPTY_ARRAY)
  }

  /**
   * @param actionId action to find the shortcut for
   * @return first keyboard shortcut that activates given action in active keymap; null if not found
   */
  @JvmStatic
  fun getPrimaryShortcut(@NonNls actionId: @NonNls String?): Shortcut? {
    if (actionId == null) {
      return null
    }

    val keymapManager = KeymapManager.getInstance() ?: return null
    return keymapManager.getActiveKeymap().getShortcuts(actionId).firstOrNull()
  }

  @NlsSafe
  @JvmStatic
  fun getFirstKeyboardShortcutText(@NonNls actionId: @NonNls String): @NlsSafe String {
    if (serviceIfCreated<KeymapManager>() == null) {
      return ""
    }
    for (shortcut in getActiveKeymapShortcuts(actionId).getShortcuts()) {
      if (shortcut is KeyboardShortcut) {
        return getShortcutText(shortcut)
      }
    }
    return ""
  }

  @NlsSafe
  @JvmStatic
  fun getFirstMouseShortcutText(@NonNls actionId: @NonNls String): @NlsSafe String {
    if (serviceIfCreated<KeymapManager>() == null) {
      return ""
    }
    for (shortcut in getActiveKeymapShortcuts(actionId).getShortcuts()) {
      if (shortcut is MouseShortcut) {
        return getShortcutText(shortcut)
      }
    }
    return ""
  }

  @JvmStatic
  fun isEventForAction(keyEvent: KeyEvent, @NonNls actionId: @NonNls String): Boolean {
    for (shortcut in getActiveKeymapShortcuts(actionId).getShortcuts()) {
      if (shortcut is KeyboardShortcut && AWTKeyStroke.getAWTKeyStrokeForEvent(keyEvent) === shortcut.firstKeyStroke) {
        return true
      }
    }
    return false
  }

  @NlsSafe
  @JvmStatic
  fun getFirstKeyboardShortcutText(action: AnAction): @NlsSafe String {
    return getFirstKeyboardShortcutText(getShortcutSetForDisplay(action))
  }

  @NlsSafe
  @JvmStatic
  fun getFirstKeyboardShortcutText(set: ShortcutSet): @NlsSafe String {
    val shortcuts = set.getShortcuts()
    val shortcut = shortcuts.firstOrNull { it is KeyboardShortcut } as? KeyboardShortcut
    return if (shortcut == null) "" else getShortcutText(shortcut)
  }

  @NlsSafe
  @JvmStatic
  fun getPreferredShortcutText(shortcuts: Array<Shortcut>): @NlsSafe String {
    val shortcut = shortcuts.firstOrNull { it is KeyboardShortcut } as? KeyboardShortcut
    if (shortcut == null) {
      return if (shortcuts.isEmpty()) "" else getShortcutText(shortcuts[0])
    }
    else {
      return getShortcutText(shortcut)
    }
  }

  @NlsSafe
  @JvmStatic
  fun getShortcutsText(shortcuts: Array<Shortcut>): @NlsSafe String {
    if (shortcuts.isEmpty()) {
      return ""
    }
    return shortcuts.joinToString(" ") { getShortcutText(it) }
  }

  /**
   * Factory method. It parses passed string and creates `MouseShortcut`.
   *
   * @param keystrokeString       target keystroke
   * @return                      shortcut for the given keystroke
   * @throws InvalidDataException if `keystrokeString` doesn't represent valid `MouseShortcut`.
   */
  @Throws(InvalidDataException::class)
  @JvmStatic
  fun parseMouseShortcut(keystrokeString: String): MouseShortcut {
    return defaultKeymapTextContext.parseMouseShortcut(keystrokeString)
  }

  /**
   * Similar to [KeyStroke.getKeyStroke] but allows keys in lower case.
   * For example, "control x" is accepted and interpreted as "control X".
   */
  @JvmStatic
  fun getKeyStroke(s: String): KeyStroke? {
    var s = s
    var result: KeyStroke? = null
    if (s.length >= 2 && s.get(s.length - 2) == ' ' && Character.isLowerCase(s.get(s.length - 1))) {
      // there's no java.awt.event.KeyEvent.VK_x, but there is VK_X
      s = s.substring(0, s.length - 1) + s.get(s.length - 1).uppercaseChar()
    }
    try {
      result = KeyStroke.getKeyStroke(s)
    }
    catch (_: Exception) {
      //ok
    }
    if (result == null && s.length >= 2 && s.get(s.length - 2) == ' ') {
      try {
        val s1 = s.substring(0, s.length - 1) + s.get(s.length - 1).uppercaseChar()
        result = KeyStroke.getKeyStroke(s1)
      }
      catch (_: Exception) {
      }
    }
    return result
  }

  /**
   * @return string representation of passed mouse shortcut. This method should
   * be used only for serializing of the `MouseShortcut`
   */
  @JvmStatic
  fun getMouseShortcutString(shortcut: MouseShortcut): String {
    return defaultKeymapTextContext.getMouseShortcutString(shortcut)
  }

  @JvmStatic
  fun isTooltipRequest(keyEvent: KeyEvent): Boolean {
    if (tooltipKeysProperty == null) {
      tooltipKeysProperty = Registry.get("ide.forcedShowTooltip")
      tooltipKeysProperty!!.addListener(object : RegistryValueListener {
        override fun afterValueChanged(value: RegistryValue) {
          updateTooltipRequestKey(value)
        }
      }, ApplicationManager.getApplication())

      updateTooltipRequestKey(tooltipKeysProperty!!)
    }

    if (keyEvent.getID() != KeyEvent.KEY_PRESSED) {
      return false
    }

    for (each in tooltipKeys) {
      if ((keyEvent.getModifiers() and each) == 0) {
        return false
      }
    }

    for (each in otherTooltipKeys) {
      if ((keyEvent.getModifiers() and each) > 0) {
        return false
      }
    }

    val code = keyEvent.getKeyCode()
    return code == KeyEvent.VK_META || code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_ALT
  }

  @Suppress("DEPRECATION")
  private fun updateTooltipRequestKey(value: RegistryValue) {
    val text = value.asString()

    tooltipKeys.clear()
    otherTooltipKeys.clear()

    processKey(text.contains("meta"), InputEvent.META_MASK)
    processKey(text.contains("control") || text.contains("ctrl"), InputEvent.CTRL_MASK)
    processKey(text.contains("shift"), InputEvent.SHIFT_MASK)
    processKey(text.contains("alt"), InputEvent.ALT_MASK)
  }

  private fun processKey(condition: Boolean, value: Int) {
    if (condition) {
      tooltipKeys.add(value)
    }
    else {
      otherTooltipKeys.add(value)
    }
  }

  @JvmStatic
  val isEmacsKeymap: Boolean
    get() = isEmacsKeymap(KeymapManager.getInstance().getActiveKeymap())

  @JvmStatic
  fun isEmacsKeymap(keymap: Keymap?): Boolean {
    var keymap = keymap
    while (keymap != null) {
      if ("Emacs".equals(keymap.getName(), ignoreCase = true)) {
        return true
      }
      keymap = keymap.getParent()
    }
    return false
  }

  @JvmStatic
  fun getKeyStroke(shortcutSet: ShortcutSet): KeyStroke? {
    val shortcuts = shortcutSet.getShortcuts()
    val shortcut = shortcuts.firstOrNull() as? KeyboardShortcut ?: return null
    return if (shortcut.secondKeyStroke == null) shortcut.firstKeyStroke else null
  }

  @JvmStatic
  fun getKeyStrokes(shortcutSet: ShortcutSet): Collection<KeyStroke> {
    val shortcuts = shortcutSet.getShortcuts()
    if (shortcuts.isEmpty()) {
      return emptySet()
    }

    val result = HashSet<KeyStroke>()
    for (shortcut in shortcuts) {
      if (shortcut !is KeyboardShortcut) {
        continue
      }
      if (shortcut.secondKeyStroke != null) {
        continue
      }
      result.add(shortcut.firstKeyStroke)
    }
    return if (result.isEmpty()) emptySet() else result
  }

  @NlsContexts.Tooltip
  @JvmStatic
  fun createTooltipText(
    @NlsContexts.Tooltip name: @NlsContexts.Tooltip String,
    @NonNls actionId: @NonNls String,
  ): @NlsContexts.Tooltip String {
    val text = getFirstKeyboardShortcutText(actionId)
    return if (text.isEmpty()) name else "$name ($text)"
  }

  @NlsSafe
  @JvmStatic
  fun createTooltipText(name: String?, action: AnAction): @NlsSafe String {
    var toolTipText = name ?: ""
    while (toolTipText.endsWith('.')) {
      toolTipText = toolTipText.substring(0, toolTipText.length - 1)
    }
    val shortcutsText = getFirstKeyboardShortcutText(action)
    if (!shortcutsText.isEmpty()) {
      toolTipText += " ($shortcutsText)"
    }
    return toolTipText
  }

  /** @return text representation of the keymap modifiers, like Ctrl+Shift
   */
  @JvmStatic
  fun getModifiersText(@JdkConstants.InputEventMask modifiers: Int): String {
    return defaultKeymapTextContext.getModifiersText(KeymapTextContext.mapNewModifiers(modifiers), false)
  }

  /**
   * Checks that one of the mouse shortcuts assigned to the provided action has the same modifiers as provided
   */
  @JvmStatic
  fun matchActionMouseShortcutsModifiers(
    activeKeymap: Keymap,
    @JdkConstants.InputEventMask modifiers: Int,
    @NonNls actionId: @NonNls String,
  ): Boolean {
    val syntheticShortcut = MouseShortcut(MouseEvent.BUTTON1, modifiers, 1)
    for (shortcut in activeKeymap.getShortcuts(actionId)) {
      if (shortcut is MouseShortcut) {
        if (shortcut.modifiers == syntheticShortcut.modifiers) {
          return true
        }
      }
    }
    return false
  }

  /**
   * Creates shortcut corresponding to a single-click event
   */
  @JvmStatic
  fun createMouseShortcut(e: MouseEvent): MouseShortcut {
    var button = MouseShortcut.getButton(e)
    val modifiers = e.getModifiersEx()
    if (button == MouseEvent.NOBUTTON && e.getID() == MouseEvent.MOUSE_DRAGGED) {
      // mouse drag events don't have button field set due to some reason
      if ((modifiers and InputEvent.BUTTON1_DOWN_MASK) != 0) {
        button = MouseEvent.BUTTON1
      }
      else if ((modifiers and InputEvent.BUTTON2_DOWN_MASK) != 0) {
        button = MouseEvent.BUTTON2
      }
    }
    return MouseShortcut(button, modifiers, 1)
  }

  /**
   * @param component    target component to reassign previously mapped action (if any)
   * @param oldKeyStroke previously mapped keystroke (e.g. standard one that you want to use in some different way)
   * @param newKeyStroke new keystroke to be assigned. `null` value means 'just unregister previously mapped action'
   * @param condition    one of
   *
   *  * JComponent.WHEN_FOCUSED,
   *  * JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
   *  * JComponent.WHEN_IN_FOCUSED_WINDOW
   *  * JComponent.UNDEFINED_CONDITION
   *
   * @param muteOldKeystroke if `true` old keystroke wouldn't work anymore
   * @return `true` if the action is reassigned successfully
   */
  @JvmOverloads
  @JvmStatic
  fun reassignAction(
    component: JComponent,
    oldKeyStroke: KeyStroke,
    newKeyStroke: KeyStroke?,
    condition: Int,
    muteOldKeystroke: Boolean = true,
  ): Boolean {
    val action = component.getActionForKeyStroke(oldKeyStroke) ?: return false
    if (newKeyStroke != null) {
      component.registerKeyboardAction(action, newKeyStroke, condition)
    }
    if (muteOldKeystroke) {
      component.registerKeyboardAction(RedispatchEventAction(component), oldKeyStroke, condition)
    }
    return true
  }

  @JvmStatic
  fun filterKeyStrokes(source: ShortcutSet, vararg toLeaveOut: KeyStroke?): ShortcutSet? {
    val shortcuts = source.getShortcuts()
    val keyStrokesToLeaveOut = toLeaveOut.toHashSet()
    val filtered = ArrayList<Shortcut>(shortcuts.size)
    for (shortcut in shortcuts) {
      if (shortcut is KeyboardShortcut && shortcut.firstKeyStroke in keyStrokesToLeaveOut) {
        continue
      }
      filtered.add(shortcut)
    }
    return if (filtered.isEmpty()) null else CustomShortcutSet(*filtered.toArray(Shortcut.EMPTY_ARRAY))
  }

  @JvmStatic
  fun getShortcutsForMnemonicChar(mnemonic: Char): CustomShortcutSet? {
    return getShortcutsForMnemonicCode(KeyEvent.getExtendedKeyCodeForChar(mnemonic.code))
  }

  @JvmStatic
  fun getShortcutsForMnemonicCode(mnemonic: Int): CustomShortcutSet? {
    if (mnemonic == KeyEvent.VK_UNDEFINED) {
      return null
    }

    val ctrlAltShortcut = KeyboardShortcut(KeyStroke.getKeyStroke(mnemonic, InputEvent.ALT_DOWN_MASK or InputEvent.CTRL_DOWN_MASK), null)
    val altShortcut = KeyboardShortcut(KeyStroke.getKeyStroke(mnemonic, InputEvent.ALT_DOWN_MASK), null)
    if (SystemInfoRt.isMac) {
      if (Registry.`is`("ide.mac.alt.mnemonic.without.ctrl")) {
        return CustomShortcutSet(ctrlAltShortcut, altShortcut)
      }
      else {
        return CustomShortcutSet(ctrlAltShortcut)
      }
    }
    else {
      return CustomShortcutSet(altShortcut)
    }
  }
}

private class RedispatchEventAction(private val myComponent: Component) : AbstractAction() {
  override fun actionPerformed(e: ActionEvent?) {
    val event = EventQueue.getCurrentEvent()
    if (event is KeyEvent && event.getSource() === myComponent) {
      val parent = myComponent.getParent() ?: return
      parent.dispatchEvent(
        KeyEvent(
          /* source = */ parent,
          /* id = */ event.getID(),
          /* when = */ event.getWhen(),
          /* modifiers = */ event.getModifiers(),
          /* keyCode = */ event.getKeyCode(),
          /* keyChar = */ event.getKeyChar(),
          /* keyLocation = */ event.getKeyLocation()
        )
      )
    }
  }
}

@Internal
fun getActiveKeymapShortcuts(@NonNls actionId: @NonNls String, keymapManager: KeymapManager): ShortcutSet {
  return CustomShortcutSet(*keymapManager.getActiveKeymap().getShortcuts(actionId))
}

/**
 * Returns shortcuts suitable for displaying action shortcuts without forcing keymap initialization.
 */
@Internal
fun getShortcutSetForDisplay(action: AnAction): ShortcutSet {
  val actionId = serviceIfCreated<ActionManager>()?.getId(action) ?: return action.shortcutSet
  val keymapManager = serviceIfCreated<KeymapManager>() ?: return CustomShortcutSet.EMPTY
  return getActiveKeymapShortcuts(actionId, keymapManager)
}


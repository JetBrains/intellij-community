// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.ui.laf.darcula

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.bootstrap.createBaseLaF
import com.intellij.ide.ui.UITheme
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.util.*
import com.intellij.ui.ComponentUtil
import com.intellij.ui.TableActions
import com.intellij.util.ResourceUtil
import com.intellij.util.ui.JBDimension
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.TestOnly
import java.awt.*
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import javax.swing.*
import javax.swing.UIDefaults.ActiveValue
import javax.swing.UIDefaults.LazyInputMap
import javax.swing.plaf.FontUIResource
import javax.swing.plaf.basic.BasicLookAndFeel
import javax.swing.plaf.metal.MetalLookAndFeel
import javax.swing.text.DefaultEditorKit
import kotlin.time.Duration.Companion.milliseconds

private val LOG: Logger
  get() = logger<DarculaLaf>()

private const val DESCRIPTION: @NlsSafe String = "IntelliJ Dark Look and Feel"

/**
 * @author Konstantin Bulenkov
 */
open class DarculaLaf(private val isThemeAdapter: Boolean) : BasicLookAndFeel() {
  private var base: LookAndFeel? = null
  private val baseDefaults = UIDefaults()

  @TestOnly
  constructor() : this(isThemeAdapter = false)

  companion object {
    const val NAME: @NlsSafe String = "Darcula"

    private val preInitializedBaseLaf = AtomicReference<LookAndFeel?>()

    fun setPreInitializedBaseLaf(value: LookAndFeel): Boolean {
      return preInitializedBaseLaf.compareAndSet(null, value)
    }

    @JvmStatic
    var isAltPressed: Boolean = false
      internal set
  }

  override fun getDefaults(): UIDefaults {
    try {
      val defaults = base!!.defaults
      baseDefaults.putAll(defaults)
      if (SystemInfoRt.isLinux && listOf("CN", "JP", "KR", "TW").contains(Locale.getDefault().country)) {
        for (key in defaults.keys) {
          if (key.toString().endsWith(".font")) {
            val font = toFont(defaults, key)
            defaults.put(key, FontUIResource("Dialog", font.style, font.size))
          }
        }
      }
      initInputMapDefaults(defaults)
      initDarculaDefaults(defaults)

      if (!isThemeAdapter) {
        defaults.put("ui.theme.is.dark", true)
      }

      patchComboBox(defaults)

      defaults.remove("Spinner.arrowButtonBorder")
      defaults.put("Spinner.arrowButtonSize", JBDimension(16, 5).asUIResource())
      if (SystemInfoRt.isMac) {
        defaults.put("RootPane.defaultButtonWindowKeyBindings", arrayOf<Any>(
          "ENTER", "press",
          "released ENTER", "release",
          "ctrl ENTER", "press",
          "ctrl released ENTER", "release",
          "meta ENTER", "press",
          "meta released ENTER", "release"
        ))
      }
      defaults.put("EditorPane.font", toFont(defaults, "TextField.font"))
      return defaults
    }
    catch (e: Exception) {
      LOG.error(e)
    }
    return super.getDefaults()
  }

  protected open val prefix: String
    get() = "themes/darcula"
  private fun initDarculaDefaults(defaults: UIDefaults) {
    if (!isThemeAdapter) {
      // it is important to use class loader of a current instance class (LaF in plugin)
      val classLoader = javaClass.getClassLoader()
      deprecatedLoadDefaultsFromJson(defaults = defaults, prefix = prefix, classLoader = classLoader)
    }

    defaults.put("Table.ancestorInputMap", LazyInputMap(arrayOf<Any>(
      "ctrl C", "copy",
      "meta C", "copy",
      "ctrl V", "paste",
      "meta V", "paste",
      "ctrl X", "cut",
      "meta X", "cut",
      "COPY", "copy",
      "PASTE", "paste",
      "CUT", "cut",
      "control INSERT", "copy",
      "shift INSERT", "paste",
      "shift DELETE", "cut",
      "RIGHT", TableActions.Right.ID,
      "KP_RIGHT", TableActions.Right.ID,
      "LEFT", TableActions.Left.ID,
      "KP_LEFT", TableActions.Left.ID,
      "DOWN", TableActions.Down.ID,
      "KP_DOWN", TableActions.Down.ID,
      "UP", TableActions.Up.ID,
      "KP_UP", TableActions.Up.ID,
      "shift RIGHT", TableActions.ShiftRight.ID,
      "shift KP_RIGHT", TableActions.ShiftRight.ID,
      "shift LEFT", TableActions.ShiftLeft.ID,
      "shift KP_LEFT", TableActions.ShiftLeft.ID,
      "shift DOWN", TableActions.ShiftDown.ID,
      "shift KP_DOWN", TableActions.ShiftDown.ID,
      "shift UP", TableActions.ShiftUp.ID,
      "shift KP_UP", TableActions.ShiftUp.ID,
      "PAGE_UP", TableActions.PageUp.ID,
      "PAGE_DOWN", TableActions.PageDown.ID,
      "HOME", "selectFirstColumn",
      "END", "selectLastColumn",
      "shift PAGE_UP", TableActions.ShiftPageUp.ID,
      "shift PAGE_DOWN", TableActions.ShiftPageDown.ID,
      "shift HOME", "selectFirstColumnExtendSelection",
      "shift END", "selectLastColumnExtendSelection",
      "ctrl PAGE_UP", "scrollLeftChangeSelection",
      "ctrl PAGE_DOWN", "scrollRightChangeSelection",
      "ctrl HOME", TableActions.CtrlHome.ID,
      "ctrl END", TableActions.CtrlEnd.ID,
      "ctrl shift PAGE_UP", "scrollRightExtendSelection",
      "ctrl shift PAGE_DOWN", "scrollLeftExtendSelection",
      "ctrl shift HOME", TableActions.CtrlShiftHome.ID,
      "ctrl shift END", TableActions.CtrlShiftEnd.ID,
      "TAB", "selectNextColumnCell",
      "shift TAB", "selectPreviousColumnCell",  //"ENTER", "selectNextRowCell",
      "shift ENTER", "selectPreviousRowCell",
      "ctrl A", "selectAll",
      "meta A", "selectAll",
      "ESCAPE", "cancel",
      "F2", "startEditing"
    )))
  }

  override fun getName() = NAME

  override fun getID() = NAME

  override fun getDescription() = DESCRIPTION

  override fun isNativeLookAndFeel() = true

  override fun isSupportedLookAndFeel() = true

  override fun initialize() {
    try {
      if (base == null) {
        base = preInitializedBaseLaf.getAndSet(null)
        if (base == null) {
          base = createBaseLaF()
        }
        else {
          // base is already initialized
          return
        }
      }
      base!!.initialize()
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

  override fun uninitialize() {
    try {
      base?.uninitialize()
    }
    catch (ignore: Exception) {
    }
  }

  override fun loadSystemColors(defaults: UIDefaults, systemColors: Array<String>, useNative: Boolean) {
    try {
      val superMethod = BasicLookAndFeel::class.java.getDeclaredMethod("loadSystemColors",
                                                                       UIDefaults::class.java,
                                                                       Array<String>::class.java,
                                                                       Boolean::class.javaPrimitiveType)
      superMethod.setAccessible(true)
      // invoke method on a base LaF, not on our instance
      superMethod.invoke(base, defaults, systemColors, useNative)
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }

  override fun getDisabledIcon(component: JComponent?, icon: Icon?): Icon? = icon?.let { IconLoader.getDisabledIcon(it) }

  override fun getSupportsWindowDecorations() = true
}

private fun toFont(defaults: UIDefaults, key: Any): Font {
  var value = defaults.get(key)
  if (value is Font) {
    return value
  }
  else if (value is ActiveValue) {
    value = value.createValue(defaults)
    if (value is Font) {
      return value
    }
  }
  throw UnsupportedOperationException("Unable to extract Font from \"$key\"")
}

private fun patchComboBox(defaults: UIDefaults) {
  val metalDefaults = MetalLookAndFeel().getDefaults()
  defaults.remove("ComboBox.ancestorInputMap")
  defaults.remove("ComboBox.actionMap")
  defaults.put("ComboBox.ancestorInputMap", metalDefaults.get("ComboBox.ancestorInputMap"))
  defaults.put("ComboBox.actionMap", metalDefaults.get("ComboBox.actionMap"))
}

private class RepaintMnemonicRequest(@JvmField val focusOwner: Component, @JvmField val pressed: Boolean)

@OptIn(FlowPreview::class)
@Service
private class MnemonicListenerService(coroutineScope: CoroutineScope) {
  // null as "cancel all"
  private val repaintRequests = MutableSharedFlow<RepaintMnemonicRequest?>(replay=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    coroutineScope.launch(CoroutineName("LaF Mnemonic Support")) {
      val repaintDispatcher = Dispatchers.EDT + ModalityState.any().asContextElement()

      repaintRequests
        .debounce(10.milliseconds)
        .collectLatest {
          it?.let {
            withContext(repaintDispatcher) {
              repaintMnemonics(focusOwner = it.focusOwner, pressed = it.pressed)
            }
          }
        }
    }

    IdeEventQueue.getInstance().addDispatcher(object : IdeEventQueue.EventDispatcher {
      override fun dispatch(e: AWTEvent): Boolean {
        if (e !is KeyEvent || e.keyCode != KeyEvent.VK_ALT) {
          return false
        }

        DarculaLaf.isAltPressed = e.getID() == KeyEvent.KEY_PRESSED
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        check(repaintRequests.tryEmit(focusOwner?.let {
          RepaintMnemonicRequest(focusOwner = focusOwner, pressed = DarculaLaf.isAltPressed)
        }))
        return false
      }
    }, coroutineScope)
  }

  private fun repaintMnemonics(focusOwner: Component, pressed: Boolean) {
    if (pressed != DarculaLaf.isAltPressed) {
      return
    }

    val window = SwingUtilities.windowForComponent(focusOwner) ?: return
    for (component in window.components) {
      if (component is JComponent) {
        for (c in ComponentUtil.findComponentsOfType(component, JComponent::class.java)) {
          if (c is JLabel && c.displayedMnemonicIndex != -1 || c is AbstractButton && c.displayedMnemonicIndex != -1) {
            c.repaint()
          }
        }
      }
    }
  }
}

private class MnemonicListener : ApplicationInitializedListener {
  init {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(asyncScope: CoroutineScope) {
    asyncScope.launch {
      serviceAsync<MnemonicListenerService>()
    }
  }
}

private fun deprecatedLoadDefaultsFromJson(defaults: UIDefaults, prefix: String, classLoader: ClassLoader) {
  val filename = "$prefix.theme.json"
  val data = ResourceUtil.getResourceAsBytes(filename, classLoader, /* checkParents */true)
             ?: throw RuntimeException("Can't load $filename")
  UITheme.loadFromJson(data = data, themeId = "Darcula", provider = classLoader).applyProperties(defaults)
}

internal fun initInputMapDefaults(defaults: UIDefaults) {
  // Make ENTER work in JTrees
  val treeInputMap = defaults.get("Tree.focusInputMap") as InputMap?
  treeInputMap?.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "toggle")
  // Cut/Copy/Paste in JTextAreas
  val textAreaInputMap = defaults.get("TextArea.focusInputMap") as InputMap?
  if (textAreaInputMap != null) {
    // It really can be null, for example, when LAF isn't properly initialized (an Alloy license problem)
    installCutCopyPasteShortcuts(textAreaInputMap, false)
  }
  // Cut/Copy/Paste in JTextFields
  val textFieldInputMap = defaults.get("TextField.focusInputMap") as InputMap?
  if (textFieldInputMap != null) {
    // It really can be null, for example, when LAF isn't properly initialized (an Alloy license problem)
    installCutCopyPasteShortcuts(textFieldInputMap, false)
  }
  // Cut/Copy/Paste in JPasswordField
  val passwordFieldInputMap = defaults.get("PasswordField.focusInputMap") as InputMap?
  if (passwordFieldInputMap != null) {
    // It really can be null, for example, when LAF isn't properly initialized (an Alloy license problem)
    installCutCopyPasteShortcuts(passwordFieldInputMap, false)
  }
  // Cut/Copy/Paste in JTables
  val tableInputMap = defaults.get("Table.ancestorInputMap") as InputMap?
  if (tableInputMap != null) {
    // It really can be null, for example, when LAF isn't properly initialized (an Alloy license problem)
    installCutCopyPasteShortcuts(tableInputMap, true)
  }
}

private fun installCutCopyPasteShortcuts(inputMap: InputMap, useSimpleActionKeys: Boolean) {
  val copyActionKey = if (useSimpleActionKeys) "copy" else DefaultEditorKit.copyAction
  val pasteActionKey = if (useSimpleActionKeys) "paste" else DefaultEditorKit.pasteAction
  val cutActionKey = if (useSimpleActionKeys) "cut" else DefaultEditorKit.cutAction
  // Ctrl+Ins, Shift+Ins, Shift+Del
  inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, InputEvent.CTRL_DOWN_MASK), copyActionKey)
  inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, InputEvent.SHIFT_DOWN_MASK), pasteActionKey)
  inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, InputEvent.SHIFT_DOWN_MASK), cutActionKey)
  // Ctrl+C, Ctrl+V, Ctrl+X
  inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), copyActionKey)
  inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), pasteActionKey)
  inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK), DefaultEditorKit.cutAction)
}
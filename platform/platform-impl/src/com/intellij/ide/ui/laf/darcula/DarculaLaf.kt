// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.ui.laf.darcula

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.bootstrap.createBaseLaF
import com.intellij.ide.ui.UITheme
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.*
import com.intellij.ui.ComponentUtil
import com.intellij.ui.TableActions
import com.intellij.util.Alarm
import com.intellij.util.ResourceUtil
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.StartupUiUtil.initInputMapDefaults
import org.jetbrains.annotations.TestOnly
import java.awt.*
import java.awt.event.KeyEvent
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import javax.swing.*
import javax.swing.UIDefaults.ActiveValue
import javax.swing.UIDefaults.LazyInputMap
import javax.swing.plaf.FontUIResource
import javax.swing.plaf.basic.BasicLookAndFeel
import javax.swing.plaf.metal.MetalLookAndFeel

private val LOG: Logger
  get() = logger<DarculaLaf>()

private const val DESCRIPTION: @NlsSafe String = "IntelliJ Dark Look and Feel"

/**
 * @author Konstantin Bulenkov
 */
open class DarculaLaf(private val isThemeAdapter: Boolean) : BasicLookAndFeel() {
  private var base: LookAndFeel? = null
  private var disposable: Disposable? = null
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
      private set
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
  protected open val systemPrefix: String?
    get() = null

  private fun initDarculaDefaults(defaults: UIDefaults) {
    if (!isThemeAdapter) {
      // it is important to use class loader of a current instance class (LaF in plugin)
      val classLoader = javaClass.getClassLoader()
      deprecatedLoadDefaultsFromJson(defaults = defaults, prefix = prefix, classLoader = classLoader)
      systemPrefix?.let {
        deprecatedLoadDefaultsFromJson(defaults = defaults, prefix = it, classLoader = classLoader)
      }
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
      }
      base!!.initialize()
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
    ideEventQueueInitialized(IdeEventQueue.getInstance())
  }

  private fun ideEventQueueInitialized(eventQueue: IdeEventQueue) {
    if (disposable == null) {
      disposable = Disposer.newDisposable()
      if (LoadingState.COMPONENTS_REGISTERED.isOccurred) {
        Disposer.register(ApplicationManager.getApplication(), disposable!!)
      }
    }
    eventQueue.addDispatcher(object : IdeEventQueue.EventDispatcher {
      private var mnemonicAlarm: Alarm? = null

      override fun dispatch(e: AWTEvent): Boolean {
        if (e !is KeyEvent || e.keyCode != KeyEvent.VK_ALT) {
          return false
        }

        isAltPressed = e.getID() == KeyEvent.KEY_PRESSED
        var mnemonicAlarm = mnemonicAlarm
        if (mnemonicAlarm == null) {
          mnemonicAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)
          this.mnemonicAlarm = mnemonicAlarm
        }
        mnemonicAlarm.cancelAllRequests()
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        if (focusOwner != null) {
          mnemonicAlarm.addRequest(Runnable { repaintMnemonics(focusOwner, isAltPressed) }, 10)
        }
        return false
      }
    }, disposable)
  }

  override fun uninitialize() {
    try {
      base?.uninitialize()
    }
    catch (ignore: Exception) {
    }

    disposable?.let {
      disposable = null
      Disposer.dispose(it)
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

private fun deprecatedLoadDefaultsFromJson(defaults: UIDefaults, prefix: String, classLoader: ClassLoader) {
  val filename = "$prefix.theme.json"
  val data = ResourceUtil.getResourceAsBytes(filename, classLoader,  /* checkParents */true)
             ?: throw RuntimeException("Can't load $filename")
  UITheme.loadFromJson(data = data, themeId = "Darcula", provider = classLoader).applyProperties(defaults)
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.IdeEventQueue.Companion.getInstance
import com.intellij.ide.ui.UITheme
import com.intellij.ide.ui.laf.IdeaLaf
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.*
import com.intellij.openapi.util.IconLoader.getDisabledIcon
import com.intellij.ui.ComponentUtil
import com.intellij.ui.TableActions
import com.intellij.util.Alarm
import com.intellij.util.ResourceUtil
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.StartupUiUtil.initInputMapDefaults
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.KeyEvent
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function
import javax.swing.*
import javax.swing.UIDefaults.ActiveValue
import javax.swing.UIDefaults.LazyInputMap
import javax.swing.plaf.FontUIResource
import javax.swing.plaf.basic.BasicLookAndFeel
import javax.swing.plaf.metal.MetalLookAndFeel

/**
 * @author Konstantin Bulenkov
 */
open class DarculaLaf : BasicLookAndFeel(), UserDataHolder {
  private var base: LookAndFeel? = null
  private var disposable: Disposable? = null
  private val userData = UserDataHolderBase()
  protected val baseDefaults: UIDefaults = UIDefaults()
  override fun <T> getUserData(key: Key<T>): T? {
    return userData.getUserData(key)
  }

  override fun <T> putUserData(key: Key<T>, value: T?) {
    userData.putUserData(key, value)
  }

  override fun getDefaults(): UIDefaults {
    try {
      val metalDefaults = MetalLookAndFeel().getDefaults()
      val defaults = base!!.defaults
      baseDefaults.putAll(defaults)
      if (SystemInfoRt.isLinux && listOf("CN", "JP", "KR", "TW").contains(Locale.getDefault().country)) {
        for (key in defaults.keys) {
          if (key.toString().endsWith(".font")) {
            val font = toFont(defaults, key)
            defaults[key] = FontUIResource("Dialog", font.style, font.size)
          }
        }
      }
      initInputMapDefaults(defaults)
      initIdeaDefaults(defaults)
      patchComboBox(metalDefaults, defaults)
      defaults.remove("Spinner.arrowButtonBorder")
      defaults["Spinner.arrowButtonSize"] = JBDimension(16, 5).asUIResource()
      if (SystemInfoRt.isMac) {
        defaults["RootPane.defaultButtonWindowKeyBindings"] = arrayOf<Any>(
          "ENTER", "press",
          "released ENTER", "release",
          "ctrl ENTER", "press",
          "ctrl released ENTER", "release",
          "meta ENTER", "press",
          "meta released ENTER", "release"
        )
      }
      defaults["EditorPane.font"] = toFont(defaults, "TextField.font")
      return defaults
    }
    catch (e: Exception) {
      log(e)
    }
    return super.getDefaults()
  }

  protected open val prefix: String
    get() = "com/intellij/ide/ui/laf/darcula/darcula"
  protected open val systemPrefix: String?
    get() = null

  protected fun initIdeaDefaults(defaults: UIDefaults) {
    loadDefaults(defaults)
    defaults["Table.ancestorInputMap"] = LazyInputMap(arrayOf<Any>(
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
    ))
  }

  protected open fun loadDefaults(defaults: UIDefaults) {
    defaults["ClassLoader"] = javaClass.getClassLoader()
    loadDefaultsFromJson(defaults)
  }

  protected fun loadDefaultsFromJson(defaults: UIDefaults) {
    loadDefaultsFromJson(defaults, prefix)
    val systemPrefix = systemPrefix
    if (systemPrefix != null) {
      loadDefaultsFromJson(defaults, systemPrefix)
    }
  }

  private fun loadDefaultsFromJson(defaults: UIDefaults, prefix: String) {
    val filename = "$prefix.theme.json"
    try {
      // it is important to use class loader of a current instance class (LaF in plugin)
      val classLoader = javaClass.getClassLoader()
      // macOS light theme uses theme file from core plugin
      val data = ResourceUtil.getResourceAsBytes(filename, classLoader,  /* checkParents */true)
      if (data == null) {
        throw RuntimeException("Can't load $filename")
      }
      val theme = UITheme.loadFromJson(data, "Darcula", classLoader, Function.identity())
      theme.applyProperties(defaults)
    }
    catch (e: IOException) {
      log(e)
    }
  }

  fun getBaseColor(key: String?): Color {
    return baseDefaults.getColor(key)
  }

  override fun getName(): @Nls(capitalization = Nls.Capitalization.Title) String? {
    return NAME
  }

  override fun getID(): String {
    return name!!
  }

  override fun getDescription(): String {
    return DESCRIPTION!!
  }

  override fun isNativeLookAndFeel(): Boolean {
    return true
  }

  override fun isSupportedLookAndFeel(): Boolean {
    return true
  }

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
      log(e)
    }
    ideEventQueueInitialized(getInstance())
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
          mnemonicAlarm.addRequest(
            Runnable { repaintMnemonics(focusOwner, isAltPressed) }, 10)
        }
        return false
      }
    }, disposable)
  }

  override fun uninitialize() {
    try {
      base!!.uninitialize()
    }
    catch (ignore: Exception) {
    }
    if (disposable != null) {
      Disposer.dispose(disposable!!)
      disposable = null
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
      log(e)
    }
  }

  override fun getDisabledIcon(component: JComponent, icon: Icon): Icon {
    return if (icon == null) null else getDisabledIcon(icon)
  }

  override fun getSupportsWindowDecorations(): Boolean {
    return true
  }

  companion object {
    val NAME: @NlsSafe String? = "Darcula"
    private val DESCRIPTION: @NlsSafe String? = "IntelliJ Dark Look and Feel"
    private val preInitializedBaseLaf = AtomicReference<LookAndFeel?>()
    fun setPreInitializedBaseLaf(value: LookAndFeel): Boolean {
      return preInitializedBaseLaf.compareAndSet(null, value)
    }

    @JvmStatic
    var isAltPressed: Boolean = false
      private set

    protected fun log(e: Throwable?) {
      Logger.getInstance(DarculaLaf::class.java).error(e)
    }

    private fun toFont(defaults: UIDefaults, key: Any): Font {
      var value = defaults[key]
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

    private fun patchComboBox(metalDefaults: UIDefaults, defaults: UIDefaults) {
      defaults.remove("ComboBox.ancestorInputMap")
      defaults.remove("ComboBox.actionMap")
      defaults["ComboBox.ancestorInputMap"] = metalDefaults["ComboBox.ancestorInputMap"]
      defaults["ComboBox.actionMap"] = metalDefaults["ComboBox.actionMap"]
    }

    @JvmStatic
    @ApiStatus.Internal
    @Throws(Throwable::class)
    fun createBaseLaF(): LookAndFeel {
      if (SystemInfoRt.isMac) {
        val aClass = DarculaLaf::class.java.getClassLoader().loadClass(UIManager.getSystemLookAndFeelClassName())
        return MethodHandles.lookup().findConstructor(aClass, MethodType.methodType(Void.TYPE)).invoke() as BasicLookAndFeel
      }
      else if (!SystemInfoRt.isLinux) {
        return IdeaLaf(null)
      }
      val fontDefaults: MutableMap<Any, Any?> = HashMap()
      // Normally, GTK LaF is considered "system" when (1) a GNOME session is active, and (2) GTK library is available.
      // Here, we weaken the requirements to only (2) and force GTK LaF installation to let it detect the system fonts
      // and scale them based on Xft.dpi value.
      try {
        @Suppress("SpellCheckingInspection") val name = "com.sun.java.swing.plaf.gtk.GTKLookAndFeel"
        val aClass = DarculaLaf::class.java.getClassLoader().loadClass(name)
        val gtk = MethodHandles.lookup().findConstructor(aClass, MethodType.methodType(Void.TYPE)).invoke() as LookAndFeel
        if (gtk.isSupportedLookAndFeel) {  // GTK is available
          gtk.initialize() // on JBR 11, overrides `SunGraphicsEnvironment#uiScaleEnabled` (sets `#uiScaleEnabled_overridden` to `false`)
          val gtkDefaults = gtk.defaults
          for (key in gtkDefaults.keys) {
            if (key.toString().endsWith(".font")) {
              fontDefaults[key] = gtkDefaults[key] // `UIDefaults#get` unwraps lazy values
            }
          }
        }
      }
      catch (e: Exception) {
        Logger.getInstance(DarculaLaf::class.java).debug(e)
      }
      return IdeaLaf(if (fontDefaults.isEmpty()) null else fontDefaults)
    }

    private fun repaintMnemonics(focusOwner: Component, pressed: Boolean) {
      if (pressed != isAltPressed) {
        return
      }
      val window = SwingUtilities.windowForComponent(focusOwner)
      if (window == null) {
        return
      }
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
}

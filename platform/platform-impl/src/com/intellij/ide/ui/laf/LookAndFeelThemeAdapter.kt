// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.ui.laf

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.UITheme
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.ColorUtil
import com.intellij.ui.TableActions
import com.intellij.util.ResourceUtil
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import javax.swing.*
import javax.swing.UIDefaults.LazyValue
import javax.swing.plaf.FontUIResource
import javax.swing.plaf.basic.BasicLookAndFeel
import javax.swing.plaf.metal.MetalLookAndFeel
import javax.swing.text.DefaultEditorKit

@Internal
class LookAndFeelThemeAdapter(
  private val base: LookAndFeel,
  private val theme: UIThemeLookAndFeelInfo,
) : BasicLookAndFeel() {
  companion object {
    @JvmField
    val preInitializedBaseLaf: AtomicReference<LookAndFeel?> = AtomicReference<LookAndFeel?>()

    @JvmStatic
    var isAltPressed: Boolean = false
      internal set
  }

  override fun getDefaults(): UIDefaults {
    val defaults = base.defaults
    initBaseLaF(defaults)

    defaults.put("Menu.arrowIcon", DefaultMenuArrowIcon)
    defaults.put("MenuItem.background", UIManager.getColor("Menu.background"))

    theme.installTheme(defaults)

    defaults.put("Tree.ancestorInputMap", null)

    if (SystemInfoRt.isLinux && listOf("CN", "JP", "KR", "TW").contains(Locale.getDefault().country)) {
      for (key in defaults.keys) {
        if (key.toString().endsWith(".font")) {
          val font = toFont(defaults, key)
          defaults.put(key, FontUIResource("Dialog", font.style, font.size))
        }
      }
    }
    return defaults
  }

  override fun initialize() {
    base.initialize()
  }

  override fun uninitialize() {
    base.uninitialize()
  }

  override fun getID(): String = "LookAndFeelThemeAdapter"

  override fun getDescription(): String = getID()

  override fun getName(): String = getID()

  override fun isNativeLookAndFeel(): Boolean = true

  override fun isSupportedLookAndFeel(): Boolean = true
}

private object DefaultMenuArrowIcon : MenuArrowIcon(
  icon = { AllIcons.Icons.Ide.MenuArrow },
  selectedIcon = { if (DefaultMenuArrowIcon.dark) AllIcons.Icons.Ide.MenuArrowSelected else AllIcons.Icons.Ide.MenuArrow },
  disabledIcon = { IconLoader.getDisabledIcon(AllIcons.Icons.Ide.MenuArrow) },
) {
  private val dark: Boolean
    get() = ColorUtil.isDark(UIManager.getColor("MenuItem.selectionBackground"))
}

internal fun initBaseLaF(defaults: UIDefaults) {
  initInputMapDefaults(defaults)

  patchComboBox(defaults)

  // these icons are only needed to prevent Swing from trying to fetch defaults with AWT ImageFetcher threads (IDEA-322089),
  // but might as well just put something sensibly-looking there, just in case they show up due to some bug
  val folderIcon = LazyValue { AllIcons.Nodes.Folder }
  defaults.put("Tree.openIcon", folderIcon)
  defaults.put("Tree.closedIcon", folderIcon)
  defaults.put("Tree.leafIcon", LazyValue { AllIcons.FileTypes.Any_type })
  // our themes actually set these two, but just in case
  defaults.put("Tree.expandedIcon", LazyValue { AllIcons.Toolbar.Expand })
  defaults.put("Tree.collapsedIcon", LazyValue { AllIcons.Actions.ArrowExpand })

  defaults.put("Table.ancestorInputMap", UIDefaults.LazyInputMap(arrayOf<Any>(
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

  defaults.remove("Spinner.arrowButtonBorder")
  defaults.put("Spinner.arrowButtonSize", JBDimension(16, 5).asUIResource())
  if (ClientSystemInfo.isMac()) {
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

  patchFileChooserStrings(defaults)

  defaults.put("Balloon.error.textInsets", JBInsets(3, 8, 3, 8).asUIResource())
}

private fun patchFileChooserStrings(defaults: UIDefaults) {
  val fileChooserTextKeys = arrayOf(
    "FileChooser.viewMenuLabelText", "FileChooser.newFolderActionLabelText",
    "FileChooser.listViewActionLabelText", "FileChooser.detailsViewActionLabelText", "FileChooser.refreshActionLabelText"
  )

  if (!defaults.containsKey(fileChooserTextKeys[0])) {
    // Alloy L&F does not define strings for names of context menu actions, so we have to patch them in here
    for (key in fileChooserTextKeys) {
      defaults.put(key, LazyValue { IdeBundle.message(key) })
    }
  }
}

private fun patchComboBox(defaults: UIDefaults) {
  val metalDefaults = MetalLookAndFeel().getDefaults()
  defaults.remove("ComboBox.ancestorInputMap")
  defaults.remove("ComboBox.actionMap")
  defaults.put("ComboBox.ancestorInputMap", metalDefaults.get("ComboBox.ancestorInputMap"))
  defaults.put("ComboBox.actionMap", metalDefaults.get("ComboBox.actionMap"))
}

internal fun initInputMapDefaults(defaults: UIDefaults) {
  // Make ENTER work in JTrees
  val treeInputMap = defaults.get("Tree.focusInputMap") as InputMap?
  treeInputMap?.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "toggle")
  // Cut/Copy/Paste in JTextAreas
  val textAreaInputMap = defaults.get("TextArea.focusInputMap") as InputMap?
  if (textAreaInputMap != null) {
    // It really can be null, for example, when LAF isn't properly initialized (an Alloy license problem)
    installCutCopyPasteShortcuts(inputMap = textAreaInputMap, useSimpleActionKeys = false)
  }
  // Cut/Copy/Paste in JTextFields
  val textFieldInputMap = defaults.get("TextField.focusInputMap") as InputMap?
  if (textFieldInputMap != null) {
    // It really can be null, for example, when LAF isn't properly initialized (an Alloy license problem)
    installCutCopyPasteShortcuts(inputMap = textFieldInputMap, useSimpleActionKeys = false)
  }
  // Cut/Copy/Paste in JPasswordField
  val passwordFieldInputMap = defaults.get("PasswordField.focusInputMap") as InputMap?
  if (passwordFieldInputMap != null) {
    // It really can be null, for example, when LAF isn't properly initialized (an Alloy license problem)
    installCutCopyPasteShortcuts(inputMap = passwordFieldInputMap, useSimpleActionKeys = false)
  }
  // Cut/Copy/Paste in JTables
  val tableInputMap = defaults.get("Table.ancestorInputMap") as InputMap?
  if (tableInputMap != null) {
    // It really can be null, for example, when LAF isn't properly initialized (an Alloy license problem)
    installCutCopyPasteShortcuts(inputMap = tableInputMap, useSimpleActionKeys = true)
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

private fun toFont(defaults: UIDefaults, key: Any): Font {
  var value = defaults.get(key)
  if (value is Font) {
    return value
  }
  else if (value is UIDefaults.ActiveValue) {
    value = value.createValue(defaults)
    if (value is Font) {
      return value
    }
  }
  throw UnsupportedOperationException("Unable to extract Font from \"$key\"")
}

internal fun createRawDarculaTheme(): UITheme {
  val classLoader = LookAndFeelThemeAdapter::class.java.getClassLoader()
  val filename = "themes/darcula.theme.json"
  val data = ResourceUtil.getResourceAsBytes(filename, classLoader, /* checkParents */true)
             ?: throw RuntimeException("Can't load $filename")

  val theme = UITheme.loadDeprecatedFromJson(data = data, themeId = "Darcula", classLoader = classLoader)
  return theme
}

@Internal
@VisibleForTesting
fun setEarlyUiLaF() {
  // it is important to use class loader of a current instance class (LaF in plugin)
  val theme = createRawDarculaTheme()
  val laf = LookAndFeelThemeAdapter(createBaseLaF(), UIThemeLookAndFeelInfoImpl(theme))
  UIManager.setLookAndFeel(laf)
}

// used by Rider
@Internal
fun createBaseLaF(): LookAndFeel {
  if (SystemInfoRt.isMac) {
    val aClass = ClassLoader.getPlatformClassLoader().loadClass("com.apple.laf.AquaLookAndFeel")
    return MethodHandles.lookup().findConstructor(aClass, MethodType.methodType(Void.TYPE)).invoke() as BasicLookAndFeel
  }
  else if (!SystemInfoRt.isLinux || GraphicsEnvironment.isHeadless()) {
    return IdeaLaf(customFontDefaults = null)
  }

  // Normally, GTK LaF is considered "system" when (1) a GNOME session is active, and (2) GTK library is available.
  // Here, we weaken the requirements to only (2) and force GTK LaF installation to let it detect the system fonts
  // and scale them based on Xft.dpi value.
  try {
    val aClass = ClassLoader.getPlatformClassLoader().loadClass("com.sun.java.swing.plaf.gtk.GTKLookAndFeel")
    val gtk = MethodHandles.privateLookupIn(aClass, MethodHandles.lookup())
      .findConstructor(aClass, MethodType.methodType(Void.TYPE)).invoke() as LookAndFeel
    // GTK is available
    if (gtk.isSupportedLookAndFeel) {
      // on JBR 11, overrides `SunGraphicsEnvironment#uiScaleEnabled` (sets `#uiScaleEnabled_overridden` to `false`)
      gtk.initialize()
      val fontDefaults = HashMap<Any, Any?>()
      val gtkDefaults = gtk.defaults
      for (key in gtkDefaults.keys) {
        if (key.toString().endsWith(".font")) {
          // `UIDefaults#get` unwraps lazy values
          fontDefaults.put(key, gtkDefaults[key])
        }
      }
      @Suppress("UsePropertyAccessSyntax")
      return IdeaLaf(customFontDefaults = if (fontDefaults.isEmpty()) null else fontDefaults)
    }
  }
  catch (e: Exception) {
    logger<IdeaLaf>().warn(e)
  }
  return IdeaLaf(customFontDefaults = null)
}
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.ui.laf

import com.intellij.CommonBundle
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.runActivity
import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ide.IdeBundle
import com.intellij.ide.WelcomeWizardUtil
import com.intellij.ide.actions.QuickChangeLookAndFeel
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.ui.*
import com.intellij.ide.ui.UISettings.Companion.getPreferredFractionalMetricsValue
import com.intellij.ide.ui.UISettings.Companion.shadowInstance
import com.intellij.ide.ui.laf.SystemDarkThemeDetector.Companion.createDetector
import com.intellij.ide.ui.laf.darcula.DarculaInstaller
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo
import com.intellij.ide.ui.laf.intellij.IdeaPopupMenuUI
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import com.intellij.ui.*
import com.intellij.ui.components.DefaultLinkButtonUI
import com.intellij.ui.popup.HeavyWeightPopup
import com.intellij.ui.scale.JBUIScale.getFontScale
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.ui.scale.JBUIScale.scaleFontSize
import com.intellij.ui.scale.JBUIScale.setUserScaleFactor
import com.intellij.ui.tree.ui.DefaultTreeUI
import com.intellij.util.EventDispatcher
import com.intellij.util.FontUtil
import com.intellij.util.IJSwingUtilities
import com.intellij.util.ModalityUiUtil
import com.intellij.util.SVGLoader.colorPatcherProvider
import com.intellij.util.SVGLoader.setSelectionColorPatcherProvider
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.ui.*
import com.intellij.util.ui.LafIconLookup.getIcon
import com.intellij.util.ui.LafIconLookup.getSelectedIcon
import org.jdom.Element
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.*
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import javax.swing.*
import javax.swing.UIManager.LookAndFeelInfo
import javax.swing.plaf.FontUIResource
import javax.swing.plaf.UIResource
import javax.swing.plaf.metal.DefaultMetalTheme
import javax.swing.plaf.metal.MetalLookAndFeel

// A constant from Mac OS X implementation. See CPlatformWindow.WINDOW_ALPHA
private const val WINDOW_ALPHA = "Window.alpha"

private val LOG = logger<LafManagerImpl>()

private const val ELEMENT_LAF: @NonNls String = "laf"
private const val ELEMENT_PREFERRED_LIGHT_LAF: @NonNls String = "preferred-light-laf"
private const val ELEMENT_PREFERRED_DARK_LAF: @NonNls String = "preferred-dark-laf"
private const val ATTRIBUTE_AUTODETECT: @NonNls String = "autodetect"
private const val ATTRIBUTE_CLASS_NAME: @NonNls String = "class-name"
private const val ATTRIBUTE_THEME_NAME: @NonNls String = "themeId"
private const val HIGH_CONTRAST_THEME_ID = "JetBrainsHighContrastTheme"
private const val DARCULA_EDITOR_THEME_KEY = "Darcula.SavedEditorTheme"
private const val DEFAULT_EDITOR_THEME_KEY = "Default.SavedEditorTheme"
private const val INTER_NAME = "Inter"
private const val INTER_SIZE = 13

@Suppress("OVERRIDE_DEPRECATION")
@State(name = "LafManager",
       storages = [Storage(value = "laf.xml", usePathMacroManager = false)],
       category = SettingsCategory.UI,
       reportStatistic = false)
class LafManagerImpl : LafManager(), PersistentStateComponent<Element>, Disposable {
  private val eventDispatcher = EventDispatcher.create(LafManagerListener::class.java)
  private val lafMap = SynchronizedClearableLazy {
    runActivity("compute LaF list") {
      computeLafMap()
    }
  }
  private val lafList
    get() = lafMap.value.keys

  private val defaultDarkLaf = SynchronizedClearableLazy {
    val lafInfoFQN = ApplicationInfoEx.getInstanceEx().defaultDarkLaf
    (if (lafInfoFQN == null) null else createLafInfo(lafInfoFQN)) ?: DarculaLookAndFeelInfo()
  }

  private val ourDefaults: Map<Any, Any> = UIManager.getDefaults().clone() as UIDefaults
  private var myCurrentLaf: LookAndFeelInfo? = null
  private var preferredLightLaf: LookAndFeelInfo? = null
  private var preferredDarkLaf: LookAndFeelInfo? = null
  private val myStoredDefaults = HashMap<LafReference?, MutableMap<String, Any?>>()
  private val myLafComboBoxModel = SynchronizedClearableLazy<CollectionComboBoxModel<LafReference>> { LafComboBoxModel() }
  private val settingsToolbar: Lazy<ActionToolbar> = SynchronizedClearableLazy {
    val group = DefaultActionGroup(PreferredLafAction())
    val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, group, true)
    toolbar.targetComponent = toolbar.component
    toolbar.component.isOpaque = false
    toolbar
  }

  // SystemDarkThemeDetector must be created as part of LafManagerImpl initialization and not on demand because system listeners are added
  private var lafDetector: SystemDarkThemeDetector? = null
  private var isFirstSetup = true
  private var isUpdatingPlugin = false
  private var themeIdBeforePluginUpdate: String? = null
  private var autodetect = false

  val defaultLightLaf: LookAndFeelInfo?
    get() = UiThemeProviderListManager.getInstance().findJetBrainsLightTheme()

  fun getDefaultDarkLaf(): LookAndFeelInfo = defaultDarkLaf.value

  val defaultFont: Font
    get() {
      val result = when {
        useInterFont() -> defaultInterFont
        UISettings.getInstance().overrideLafFonts || UISettingsUtils.instance.currentIdeScale != 1f -> storedLafFont
        else -> null
      }
      return result ?: JBFont.label()
    }

  companion object {
    @Suppress("unused")
    var lafNameOrder: Map<String, Int>
      // allowing other plugins to change the order of the LaFs (used by Rider)
      get() = UiThemeProviderListManager.lafNameOrder
      set(value) {
        UiThemeProviderListManager.lafNameOrder = value
      }

    @JvmStatic
    @Suppress("SpellCheckingInspection")
    fun installMacOSXFonts(defaults: UIDefaults) {
      val face = "Helvetica Neue"
      // ui font
      StartupUiUtil.initFontDefaults(defaults, getFont(face, 13, Font.PLAIN))
      for (key in java.util.List.copyOf(defaults.keys)) {
        if (key !is String || !key.endsWith("font", ignoreCase = true)) {
          continue
        }

        val value = defaults.get(key)
        if (value is FontUIResource) {
          if (value.family == "Lucida Grande" || value.family == "Serif") {
            if (!key.toString().contains("Menu")) {
              defaults.put(key, getFont(face, value.size, value.style))
            }
          }
        }
      }
      defaults.put("TableHeader.font", getFont(face, 11, Font.PLAIN))
      @Suppress("SpellCheckingInspection") val buttonFont = getFont("Helvetica Neue", 13, Font.PLAIN)
      defaults.put("Button.font", buttonFont)
      val menuFont: Font = getFont("Lucida Grande", 13, Font.PLAIN)
      defaults.put("Menu.font", menuFont)
      defaults.put("MenuItem.font", menuFont)
      defaults.put("MenuItem.acceleratorFont", menuFont)
      defaults.put("PasswordField.font", defaults.getFont("TextField.font"))
    }

    private var ourTestInstance: LafManagerImpl? = null

    @TestOnly
    @JvmStatic
    fun getTestInstance(): LafManagerImpl? {
      if (ourTestInstance == null) {
        ourTestInstance = LafManagerImpl()
      }
      return ourTestInstance
    }
  }

  private fun computeLafMap(): SortedMap<LookAndFeelInfo, TargetUIType> {
    val map = TreeMap<LookAndFeelInfo, TargetUIType>(UiThemeProviderListManager.themesSortingComparator)
    map.put(defaultDarkLaf.value, TargetUIType.CLASSIC)
    if (!SystemInfoRt.isMac) {
      for (laf in UIManager.getInstalledLookAndFeels()) {
        val name = laf.name
        if (!"Metal".equals(name, ignoreCase = true)
            && !"CDE/Motif".equals(name, ignoreCase = true)
            && !"Nimbus".equals(name, ignoreCase = true)
            && !name.startsWith("Windows")
            && !"GTK+".equals(name, ignoreCase = true)
            && !name.startsWith("JGoodies")) {
          map.put(laf, TargetUIType.CLASSIC)
        }
      }
    }
    LafProvider.EP_NAME.forEachExtensionSafe { provider -> map.put(provider.lookAndFeelInfo, provider.targetUI) }
    map.putAll(UiThemeProviderListManager.getInstance().getLaFsWithUITypes())
    return map
  }

  @Suppress("removal")
  override fun addLafManagerListener(listener: LafManagerListener) {
    eventDispatcher.addListener(listener)
  }

  @Suppress("removal")
  override fun removeLafManagerListener(listener: LafManagerListener) {
    eventDispatcher.removeListener(listener)
  }

  override fun initializeComponent() {
    ModalityUiUtil.invokeLaterIfNeeded(ModalityState.any()) {
      val currentLaf = myCurrentLaf!!
      if (currentLaf is UIThemeBasedLookAndFeelInfo) {
        if (!currentLaf.isInitialised) {
          doSetLaF(currentLaf, false)
        }
      }
      else {
        val laf = findLaf(currentLaf.className)
        if (laf != null) {
          val needUninstall = StartupUiUtil.isUnderDarcula()
          // setup default LAF or one specified by readExternal
          doSetLaF(lookAndFeelInfo = laf, installEditorScheme = false)
          updateWizardLAF(needUninstall)
        }
      }
      selectComboboxModel()
      updateUI()
      // must be after updateUI
      isFirstSetup = false
      detectAndSyncLaf()
      runActivity("new ui configuration") {
        ExperimentalUI.getInstance().lookAndFeelChanged()
      }
      addThemeAndDynamicPluginListeners()
    }
  }

  private fun addThemeAndDynamicPluginListeners() {
    UIThemeProvider.EP_NAME.addExtensionPointListener(UiThemeEpListener(), this)
    ApplicationManager.getApplication().messageBus.connect(this)
      .subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
        override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
          isUpdatingPlugin = isUpdate
          themeIdBeforePluginUpdate = if (myCurrentLaf is UIThemeBasedLookAndFeelInfo) {
            (myCurrentLaf as UIThemeBasedLookAndFeelInfo).theme.id
          }
          else {
            null
          }
        }

        override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
          isUpdatingPlugin = false
          themeIdBeforePluginUpdate = null
        }
      })
  }

  private fun detectAndSyncLaf() {
    if (autodetect) {
      val lafDetector = orCreateLafDetector
      if (lafDetector.detectionSupported) {
        lafDetector.check()
      }
    }
  }

  private fun syncLaf(systemIsDark: Boolean) {
    if (!autodetect) {
      return
    }

    val currentIsDark = StartupUiUtil.isUnderDarcula() ||
                        (myCurrentLaf is UIThemeBasedLookAndFeelInfo && (myCurrentLaf as UIThemeBasedLookAndFeelInfo).theme.isDark)
    var expectedLaf: LookAndFeelInfo?
    if (systemIsDark) {
      expectedLaf = preferredDarkLaf
      if (expectedLaf == null && ExperimentalUI.isNewUI()) {
        expectedLaf = findLafByName("Dark")
      }
      if (expectedLaf == null) {
        expectedLaf = getDefaultDarkLaf()
      }
    }
    else {
      expectedLaf = preferredLightLaf
      if (expectedLaf == null && ExperimentalUI.isNewUI()) {
        expectedLaf = findLafByName("Light")
      }
      if (expectedLaf == null) {
        expectedLaf = defaultLightLaf
      }
    }
    if (currentIsDark != systemIsDark || myCurrentLaf !== expectedLaf) {
      QuickChangeLookAndFeel.switchLafAndUpdateUI(this, expectedLaf!!, true)
    }
  }

  fun updateWizardLAF(wasUnderDarcula: Boolean) {
    if (WelcomeWizardUtil.getWizardLAF() == null) {
      return
    }

    if (StartupUiUtil.isUnderDarcula()) {
      DarculaInstaller.install()
    }
    else if (wasUnderDarcula) {
      DarculaInstaller.uninstall()
    }
    WelcomeWizardUtil.setWizardLAF(null)
  }

  override fun dispose() {}

  override fun loadState(element: Element) {
    val oldLaF = myCurrentLaf
    myCurrentLaf = loadLafState(element, ELEMENT_LAF)
    if (myCurrentLaf == null) {
      myCurrentLaf = loadDefaultLaf()
    }
    autodetect = java.lang.Boolean.parseBoolean(element.getAttributeValue(ATTRIBUTE_AUTODETECT))
    preferredLightLaf = loadLafState(element, ELEMENT_PREFERRED_LIGHT_LAF)
    preferredDarkLaf = loadLafState(element, ELEMENT_PREFERRED_DARK_LAF)
    if (autodetect) {
      orCreateLafDetector
    }
    if (!isFirstSetup && myCurrentLaf != oldLaF) {
      QuickChangeLookAndFeel.switchLafAndUpdateUI(this, myCurrentLaf!!, true, true)
    }
  }

  private fun loadLafState(element: Element, attrName: @NonNls String): LookAndFeelInfo? {
    val lafElement = element.getChild(attrName) ?: return null
    return findLaf(lafElement.getAttributeValue(ATTRIBUTE_CLASS_NAME), lafElement.getAttributeValue(ATTRIBUTE_THEME_NAME))
  }

  private fun findLaf(suggestedLafClassName: String?, themeId: String?): LookAndFeelInfo? {
    var lafClassName = suggestedLafClassName
    if ("JetBrainsLightTheme" == themeId) {
      return defaultLightLaf
    }
    if (lafClassName != null && ourLafClassesAliases.containsKey(lafClassName)) {
      lafClassName = ourLafClassesAliases.get(lafClassName)
    }

    @Suppress("SpellCheckingInspection")
    if (lafClassName == "com.sun.java.swing.plaf.windows.WindowsLookAndFeel") {
      return defaultLightLaf
    }

    if (themeId != null) {
      for (l in lafList) {
        if (l is UIThemeBasedLookAndFeelInfo && l.theme.id == themeId) {
          return l
        }
      }
    }
    var laf: LookAndFeelInfo? = null
    if (lafClassName != null) {
      laf = findLaf(lafClassName)
    }

    if (laf == null && ("com.intellij.laf.win10.WinIntelliJLaf" == lafClassName || "com.intellij.laf.macos.MacIntelliJLaf" == lafClassName)) {
      return defaultLightLaf
    }
    else {
      return laf
    }
  }

  override fun noStateLoaded() {
    myCurrentLaf = loadDefaultLaf()
    preferredLightLaf = null
    preferredDarkLaf = null
    autodetect = false
  }

  override fun getState(): Element {
    val element = Element("state")
    element.setAttribute(ATTRIBUTE_AUTODETECT, java.lang.Boolean.toString(autodetect))
    getLafState(element, ELEMENT_LAF, currentLookAndFeel)
    if (preferredLightLaf != null && preferredLightLaf !== defaultLightLaf) {
      getLafState(element, ELEMENT_PREFERRED_LIGHT_LAF, preferredLightLaf)
    }
    if (preferredDarkLaf != null && preferredDarkLaf !== defaultDarkLaf.value) {
      getLafState(element, ELEMENT_PREFERRED_DARK_LAF, preferredDarkLaf)
    }
    return element
  }

  override fun getInstalledLookAndFeels(): Array<LookAndFeelInfo> = lafList.toTypedArray()

  override fun getLafComboBoxModel(): CollectionComboBoxModel<LafReference> = myLafComboBoxModel.value

  fun getLafListForTargetUI(targetUI: TargetUIType): List<LookAndFeelInfo> {
    return lafMap.value.filterValues { it == targetUI }.keys.toList()
  }

  private val allReferences: List<LafReference>
    get() {
      val result = ArrayList<LafReference>()
      for (group in ThemesListProvider.getInstance().getShownThemes()) {
        if (!result.isEmpty()) result.add(SEPARATOR)
        for (info in group) result.add(createLafReference(info))
      }
      return result
    }

  private fun updateLafComboboxModel() {
    myLafComboBoxModel.drop()
  }

  private fun selectComboboxModel() {
    if (myLafComboBoxModel.isInitialized()) {
      myLafComboBoxModel.value.selectedItem = createLafReference(myCurrentLaf)
    }
  }

  override fun findLaf(reference: LafReference): LookAndFeelInfo = findLaf(reference.className, reference.themeId)!!

  override fun getCurrentLookAndFeel(): LookAndFeelInfo? = myCurrentLaf

  override fun getLookAndFeelReference(): LafReference = createLafReference(currentLookAndFeel)

  override fun getLookAndFeelCellRenderer(): ListCellRenderer<LafReference> = LafCellRenderer()

  override fun getSettingsToolbar(): JComponent = settingsToolbar.value.component

  private fun loadDefaultLaf(): LookAndFeelInfo {
    val wizardLafName = WelcomeWizardUtil.getWizardLAF()
    if (wizardLafName != null) {
      val laf = findLaf(wizardLafName)
      if (laf != null) {
        return laf
      }
      LOG.error("Could not find wizard L&F: $wizardLafName")
    }
    if (SystemInfoRt.isMac) {
      val className = DarculaLaf::class.java.name
      val laf = findLaf(className)
      if (laf != null) {
        return laf
      }
      LOG.error("Could not find OS X L&F: $className")
    }

    // Use HighContrast theme for IDE in Windows if HighContrast desktop mode is set.
    if (SystemInfoRt.isWindows && Toolkit.getDefaultToolkit().getDesktopProperty("win.highContrast.on") == true) {
      for (laf in lafList) {
        if (laf is UIThemeBasedLookAndFeelInfo && HIGH_CONTRAST_THEME_ID == laf.theme.id) {
          return laf
        }
      }
    }

    val defaultLafName = DarculaLaf::class.java.name
    return findLaf(defaultLafName) ?: throw IllegalStateException("No default L&F found: $defaultLafName")
  }

  private fun findLafByName(name: String): LookAndFeelInfo? = installedLookAndFeels.firstOrNull { name == it.name }

  private fun findLaf(className: String): LookAndFeelInfo? {
    val defaultLightLaf = defaultLightLaf
    return when {
      defaultLightLaf!!.className == className -> defaultLightLaf
      defaultDarkLaf.value.className == className -> defaultDarkLaf.value
      else -> {
        for (l in lafList) {
          if (l !is UIThemeBasedLookAndFeelInfo && className == l.className) {
            return l
          }
        }
        null
      }
    }
  }

  /**
   * Sets current LAF. The method doesn't update component hierarchy.
   */
  override fun setCurrentLookAndFeel(lookAndFeelInfo: LookAndFeelInfo, lockEditorScheme: Boolean) {
    setLookAndFeelImpl(lookAndFeelInfo, !lockEditorScheme, true)
  }

  /**
   * Sets current LAF. The method doesn't update component hierarchy.
   */
  private fun setLookAndFeelImpl(lookAndFeelInfo: LookAndFeelInfo, installEditorScheme: Boolean, processChangeSynchronously: Boolean) {
    val oldLaf = myCurrentLaf
    if (oldLaf !== lookAndFeelInfo && oldLaf is UIThemeBasedLookAndFeelInfo) {
      oldLaf.dispose()
    }
    if (findLaf(lookAndFeelInfo.className) == null) {
      LOG.error("unknown LookAndFeel : $lookAndFeelInfo")
      return
    }
    if (doSetLaF(lookAndFeelInfo, installEditorScheme)) {
      return
    }
    myCurrentLaf = lookAndFeelInfo
    selectComboboxModel()
    if (!isFirstSetup && installEditorScheme) {
      if (processChangeSynchronously) {
        updateEditorSchemeIfNecessary(oldLaf, true)
        shadowInstance.fireUISettingsChanged()
        ActionToolbarImpl.updateAllToolbarsImmediately()
      }
      else {
        ApplicationManager.getApplication().invokeLater {
          updateEditorSchemeIfNecessary(oldLaf, false)
          shadowInstance.fireUISettingsChanged()
          ActionToolbarImpl.updateAllToolbarsImmediately()
        }
      }
    }
    isFirstSetup = false
  }

  private fun doSetLaF(lookAndFeelInfo: LookAndFeelInfo, installEditorScheme: Boolean): Boolean {
    val defaults = UIManager.getDefaults()
    defaults.clear()
    defaults.putAll(ourDefaults)
    if (!isFirstSetup) {
      colorPatcherProvider = null
      setSelectionColorPatcherProvider(null)
    }

    // set L&F
    val lafClassName = lookAndFeelInfo.className
    if (DarculaLookAndFeelInfo.CLASS_NAME == lafClassName) {
      val laf = DarculaLaf()
      try {
        UIManager.setLookAndFeel(laf)
        AppUIUtil.updateForDarcula(true)
        //if (lafNameOrder.containsKey(lookAndFeelInfo.getName())) {
        //  updateIconsUnderSelection(true);
        //}
      }
      catch (e: Exception) {
        LOG.error(e)
        Messages.showMessageDialog(
          IdeBundle.message("error.cannot.set.look.and.feel", lookAndFeelInfo.name, e.message),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        )
        return true
      }
    }
    else {
      // non default LAF
      try {
        val laf: LookAndFeel
        if (lookAndFeelInfo is PluggableLafInfo) {
          laf = lookAndFeelInfo.createLookAndFeel()
        }
        else {
          laf = LafManagerImpl::class.java.classLoader.loadClass(lafClassName).getConstructor().newInstance() as LookAndFeel
          // avoid loading MetalLookAndFeel class here - check for UIThemeBasedLookAndFeelInfo first
          if (lookAndFeelInfo is UIThemeBasedLookAndFeelInfo) {
            if (laf is UserDataHolder) {
              (laf as UserDataHolder).putUserData(UIUtil.LAF_WITH_THEME_KEY, true)
            }
            //if (lafNameOrder.containsKey(lookAndFeelInfo.getName()) && lookAndFeelInfo.getName().endsWith("Light")) {
            //  updateIconsUnderSelection(false);
            //}
          }
          else if (laf is MetalLookAndFeel) {
            MetalLookAndFeel.setCurrentTheme(DefaultMetalTheme())
          }
        }
        UIManager.setLookAndFeel(laf)
      }
      catch (e: Exception) {
        LOG.error(e)
        Messages.showMessageDialog(
          IdeBundle.message("error.cannot.set.look.and.feel", lookAndFeelInfo.name, e.message),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        )
        return true
      }
    }
    if (lookAndFeelInfo is UIThemeBasedLookAndFeelInfo) {
      try {
        lookAndFeelInfo.installTheme(UIManager.getLookAndFeelDefaults(), !installEditorScheme)

        //IntelliJ Light is the only theme which is, in fact, a LaF.
        if (lookAndFeelInfo.name != "IntelliJ Light") {
          defaults.put("Theme.name", lookAndFeelInfo.name)
        }
      }
      catch (e: Exception) {
        LOG.error(e)
        Messages.showMessageDialog(
          IdeBundle.message("error.cannot.set.look.and.feel", lookAndFeelInfo.getName(), e.message),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        )
        return true
      }
    }
    if (SystemInfoRt.isMac) {
      installMacOSXFonts(UIManager.getLookAndFeelDefaults())
    }
    else if (SystemInfoRt.isLinux) {
      installLinuxFonts(UIManager.getLookAndFeelDefaults())
    }
    updateColors(defaults)
    return false
  }

  override fun applyDensity() {
    val settingsUtils = UISettingsUtils.instance
    val ideScale = settingsUtils.currentIdeScale

    settingsUtils.setCurrentIdeScale(1f) // need to temporarily reset this to correctly apply new size values
    UISettings.getInstance().fireUISettingsChanged()
    setCurrentLookAndFeel(currentLookAndFeel!!, true)
    updateUI()
    settingsUtils.setCurrentIdeScale(ideScale)
    UISettings.getInstance().fireUISettingsChanged()
  }

  private fun applyDensity(defaults: UIDefaults) {
    val densityKey = "ui.density"
    val oldDensityName = defaults.get(densityKey) as? String
    val newDensity = UISettings.getInstance().uiDensity
    if (oldDensityName == newDensity.name) {
      return // re-applying the same density would break HiDPI-scalable values like Tree.rowHeight
    }
    defaults.put(densityKey, newDensity.name)
    if (newDensity == UIDensity.DEFAULT) {
      // Special case: we need to set this one to its default value even in non-compact mode, UNLESS it was already set by the theme.
      // If it's null, it can't be properly patched in patchRowHeight, which looks ugly with larger UI fonts.
      val vcsLogHeight = defaults.get(JBUI.CurrentTheme.VersionControl.Log.rowHeightKey())
      // don't want to rely on putIfAbsent here, as UIDefaults is a rather messy combination of multiple hash tables
      if (vcsLogHeight == null) {
        defaults.put(JBUI.CurrentTheme.VersionControl.Log.rowHeightKey(), JBUI.CurrentTheme.VersionControl.Log.defaultRowHeight())
      }
    }
    if (newDensity == UIDensity.COMPACT) {
      // main toolbar
      defaults.put(JBUI.CurrentTheme.Toolbar.experimentalToolbarButtonSizeKey(), cmSize(34, 34))
      defaults.put(JBUI.CurrentTheme.Toolbar.experimentalToolbarButtonIconSizeKey(), 16)
      defaults.put(JBUI.CurrentTheme.Toolbar.experimentalToolbarFontKey(), Supplier { JBFont.medium() })
      defaults.put(JBUI.CurrentTheme.TitlePane.buttonPreferredSizeKey(), cmSize(44, 34))
      // tool window stripes
      defaults.put(JBUI.CurrentTheme.Toolbar.stripeToolbarButtonSizeKey(), cmSize(32, 32))
      defaults.put(JBUI.CurrentTheme.Toolbar.stripeToolbarButtonIconSizeKey(), 16)
      defaults.put(JBUI.CurrentTheme.Toolbar.stripeToolbarButtonIconPaddingKey(), cmInsets(4))
      // Run Widget
      defaults.put(JBUI.CurrentTheme.RunWidget.toolbarHeightKey(), 26)
      defaults.put(JBUI.CurrentTheme.RunWidget.toolbarBorderHeightKey(), 4)
      defaults.put(JBUI.CurrentTheme.RunWidget.configurationSelectorFontKey(), Supplier { JBFont.medium() })
      // trees
      defaults.put(JBUI.CurrentTheme.Tree.rowHeightKey(), 22)
      // lists
      defaults.put("List.rowHeight", 22)
      // popups
      defaults.put(JBUI.CurrentTheme.Popup.headerInsetsKey(), cmInsets(8, 10, 8, 10))
      defaults.put(JBUI.CurrentTheme.Advertiser.borderInsetsKey(), cmInsets(4, 20, 5, 20))
      defaults.put(JBUI.CurrentTheme.CompletionPopup.selectionInnerInsetsKey(), cmInsets(0, 2, 0, 2))
      // status bar
      defaults.put(JBUI.CurrentTheme.StatusBar.Widget.insetsKey(), cmInsets(4, 8, 3, 8))
      defaults.put(JBUI.CurrentTheme.StatusBar.Breadcrumbs.navBarInsetsKey(), cmInsets(1, 0, 1, 4))
      defaults.put(JBUI.CurrentTheme.StatusBar.fontKey(), Supplier { JBFont.medium() })
      // separate navbar
      defaults.put(JBUI.CurrentTheme.NavBar.itemInsetsKey(), cmInsets(2))
      // editor tabs
      defaults.put("EditorTabs.tabInsets", cmInsets(1, 4, 2, 4))
      defaults.put(JBUI.CurrentTheme.EditorTabs.tabActionsInsetKey(), 0)
      defaults.put(JBUI.CurrentTheme.EditorTabs.fontKey(), Supplier { JBFont.medium() })
      // toolwindows
      defaults.put(JBUI.CurrentTheme.ToolWindow.headerHeightKey(), 32)
      defaults.put(JBUI.CurrentTheme.ToolWindow.headerFontKey(), Supplier { JBFont.medium() })
      // run, debug tabs
      defaults.put(JBUI.CurrentTheme.DebuggerTabs.tabHeightKey(), 32)
      defaults.put(JBUI.CurrentTheme.DebuggerTabs.fontKey(), Supplier { JBFont.medium() })
      // VCS log
      defaults.put(JBUI.CurrentTheme.VersionControl.Log.rowHeightKey(), 24)
      defaults.put(JBUI.CurrentTheme.VersionControl.Log.verticalPaddingKey(), 4)
    }
  }
  
  private fun cmSize(width: Int, height: Int): Dimension = Dimension(width, height)

  @Suppress("UseDPIAwareInsets")
  private fun cmInsets(all: Int): Insets = Insets(all, all, all, all)

  @Suppress("UseDPIAwareInsets")
  private fun cmInsets(top: Int, left: Int, bottom: Int, right: Int): Insets = Insets(top, left, bottom, right)

  private fun updateEditorSchemeIfNecessary(oldLaf: LookAndFeelInfo?, processChangeSynchronously: Boolean) {
    if (oldLaf is TempUIThemeBasedLookAndFeelInfo || myCurrentLaf is TempUIThemeBasedLookAndFeelInfo) {
      return
    }
    if (myCurrentLaf is UIThemeBasedLookAndFeelInfo) {
      if ((myCurrentLaf as UIThemeBasedLookAndFeelInfo).theme.editorSchemeName != null) {
        return
      }
    }
    val dark = StartupUiUtil.isUnderDarcula()
    val editorColorManager = EditorColorsManager.getInstance()
    val current = editorColorManager.globalScheme
    val wasUITheme = oldLaf is UIThemeBasedLookAndFeelInfo
    if (dark != ColorUtil.isDark(current.defaultBackground) || wasUITheme) {
      var targetScheme: String? = if (dark) DarculaLaf.NAME else EditorColorsScheme.DEFAULT_SCHEME_NAME
      val properties = PropertiesComponent.getInstance()
      val savedEditorThemeKey = if (dark) DARCULA_EDITOR_THEME_KEY else DEFAULT_EDITOR_THEME_KEY
      val toSavedEditorThemeKey = if (dark) DEFAULT_EDITOR_THEME_KEY else DARCULA_EDITOR_THEME_KEY
      val themeName = properties.getValue(savedEditorThemeKey)
      if (themeName != null && editorColorManager.getScheme(themeName) != null) {
        targetScheme = themeName
      }
      if (!wasUITheme) {
        properties.setValue(toSavedEditorThemeKey, current.name, if (dark) EditorColorsScheme.DEFAULT_SCHEME_NAME else DarculaLaf.NAME)
      }
      val scheme = editorColorManager.getScheme(targetScheme!!)
      if (scheme != null) {
        (editorColorManager as EditorColorsManagerImpl).setGlobalScheme(scheme, processChangeSynchronously)
      }
    }
  }

  /**
   * Updates LAF of all windows. The method also updates font of components
   * as it's configured in `UISettings`.
   */
  override fun updateUI() {
    val uiDefaults = UIManager.getLookAndFeelDefaults()
    uiDefaults.put("LinkButtonUI", DefaultLinkButtonUI::class.java.name)
    fixPopupWeight()
    fixMenuIssues(uiDefaults)
    StartupUiUtil.initInputMapDefaults(uiDefaults)
    uiDefaults.put("Button.defaultButtonFollowsFocus", false)
    uiDefaults.put("Balloon.error.textInsets", JBInsets(3, 8, 3, 8).asUIResource())
    patchFileChooserStrings(uiDefaults)
    patchLafFonts(uiDefaults)
    patchTreeUI(uiDefaults)
    if (ExperimentalUI.isNewUI()) {
      applyDensity(uiDefaults)
    }

    //should be called last because this method modifies uiDefault values
    patchHiDPI(uiDefaults)
    // required for MigLayout logical pixels to work
    // super-huge DPI causes issues like IDEA-170295 if `laf.scaleFactor` property is missing
    uiDefaults.put("laf.scaleFactor", scale(1f))
    uiDefaults.put(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false))
    uiDefaults.put(RenderingHints.KEY_TEXT_LCD_CONTRAST, UIUtil.getLcdContrastValue())
    uiDefaults.put(RenderingHints.KEY_FRACTIONALMETRICS, AppUIUtil.adjustFractionalMetrics(getPreferredFractionalMetricsValue()))
    if (isFirstSetup) {
      ApplicationManager.getApplication().invokeLater { notifyLookAndFeelChanged() }
    }
    else {
      ExperimentalUI.getInstance().lookAndFeelChanged()
      notifyLookAndFeelChanged()
      for (frame in Frame.getFrames()) {
        updateUI(frame)
      }
    }
  }

  private fun notifyLookAndFeelChanged() {
    val activity = StartUpMeasurer.startActivity("lookAndFeelChanged event processing")
    ApplicationManager.getApplication().messageBus.syncPublisher(LafManagerListener.TOPIC).lookAndFeelChanged(this)
    eventDispatcher.multicaster.lookAndFeelChanged(this)
    activity.end()
  }

  private fun patchLafFonts(uiDefaults: UIDefaults) {
    val uiSettings = UISettings.getInstance()
    val currentScale = UISettingsUtils.with(uiSettings).currentIdeScale
    if (uiSettings.overrideLafFonts || currentScale != 1f) {
      storeOriginalFontDefaults(uiDefaults)
      val fontFace = if (uiSettings.overrideLafFonts) uiSettings.fontFace else defaultFont.family
      val fontSize = (if (uiSettings.overrideLafFonts) uiSettings.fontSize2D else defaultFont.size2D).let{
        it * currentScale
      }
      StartupUiUtil.initFontDefaults(uiDefaults, StartupUiUtil.getFontWithFallback(fontFace, Font.PLAIN, fontSize))
      val userScaleFactor = if (useInterFont()) fontSize / INTER_SIZE else getFontScale(fontSize)
      setUserScaleFactor(userScaleFactor)
    }
    else if (useInterFont()) {
      storeOriginalFontDefaults(uiDefaults)
      StartupUiUtil.initFontDefaults(uiDefaults, defaultInterFont)
      setUserScaleFactor(defaultUserScaleFactor)
    }
    else {
      restoreOriginalFontDefaults(uiDefaults)
    }
  }

  private val defaultUserScaleFactor: Float
    get() {
      var font = storedLafFont
      if (font == null) {
        font = JBFont.label()
      }
      return getFontScale(font.size.toFloat())
    }
  private val defaultInterFont: FontUIResource
    get() {
      val userScaleFactor = defaultUserScaleFactor
      return StartupUiUtil.getFontWithFallback(INTER_NAME, Font.PLAIN, scaleFontSize(INTER_SIZE.toFloat(), userScaleFactor).toFloat())
    }
  private val storedLafFont: Font?
    get() {
      val lf = if (myCurrentLaf == null) null else lookAndFeelReference
      val lfDefaults: Map<String, Any?>? = myStoredDefaults[lf]
      return if (lfDefaults == null) null else lfDefaults["Label.font"] as Font?
    }

  private fun restoreOriginalFontDefaults(defaults: UIDefaults) {
    val laf = if (myCurrentLaf == null) null else lookAndFeelReference
    val lafDefaults = myStoredDefaults.get(laf)
    if (lafDefaults != null) {
      for (resource in StartupUiUtil.ourPatchableFontResources) {
        defaults.put(resource, lafDefaults.get(resource))
      }
    }
    setUserScaleFactor(getFontScale(JBFont.label().size.toFloat()))
  }

  private fun storeOriginalFontDefaults(defaults: UIDefaults) {
    val laf = if (myCurrentLaf == null) null else lookAndFeelReference
    var lafDefaults = myStoredDefaults.get(laf)
    if (lafDefaults == null) {
      lafDefaults = HashMap()
      for (resource in StartupUiUtil.ourPatchableFontResources) {
        lafDefaults.put(resource, defaults.get(resource))
      }
      myStoredDefaults.put(laf, lafDefaults)
    }
  }

  /**
   * Repaints all displayable window.
   */
  override fun repaintUI() {
    val frames = Frame.getFrames()
    for (frame in frames) {
      repaintUI(frame)
    }
  }

  override fun getAutodetect(): Boolean {
    return autodetect
  }

  override fun setAutodetect(value: Boolean) {
    if (autodetect == value) {
      return
    }

    autodetect = value

    // Notify autodetect is changed
    notifyLookAndFeelChanged()

    if (autodetect) {
      detectAndSyncLaf()
    }
    else if (ExperimentalUI.isNewUI()) {
      if ("Light" == myCurrentLaf!!.name && myCurrentLaf === preferredLightLaf) {
        preferredLightLaf = null
      }
      else if ("Dark" == myCurrentLaf!!.name && myCurrentLaf === preferredDarkLaf) {
        preferredDarkLaf = null
      }
    }
  }

  override fun getAutodetectSupported(): Boolean {
    return orCreateLafDetector.detectionSupported
  }

  private val orCreateLafDetector: SystemDarkThemeDetector
    get() {
      var result = lafDetector
      if (result == null) {
        result = createDetector { systemIsDark: Boolean -> syncLaf(systemIsDark) }
        lafDetector = result
      }

      return result
    }

  override fun setPreferredDarkLaf(value: LookAndFeelInfo) {
    preferredDarkLaf = value
  }

  override fun setPreferredLightLaf(value: LookAndFeelInfo) {
    preferredLightLaf = value
  }

  private inner class UiThemeEpListener : ExtensionPointListener<UIThemeProvider> {
    override fun extensionAdded(extension: UIThemeProvider, pluginDescriptor: PluginDescriptor) {
      val newLaF = UiThemeProviderListManager.getInstance().themeProviderAdded(extension) ?: return
      val oldLaFsMap = lafMap.value
      val newLaFsMap = TreeMap(oldLaFsMap)
      newLaFsMap.put(newLaF, extension.targetUI)
      lafMap.value = newLaFsMap
      updateLafComboboxModel()

      // when updating a theme plugin that doesn't provide the current theme, don't select any of its themes as current
      val newTheme = newLaF.theme
      val pluginClassLoader = pluginDescriptor.pluginClassLoader
      if (pluginClassLoader != null) {
        newTheme.setProviderClassLoader(pluginClassLoader)
      }
      if (!autodetect && (!isUpdatingPlugin || newTheme.id == themeIdBeforePluginUpdate)) {
        setLookAndFeelImpl(newLaF, true, false)
        JBColor.setDark(newTheme.isDark)
        updateUI()
      }
    }

    override fun extensionRemoved(extension: UIThemeProvider, pluginDescriptor: PluginDescriptor) {
      val oldLaF = UiThemeProviderListManager.getInstance().themeProviderRemoved(extension) ?: return
      val oldTheme = oldLaF.theme
      oldTheme.setProviderClassLoader(null)
      val isDark = oldTheme.isDark
      val defaultLaF = if (oldLaF === currentLookAndFeel) {
        if (isDark) defaultDarkLaf.value else defaultLightLaf
      }
      else {
        null
      }
      val newLaFs = TreeMap<LookAndFeelInfo, TargetUIType>(lafMap.value.comparator())
      for (laf in lafMap.value) {
        if (laf.key !== oldLaF) {
          newLaFs.put(laf.key, laf.value)
        }
      }
      lafMap.value = newLaFs
      updateLafComboboxModel()
      if (defaultLaF != null) {
        setLookAndFeelImpl(defaultLaF, true, true)
        JBColor.setDark(defaultDarkLaf.isInitialized() && isDark)
        updateUI()
      }
    }
  }

  private class LafCellRenderer : SimpleListCellRenderer<LafReference>() {
    companion object {
      private val separator: SeparatorWithText = object : SeparatorWithText() {
        override fun paintComponent(g: Graphics) {
          g.color = foreground
          val bounds = Rectangle(width, height)
          JBInsets.removeFrom(bounds, insets)
          paintLine(g, bounds.x, bounds.y + bounds.height / 2, bounds.width)
        }
      }
    }

    override fun getListCellRendererComponent(list: JList<out LafReference>,
                                              value: LafReference,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      return if (value === SEPARATOR) separator else super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
    }

    override fun customize(list: JList<out LafReference>, value: LafReference, index: Int, selected: Boolean, hasFocus: Boolean) {
      text = value.toString()
    }
  }

  private inner class LafComboBoxModel : CollectionComboBoxModel<LafReference>(allReferences) {
    override fun setSelectedItem(item: Any?) {
      if (item !== SEPARATOR) {
        super.setSelectedItem(item)
      }
    }
  }

  private inner class PreferredLafAction : DefaultActionGroup(), DumbAware {
    init {
      isPopup = true
      templatePresentation.icon = AllIcons.General.GearPlain
      templatePresentation.text = IdeBundle.message("preferred.theme.text")
      templatePresentation.description = IdeBundle.message("preferred.theme.description")
      templatePresentation.isPerformGroup = true
    }

    override fun actionPerformed(e: AnActionEvent) {
      val popup = JBPopupFactory.getInstance().createActionGroupPopup(IdeBundle.message("preferred.theme.text"), lafGroups, e.dataContext,
                                                                      true, null, Int.MAX_VALUE)
      HelpTooltip.setMasterPopup(e.inputEvent.component, popup)
      val component = e.inputEvent.component
      if (component is ActionButtonComponent) {
        popup.showUnderneathOf(component)
      }
      else {
        popup.showInCenterOf(component)
      }
    }

    private val lafGroups: ActionGroup
      get() {
        val allLaFs =
          if (ExperimentalUI.isNewUI()) getLafListForTargetUI(TargetUIType.NEW) + getLafListForTargetUI(TargetUIType.CLASSIC)
          else getLafListForTargetUI(TargetUIType.CLASSIC)

        val lightLaFs = ArrayList<LookAndFeelInfo>()
        val darkLaFs = ArrayList<LookAndFeelInfo>()
        for (lafInfo in allLaFs) {
          if (lafInfo is UIThemeBasedLookAndFeelInfo && lafInfo.theme.isDark || lafInfo.name == DarculaLaf.NAME) {
            darkLaFs.add(lafInfo)
          }
          else {
            lightLaFs.add(lafInfo)
          }
        }

        val group = DefaultActionGroup()
        group.addAll(createThemeActions(IdeBundle.message("preferred.theme.light.header"), lightLaFs, isDark = false))
        group.addAll(createThemeActions(IdeBundle.message("preferred.theme.dark.header"), darkLaFs, isDark = true))
        return group
      }

    private fun createThemeActions(separatorText: @NlsContexts.Separator String,
                                   lafs: List<LookAndFeelInfo>,
                                   isDark: Boolean): Collection<AnAction> {
      if (lafs.isEmpty()) {
        return emptyList()
      }

      val result = ArrayList<AnAction>()
      result.add(Separator.create(separatorText))
      lafs.mapTo(result) { LafToggleAction(it.name, it, isDark) }
      return result
    }
  }

  private inner class LafToggleAction(name: @Nls String?,
                                      private val lafInfo: LookAndFeelInfo,
                                      private val isDark: Boolean) : ToggleAction(name) {
    override fun isSelected(e: AnActionEvent): Boolean {
      return if (isDark) lafInfo === preferredDarkLaf else lafInfo === preferredLightLaf
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (isDark) {
        if (preferredDarkLaf !== lafInfo) {
          preferredDarkLaf = lafInfo
          detectAndSyncLaf()
        }
      }
      else {
        if (preferredLightLaf !== lafInfo) {
          preferredLightLaf = lafInfo
          detectAndSyncLaf()
        }
      }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun isDumbAware(): Boolean = true
  }

  open class IJColor internal constructor(color: Color?, private val name: String) : JBColor(Supplier { color }) {
    override fun getName(): String = name

    override fun toString(): String = "${super.toString()} Name: $name"
  }

  class IJColorUIResource internal constructor(color: Color?, name: String) : IJColor(color, name), UIResource
}

private val SEPARATOR = LafManager.LafReference("", null, null)
private val fileChooserTextKeys = arrayOf(
  "FileChooser.viewMenuLabelText", "FileChooser.newFolderActionLabelText",
  "FileChooser.listViewActionLabelText", "FileChooser.detailsViewActionLabelText", "FileChooser.refreshActionLabelText"
)

private val ourLafClassesAliases = java.util.Map.of("idea.dark.laf.classname", DarculaLookAndFeelInfo.CLASS_NAME)

private object DefaultMenuArrowIcon : MenuArrowIcon(
  icon = { AllIcons.Icons.Ide.MenuArrow },
  selectedIcon = { if (DefaultMenuArrowIcon.dark.asBoolean) AllIcons.Icons.Ide.MenuArrowSelected else AllIcons.Icons.Ide.MenuArrow },
  disabledIcon = { IconLoader.getDisabledIcon(AllIcons.Icons.Ide.MenuArrow) },
) {
  private val dark = BooleanSupplier { ColorUtil.isDark(UIManager.getColor("MenuItem.selectionBackground")) }
}

private fun fixMenuIssues(uiDefaults: UIDefaults) {
  uiDefaults.put("Menu.arrowIcon", DefaultMenuArrowIcon)
  uiDefaults.put("MenuItem.background", UIManager.getColor("Menu.background"))
}

private class OurPopupFactory(private val delegate: PopupFactory) : PopupFactory() {
  companion object {
    const val WEIGHT_LIGHT = 0
    const val WEIGHT_MEDIUM = 1
    const val WEIGHT_HEAVY = 2
    private fun fixPopupLocation(contents: Component, x: Int, y: Int): Point {
      @Suppress("NAME_SHADOWING") var y = y
      if (contents !is JToolTip) {
        if (IdeaPopupMenuUI.isUnderPopup(contents)) {
          val topBorder = JBUI.insets("PopupMenu.borderInsets", JBInsets.emptyInsets()).top
          val invoker = (contents as JPopupMenu).invoker
          if (invoker is ActionMenu) {
            y -= topBorder / 2
            if (SystemInfoRt.isMac) {
              y += JBUI.scale(1)
            }
          }
          else {
            y -= topBorder
            y -= JBUI.scale(1)
          }
        }
        return Point(x, y)
      }
      val info = try {
        MouseInfo.getPointerInfo()
      }
      catch (e: InternalError) {
        // http://www.jetbrains.net/jira/browse/IDEADEV-21390
        // may happen under Mac OSX 10.5
        return Point(x, y)
      }
      var deltaY = 0
      if (info != null) {
        val mouse = info.location
        deltaY = mouse.y - y
      }
      val size = contents.getPreferredSize()
      val rec = Rectangle(Point(x, y), size)
      ScreenUtil.moveRectangleToFitTheScreen(rec)
      if (rec.y < y) {
        rec.y += deltaY
      }
      return rec.location
    }
  }

  override fun getPopup(owner: Component, contents: Component, x: Int, y: Int): Popup {
    val point = fixPopupLocation(contents, x, y)
    val popupType = PopupUtil.getPopupType(this)
    if (popupType >= 0) {
      PopupUtil.setPopupType(delegate, popupType)
    }
    var popup = delegate.getPopup(owner, contents, point.x, point.y)
    val window = ComponentUtil.getWindow(contents)
    val isHeavyWeightPopup = window is RootPaneContainer && window !== ComponentUtil.getWindow(owner)
    if (isHeavyWeightPopup) {
      popup = HeavyWeightPopup(popup, window!!) // disable popup caching by runtime
      val rootPane = (window as RootPaneContainer?)!!.rootPane
      rootPane.glassPane = IdeGlassPaneImpl(rootPane, false)
      rootPane.putClientProperty(WINDOW_ALPHA, 1.0f)
      window.addWindowListener(object : WindowAdapter() {
        override fun windowOpened(e: WindowEvent) {
          // cleanup will be handled by AbstractPopup wrapper
          if (PopupUtil.getPopupContainerFor(rootPane) != null) {
            window.removeWindowListener(this)
          }
        }

        override fun windowClosed(e: WindowEvent) {
          window.removeWindowListener(this)
          DialogWrapper.cleanupRootPane(rootPane)
          DialogWrapper.cleanupWindowListeners(window)
        }
      })
      if (IdeaPopupMenuUI.isUnderPopup(contents) && WindowRoundedCornersManager.isAvailable()) {
        if ((SystemInfoRt.isMac && UIUtil.isUnderDarcula()) || SystemInfoRt.isWindows) {
          WindowRoundedCornersManager.setRoundedCorners(window, JBUI.CurrentTheme.Popup.borderColor(true))
        }
        else {
          WindowRoundedCornersManager.setRoundedCorners(window)
        }
        if (SystemInfoRt.isMac) {
          val contentPane = (window as RootPaneContainer?)!!.contentPane as JComponent
          contentPane.isOpaque = true
          contentPane.background = contents.background
        }
      }
    }
    return popup
  }
}

private fun createLafInfo(fqn: String): LookAndFeelInfo? {
  try {
    val lafInfoClass = Class.forName(fqn)
    return lafInfoClass.getDeclaredConstructor().newInstance() as LookAndFeelInfo
  }
  catch (e: Throwable) {
    return null
  }
}

private fun getLafState(element: Element, attributeName: @NonNls String?, suggestedLaf: LookAndFeelInfo?) {
  var laf = suggestedLaf
  if (laf is TempUIThemeBasedLookAndFeelInfo) {
    laf = laf.previousLaf
  }
  if (laf == null) {
    return
  }
  val className = laf.className
  if (className != null) {
    val child = Element(attributeName)
    child.setAttribute(ATTRIBUTE_CLASS_NAME, className)
    if (laf is UIThemeBasedLookAndFeelInfo) {
      child.setAttribute(ATTRIBUTE_THEME_NAME, laf.theme.id)
    }
    element.addContent(child)
  }
}

private fun createLafReference(laf: LookAndFeelInfo?): LafManager.LafReference {
  var themeId: String? = null
  if (laf is UIThemeBasedLookAndFeelInfo) {
    themeId = laf.theme.id
  }
  return LafManager.LafReference(laf!!.name, laf.className, themeId)
}

private fun updateColors(defaults: UIDefaults) {
  // MultiUIDefaults doesn't override keySet() in JDK 11 (JDK 17 is ok) and returns set of UIDefaults,
  // but not expected UI pairs with key/value. So don't use it
  for (entry in defaults.entries) {
    val value = entry.value
    if (value is Color && !(value is JBColor && value.name != null)) {
      entry.setValue(wrapColorToNamedColor(value, entry.key.toString()))
    }
  }
}

private fun wrapColorToNamedColor(color: Color, key: String): Color {
  return if (color is UIResource) LafManagerImpl.IJColorUIResource(color, key) else LafManagerImpl.IJColor(color, key)
}

private fun getFont(yosemite: String, size: Int, style: Int): FontUIResource {
  if (SystemInfoRt.isMac) {
    // Text family should be used for relatively small sizes (<20pt), don't change to Display
    // see more about SF https://medium.com/@mach/the-secret-of-san-francisco-fonts-4b5295d9a745#.2ndr50z2v
    val font = FontUtil.enableKerning(Font(if (SystemInfo.isMacOSCatalina) ".AppleSystemUIFont" else ".SF NS Text", style, size))
    if (!StartupUiUtil.isDialogFont(font)) {
      return FontUIResource(font)
    }
  }
  return FontUIResource(yosemite, style, size)
}

private fun installLinuxFonts(defaults: UIDefaults) {
  defaults.put("MenuItem.acceleratorFont", defaults.get("MenuItem.font"))
}

private fun patchTreeUI(defaults: UIDefaults) {
  defaults.put("TreeUI", DefaultTreeUI::class.java.name)
  defaults.put("Tree.repaintWholeRow", true)
  if (isUnsupported(defaults.getIcon("Tree.collapsedIcon"))) {
    defaults.put("Tree.collapsedIcon", getIcon("treeCollapsed"))
    defaults.put("Tree.collapsedSelectedIcon", getSelectedIcon("treeCollapsed"))
  }
  if (isUnsupported(defaults.getIcon("Tree.expandedIcon"))) {
    defaults.put("Tree.expandedIcon", getIcon("treeExpanded"))
    defaults.put("Tree.expandedSelectedIcon", getSelectedIcon("treeExpanded"))
  }
}

/**
 * @param icon an icon retrieved from L&F
 * @return `true` if an icon is not specified or if it is declared in some Swing L&F
 * (such icons do not have a variant to paint in selected row)
 */
private fun isUnsupported(icon: Icon?): Boolean {
  val name = icon?.javaClass?.name
  @Suppress("SpellCheckingInspection")
  return name == null || name.startsWith("javax.swing.plaf.") || name.startsWith("com.sun.java.swing.plaf.")
}

private fun patchHiDPI(defaults: UIDefaults) {
  val prevScaleVal = defaults["hidpi.scaleFactor"]
  // used to normalize previously patched values
  val prevScale = if (prevScaleVal != null) prevScaleVal as Float else 1f

  // fix predefined row height if default system font size is not expected
  val prevRowHeightScale = if (prevScaleVal != null || SystemInfoRt.isMac || SystemInfoRt.isWindows) prevScale else getFontScale(12f)
  patchRowHeight(defaults, "List.rowHeight", prevRowHeightScale)
  patchRowHeight(defaults, "Table.rowHeight", prevRowHeightScale)
  patchRowHeight(defaults, JBUI.CurrentTheme.Tree.rowHeightKey(), prevRowHeightScale)
  patchRowHeight(defaults, JBUI.CurrentTheme.VersionControl.Log.rowHeightKey(), prevRowHeightScale)
  if (prevScale == scale(1f) && prevScaleVal != null) {
    return
  }

  val intKeys = setOf("Tree.leftChildIndent", "Tree.rightChildIndent", "SettingsTree.rowHeight")
  val dimensionKeys = setOf("Slider.horizontalSize",
                            "Slider.verticalSize",
                            "Slider.minimumHorizontalSize",
                            "Slider.minimumVerticalSize")
  for (entry in defaults.entries) {
    val value = entry.value
    val key = entry.key.toString()
    if (value is Dimension) {
      if (value is UIResource || dimensionKeys.contains(key)) {
        entry.setValue(JBDimension.size(value).asUIResource())
      }
    }
    else if (value is Insets) {
      if (value is UIResource) {
        entry.setValue(JBInsets.create((value as Insets)).asUIResource())
      }
    }
    else if (value is Int) {
      if (key.endsWith(".maxGutterIconWidth") || intKeys.contains(key)) {
        val normValue = (value / prevScale).toInt()
        entry.setValue(Integer.valueOf(scale(normValue)))
      }
    }
  }
  defaults.put("hidpi.scaleFactor", scale(1f))
}

private fun patchRowHeight(defaults: UIDefaults, key: String, prevScale: Float) {
  val value = defaults.get(key)
  var rowHeight = if (value is Int) value else 0
  if (!SystemInfoRt.isMac && !SystemInfoRt.isWindows &&
      (!LoadingState.APP_STARTED.isOccurred || Registry.`is`("linux.row.height.disabled", true))) {
    rowHeight = 0
  }
  else if (rowHeight <= 0) {
    LOG.warn("$key = $value in ${UIManager.getLookAndFeel().name}; it may lead to performance degradation")
  }
  @Suppress("UnresolvedPluginConfigReference")
  val custom = if (LoadingState.APP_STARTED.isOccurred) Registry.intValue("ide.override.$key", -1) else -1
  defaults.put(key, if (custom >= 0) custom else if (rowHeight <= 0) 0 else scale((rowHeight / prevScale).toInt()))
}

/**
 * The following code is a trick! By default, Swing uses lightweight and "medium" weight
 * popups to show JPopupMenu. The code below force the creation of real heavyweight menus -
 * this increases speed of popups and allows getting rid of some drawing artifacts.
 */
private fun fixPopupWeight() {
  var popupWeight = OurPopupFactory.WEIGHT_MEDIUM
  var property = System.getProperty("idea.popup.weight")
  if (property != null) property = Strings.toLowerCase(property).trim { it <= ' ' }
  if (SystemInfoRt.isMac) {
    // force heavy weight popups under Leopard, otherwise they don't have shadow or any kind of border.
    popupWeight = OurPopupFactory.WEIGHT_HEAVY
  }
  else if (property == null) {
    // use defaults if popup weight isn't specified
    if (SystemInfoRt.isWindows) {
      popupWeight = OurPopupFactory.WEIGHT_HEAVY
    }
  }
  else {
    if ("light" == property) {
      popupWeight = OurPopupFactory.WEIGHT_LIGHT
    }
    else if ("heavy" == property) {
      popupWeight = OurPopupFactory.WEIGHT_HEAVY
    }
    else if ("medium" != property) {
      LOG.error("Illegal value of property \"idea.popup.weight\": $property")
    }
  }
  var factory = PopupFactory.getSharedInstance()
  if (factory !is OurPopupFactory) {
    factory = OurPopupFactory(factory)
    PopupFactory.setSharedInstance(factory)
  }
  PopupUtil.setPopupType(factory, popupWeight)
}

private fun patchFileChooserStrings(defaults: UIDefaults) {
  if (!defaults.containsKey(fileChooserTextKeys[0])) {
    // Alloy L&F does not define strings for names of context menu actions, so we have to patch them in here
    for (key in fileChooserTextKeys) {
      defaults.put(key, IdeBundle.message(key))
    }
  }
}

private fun useInterFont(): Boolean {
  return (ExperimentalUI.isNewUI() && SystemInfo.isJetBrainsJvm && (Runtime.version().feature() >= 17)) || forceToUseInterFont()
}

private fun forceToUseInterFont(): Boolean {
  return RegistryManager.getInstance().`is`("ide.ui.font.force.use.inter.font")
}

private fun updateUI(window: Window) {
  IJSwingUtilities.updateComponentTreeUI(window)
  for (w in window.ownedWindows) {
    IJSwingUtilities.updateComponentTreeUI(w)
  }
}

private fun repaintUI(window: Window) {
  if (!window.isDisplayable) {
    return
  }
  window.repaint()
  val children = window.ownedWindows
  for (aChildren in children) {
    repaintUI(aChildren)
  }
}
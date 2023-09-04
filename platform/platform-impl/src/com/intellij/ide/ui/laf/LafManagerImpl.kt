// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.ui.laf

import com.intellij.CommonBundle
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.runActivity
import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.QuickChangeLookAndFeel
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.ui.*
import com.intellij.ide.ui.UISettings.Companion.getPreferredFractionalMetricsValue
import com.intellij.ide.ui.laf.SystemDarkThemeDetector.Companion.createDetector
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.ide.ui.laf.intellij.IdeaPopupMenuUI
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
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
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
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
import com.intellij.util.SVGLoader.colorPatcherProvider
import com.intellij.util.SVGLoader.setSelectionColorPatcherProvider
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.ui.*
import com.intellij.util.ui.LafIconLookup.getIcon
import com.intellij.util.ui.LafIconLookup.getSelectedIcon
import kotlinx.coroutines.*
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import javax.swing.*
import javax.swing.plaf.FontUIResource
import javax.swing.plaf.UIResource

// A constant from Mac OS X implementation. See CPlatformWindow.WINDOW_ALPHA
private const val WINDOW_ALPHA = "Window.alpha"

private val LOG: Logger
  get() = logger<LafManagerImpl>()

private const val ELEMENT_LAF: @NonNls String = "laf"
private const val ELEMENT_PREFERRED_LIGHT_LAF: @NonNls String = "preferred-light-laf"
private const val ELEMENT_PREFERRED_DARK_LAF: @NonNls String = "preferred-dark-laf"
private const val ATTRIBUTE_AUTODETECT: @NonNls String = "autodetect"
private const val ATTRIBUTE_THEME_NAME: @NonNls String = "themeId"
private const val ELEMENT_LAFS_TO_PREVIOUS_SCHEMES: @NonNls String = "lafs-to-previous-schemes"
private const val ELEMENT_LAF_TO_SCHEME: @NonNls String = "laf-to-scheme"
private const val ATTRIBUTE_LAF: @NonNls String = "laf"
private const val ATTRIBUTE_SCHEME: @NonNls String = "scheme"
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
class LafManagerImpl(private val coroutineScope: CoroutineScope) : LafManager(), PersistentStateComponent<Element> {
  private val eventDispatcher = EventDispatcher.create(LafManagerListener::class.java)

  private val defaultDarkTheme = SynchronizedClearableLazy {
    val name = ApplicationInfoEx.getInstanceEx().defaultDarkLaf
    if (name == null) {
      findLafByName("Dark") ?: error("Default dark theme 'Dark' not found")
    }
    else {
      findLafByName(name) ?: error("Default dark theme '$name' not found")
    }
  }

  private val defaultClassicDarkTheme = SynchronizedClearableLazy {
    val name = ApplicationInfoEx.getInstanceEx().defaultClassicDarkLaf
    if (name == null) {
      findLafByName("Darcula")?: error("Default classic dark theme 'Darcula' not found")
    }
    else {
      findLafByName(name) ?: error("Default classic dark theme '$name' not found")
    }
  }

  private val defaultLightTheme = SynchronizedClearableLazy {
    val name = ApplicationInfoEx.getInstanceEx().defaultLightLaf
    if (name == null) {
      findLafByName("Light") ?: error("Default light LookAndFeel 'Light' not found")
    }
    else {
      findLafByName(name) ?: error("Default light LookAndFeel '$name' not found")
    }
  }

  private val defaultClassicLightTheme = SynchronizedClearableLazy {
    val name = ApplicationInfoEx.getInstanceEx().defaultClassicLightLaf
    if (name == null) {
      UiThemeProviderListManager.getInstance().findJetBrainsLightTheme() ?: error("JetBrains light theme not found")
    }
    else {
      findLafByName(name) ?: error("Default light LookAndFeel '$name' not found")
    }
  }

  private val ourDefaults: Map<Any, Any> = UIManager.getDefaults().clone() as UIDefaults
  private var currentTheme: UIThemeLookAndFeelInfo? = null
  private var preferredLightTheme: UIThemeLookAndFeelInfo? = null
  private var preferredDarkTheme: UIThemeLookAndFeelInfo? = null
  private val myStoredDefaults = HashMap<LafReference?, MutableMap<String, Any?>>()
  private val lafComboBoxModel = SynchronizedClearableLazy<CollectionComboBoxModel<LafReference>> { LafComboBoxModel() }
  private val settingsToolbar = lazy {
    val group = DefaultActionGroup(PreferredLafAction())
    val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, group, true)
    toolbar.targetComponent = toolbar.component
    toolbar.component.isOpaque = false
    toolbar
  }

  // SystemDarkThemeDetector must be created as part of LafManagerImpl initialization and not on demand because system listeners are added
  private var themeDetector: SystemDarkThemeDetector? = null
  private var isFirstSetup = true
  private var isUpdatingPlugin = false
  private var themeIdBeforePluginUpdate: String? = null
  private var autodetect = false
  private var defaultThemeId = ""

  // We remember the last used editor scheme for each laf in order to restore it after switching laf
  private var rememberSchemeForLaf = true
  private val lafToPreviousScheme: MutableMap<String, String> = mutableMapOf()

  /**
   * Stores values of options from [UISettings] which were used to set up current LaF.
   */
  private var usedValuesOfUiOptions: List<Any?> = emptyList()

  override fun getDefaultLightLaf(): UIThemeLookAndFeelInfo {
    return if (ExperimentalUI.isNewUI()) defaultLightTheme.value else defaultClassicLightTheme.value
  }

  override fun getDefaultDarkLaf(): UIThemeLookAndFeelInfo {
    return if (ExperimentalUI.isNewUI()) defaultDarkTheme.value else defaultClassicDarkTheme.value
  }

  val defaultFont: Font
    get() {
      return when {
               useInterFont() -> defaultInterFont
               UISettings.getInstance().overrideLafFonts || UISettingsUtils.getInstance().currentIdeScale != 1f -> storedLafFont
               else -> null
             } ?: JBFont.label()
    }

  companion object {
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
      val menuFont = getFont("Lucida Grande", 13, Font.PLAIN)
      defaults.put("Menu.font", menuFont)
      defaults.put("MenuItem.font", menuFont)
      defaults.put("MenuItem.acceleratorFont", menuFont)
      defaults.put("PasswordField.font", defaults.getFont("TextField.font"))
    }

    private var ourTestInstance: LafManagerImpl? = null

    @OptIn(DelicateCoroutinesApi::class)
    @TestOnly
    @JvmStatic
    fun getTestInstance(): LafManagerImpl? {
      if (ourTestInstance == null) {
        ourTestInstance = LafManagerImpl(GlobalScope)
      }
      return ourTestInstance
    }
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
    // we preload LafManagerImpl in EDT, no one can access LafManagerImpl early
    EDT.assertIsEdt()

    val theme = currentTheme!!
    if (theme is UIThemeLookAndFeelInfoImpl) {
      if (!theme.isInitialised) {
        doSetLaF(lookAndFeelInfo = theme, installEditorScheme = false)
      }
    }
    selectComboboxModel()

    runActivity("new ui configuration") {
      ExperimentalUI.getInstance().lookAndFeelChanged()
    }

    updateUI()
    // must be after updateUI
    isFirstSetup = false
    detectAndSyncLaf()
    addListeners()
  }

  private fun addListeners() {
    UIThemeProvider.EP_NAME.addExtensionPointListener(UiThemeEpListener())
    val connection = ApplicationManager.getApplication().messageBus.connect(coroutineScope)
    connection.subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        isUpdatingPlugin = isUpdate
        themeIdBeforePluginUpdate = if (currentTheme is UIThemeLookAndFeelInfo) {
          (currentTheme as UIThemeLookAndFeelInfo).theme.id
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
    @Suppress("ObjectLiteralToLambda")
    connection.subscribe(UISettingsListener.TOPIC, object : UISettingsListener {
      override fun uiSettingsChanged(uiSettings: UISettings) {
        val newValues = computeValuesOfUsedUiOptions()
        if (newValues != usedValuesOfUiOptions) {
          updateUI()
        }
      }
    })
  }

  private fun detectAndSyncLaf() {
    if (autodetect) {
      val lafDetector = getOrCreateLafDetector
      if (lafDetector.detectionSupported) {
        lafDetector.check()
      }
    }
  }

  private fun syncTheme(systemIsDark: Boolean) {
    if (!autodetect) {
      return
    }

    val currentIsDark = StartupUiUtil.isDarkTheme ||
                        (currentTheme is UIThemeLookAndFeelInfo && (currentTheme as UIThemeLookAndFeelInfo).theme.isDark)
    var expectedTheme: UIThemeLookAndFeelInfo?
    if (systemIsDark) {
      expectedTheme = preferredDarkTheme
      if (expectedTheme == null) {
        expectedTheme = defaultDarkLaf
      }
    }
    else {
      expectedTheme = preferredLightTheme
      if (expectedTheme == null) {
        expectedTheme = defaultLightLaf
      }
    }
    if (currentIsDark != systemIsDark || currentTheme !== expectedTheme) {
      QuickChangeLookAndFeel.switchLafAndUpdateUI(this, expectedTheme, true)
    }
  }

  override fun loadState(element: Element) {
    val oldTheme = currentTheme
    val newTheme = loadThemeState(element, ELEMENT_LAF) ?:
                   // We try to read `class-name` attribute for backward compatibilities IDE's before 2023.3.
                   // Only for Darcula theme. We could drop it in future when we will stop bundle Darcula.
                   if (element.getChild("laf")?.getAttribute("class-name")?.value == "com.intellij.ide.ui.laf.darcula.DarculaLaf") {
                     findLafByName("Darcula") ?: loadDefaultTheme()
                   }
                   else {
                     loadDefaultTheme()
                   }

    autodetect = java.lang.Boolean.parseBoolean(element.getAttributeValue(ATTRIBUTE_AUTODETECT))
    preferredLightTheme = loadThemeState(element, ELEMENT_PREFERRED_LIGHT_LAF)
    preferredDarkTheme = loadThemeState(element, ELEMENT_PREFERRED_DARK_LAF)

    val lafsToSchemesElement = element.getChild(ELEMENT_LAFS_TO_PREVIOUS_SCHEMES)
    if (lafsToSchemesElement != null) {
      val lafToSchemes = lafsToSchemesElement.getChildren(ELEMENT_LAF_TO_SCHEME)
      for (lafToScheme in lafToSchemes) {
        lafToPreviousScheme.put(lafToScheme.getAttributeValue(ATTRIBUTE_LAF), lafToScheme.getAttributeValue(ATTRIBUTE_SCHEME))
      }
    }

    if (autodetect) {
      getOrCreateLafDetector
    }
    if (!isFirstSetup && newTheme != oldTheme) {
      QuickChangeLookAndFeel.switchLafAndUpdateUI(this, newTheme, true, true, true)
    }
    else {
      currentTheme = newTheme
    }
  }

  private fun loadThemeState(element: Element, attributeName: @NonNls String): UIThemeLookAndFeelInfo? {
    val id = element.getChild(attributeName)?.getAttributeValue(ATTRIBUTE_THEME_NAME) ?: return null
    return UiThemeProviderListManager.getInstance().findThemeById(id)
  }

  override fun noStateLoaded() {
    val theme = loadDefaultTheme()
    currentTheme = theme
    defaultThemeId = theme.theme.id
    preferredLightTheme = null
    preferredDarkTheme = null
    autodetect = false
  }

  override fun getState(): Element {
    val element = Element("state")
    if (autodetect) {
      element.setAttribute(ATTRIBUTE_AUTODETECT, java.lang.Boolean.toString(autodetect))
    }
    if (currentTheme?.theme?.id != defaultThemeId) {
      writeThemeState(element, ELEMENT_LAF, currentUIThemeLookAndFeel)
    }
    if (preferredLightTheme != null && preferredLightTheme !== defaultLightLaf) {
      writeThemeState(element, ELEMENT_PREFERRED_LIGHT_LAF, preferredLightTheme)
    }
    if (preferredDarkTheme != null && preferredDarkTheme !== defaultDarkLaf) {
      writeThemeState(element, ELEMENT_PREFERRED_DARK_LAF, preferredDarkTheme)
    }

    if (lafToPreviousScheme.isNotEmpty()) {
      val lafsToSchemes = Element(ELEMENT_LAFS_TO_PREVIOUS_SCHEMES)
      val lafToPreviousSchemeSorted = lafToPreviousScheme.toList().sortedBy { it.first }
      for ((laf, scheme) in lafToPreviousSchemeSorted) {
        val lafToScheme = Element(ELEMENT_LAF_TO_SCHEME)
        lafToScheme.setAttribute(ATTRIBUTE_LAF, laf)
        lafToScheme.setAttribute(ATTRIBUTE_SCHEME, scheme)
        lafsToSchemes.addContent(lafToScheme)
      }
      element.addContent(lafsToSchemes)
    }

    return element
  }

  override fun getInstalledLookAndFeels(): Array<UIThemeLookAndFeelInfo> {
    return UiThemeProviderListManager.getInstance().getLaFs().toList().toTypedArray()
  }

  override fun getLafComboBoxModel(): CollectionComboBoxModel<LafReference> = lafComboBoxModel.value

  fun getThemeListForTargetUI(targetUI: TargetUIType): Sequence<UIThemeLookAndFeelInfo> {
    return UiThemeProviderListManager.getInstance().getLaFsWithUITypes().asSequence()
      .filter { it.targetUiType == targetUI }
      .mapNotNull { it.theme.get() }
  }

  private val allReferences: List<LafReference>
    get() {
      val result = ArrayList<LafReference>()
      for (group in ThemeListProvider.getInstance().getShownThemes()) {
        if (!result.isEmpty()) {
          result.add(SEPARATOR)
        }
        for (info in group) {
          result.add(createLafReference(info))
        }
      }
      return result
    }

  private fun updateLafComboboxModel() {
    lafComboBoxModel.drop()
  }

  private fun selectComboboxModel() {
    if (lafComboBoxModel.isInitialized()) {
      lafComboBoxModel.value.selectedItem = createLafReference(currentTheme)
    }
  }

  override fun findLaf(reference: LafReference): UIThemeLookAndFeelInfo {
    return UiThemeProviderListManager.getInstance().getLaFsWithUITypes()
             .singleOrNull { it.id == reference.themeId }
             ?.theme
             ?.get()
           ?: error("Theme not found for themeId: ${reference.themeId}")
  }

  @Suppress("removal")
  override fun getCurrentLookAndFeel(): UIManager.LookAndFeelInfo? = currentTheme

  override fun getCurrentUIThemeLookAndFeel(): UIThemeLookAndFeelInfo? = currentTheme

  override fun getLookAndFeelReference(): LafReference = createLafReference(currentUIThemeLookAndFeel)

  override fun getLookAndFeelCellRenderer(): ListCellRenderer<LafReference> = LafCellRenderer()

  override fun getSettingsToolbar(): JComponent = settingsToolbar.value.component

  private fun loadDefaultTheme(): UIThemeLookAndFeelInfo {
    // use HighContrast theme for IDE in Windows if HighContrast desktop mode is set
    if (SystemInfoRt.isWindows && Toolkit.getDefaultToolkit().getDesktopProperty("win.highContrast.on") == true) {
      UiThemeProviderListManager.getInstance().findThemeById(HIGH_CONTRAST_THEME_ID)?.let {
        return it
      }
    }
    return defaultDarkLaf
  }

  private fun findLafByName(name: String): UIThemeLookAndFeelInfo? {
    return UiThemeProviderListManager.getInstance().findThemeByName(name)
  }

  /**
   * Sets current LAF. The method doesn't update component hierarchy.
   */
  override fun setCurrentLookAndFeel(lookAndFeelInfo: UIManager.LookAndFeelInfo, lockEditorScheme: Boolean) {
    setLookAndFeelImpl(lookAndFeelInfo = lookAndFeelInfo as UIThemeLookAndFeelInfo,
                       installEditorScheme = !lockEditorScheme,
                       processChangeSynchronously = true)
  }

  /**
   * Sets current LAF. The method doesn't update component hierarchy.
   */
  private fun setLookAndFeelImpl(lookAndFeelInfo: UIThemeLookAndFeelInfo, installEditorScheme: Boolean, processChangeSynchronously: Boolean) {
    val oldLaf = currentTheme

    rememberSchemeForLaf(EditorColorsManager.getInstance().globalScheme)

    if (oldLaf !== lookAndFeelInfo && oldLaf is UIThemeLookAndFeelInfoImpl) {
      oldLaf.dispose()
    }
    if (doSetLaF(lookAndFeelInfo, installEditorScheme)) {
      return
    }

    currentTheme = lookAndFeelInfo
    selectComboboxModel()
    if (!isFirstSetup && installEditorScheme) {
      if (processChangeSynchronously) {
        updateEditorSchemeIfNecessary(oldLaf, true)
        UISettings.getInstance().fireUISettingsChanged()
        ActionToolbarImpl.updateAllToolbarsImmediately()
      }
      else {
        coroutineScope.launch(Dispatchers.EDT) {
          updateEditorSchemeIfNecessary(oldLaf, false)
          UISettings.getInstance().fireUISettingsChanged()
          ActionToolbarImpl.updateAllToolbarsImmediately()
        }
      }
    }
    isFirstSetup = false
  }

  @Suppress("unused")
  @Internal
  fun updateLafNoSave(lookAndFeelInfo: UIThemeLookAndFeelInfo) = doSetLaF(lookAndFeelInfo = lookAndFeelInfo, installEditorScheme = false)

  private fun doSetLaF(lookAndFeelInfo: UIThemeLookAndFeelInfo, installEditorScheme: Boolean): Boolean {
    val defaults = UIManager.getDefaults()
    defaults.clear()
    fillFallbackDefaults(defaults)
    defaults.putAll(ourDefaults)
    if (!isFirstSetup) {
      colorPatcherProvider = null
      setSelectionColorPatcherProvider(null)
    }
    UIManager.setLookAndFeel(DarculaLaf())
    // set L&F
    if (lookAndFeelInfo is UIThemeLookAndFeelInfoImpl) {
      try {
        lookAndFeelInfo.installTheme(UIManager.getLookAndFeelDefaults(), !installEditorScheme)
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
    val settingsUtils = UISettingsUtils.getInstance()
    val ideScale = settingsUtils.currentIdeScale

    // need to temporarily reset this to correctly apply new size values
    settingsUtils.setCurrentIdeScale(1f)
    val uiSettings = UISettings.getInstance()
    uiSettings.fireUISettingsChanged()
    setCurrentLookAndFeel(currentUIThemeLookAndFeel!!, true)
    updateUI()
    settingsUtils.setCurrentIdeScale(ideScale)
    uiSettings.fireUISettingsChanged()
  }

  private fun updateEditorSchemeIfNecessary(oldLaf: UIThemeLookAndFeelInfo?, processChangeSynchronously: Boolean) {
    if (oldLaf is TempUIThemeLookAndFeelInfo || currentTheme is TempUIThemeLookAndFeelInfo) {
      return
    }
    if (currentTheme is UIThemeLookAndFeelInfo && (currentTheme as UIThemeLookAndFeelInfo).theme.editorSchemeName != null) {
      return
    }

    val editorColorManager = EditorColorsManager.getInstance()
    val current = editorColorManager.globalScheme
    if (currentTheme != null) {
      val previousSchemeForLaf = getPreviousSchemeForLaf(currentTheme!!)
      if (previousSchemeForLaf != null) {
        (editorColorManager as EditorColorsManagerImpl).setGlobalScheme(previousSchemeForLaf, processChangeSynchronously)
        return
      }
    }

    val dark = StartupUiUtil.isDarkTheme
    val wasUITheme = oldLaf is UIThemeLookAndFeelInfo
    if (wasUITheme || dark != ColorUtil.isDark(current.defaultBackground)) {
      var targetScheme = defaultNonLaFSchemeName(dark)
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
      val scheme = editorColorManager.getScheme(targetScheme)
      if (scheme != null) {
        (editorColorManager as EditorColorsManagerImpl).setGlobalScheme(scheme, processChangeSynchronously)
      }
    }
  }

  /**
   * Updates LAF of all windows. The method also updates font of components as it's configured in `UISettings`.
   */
  override fun updateUI() {
    val uiDefaults = UIManager.getLookAndFeelDefaults()
    uiDefaults.put("*cache", ConcurrentHashMap<String, Color>()) // for JBColor
    uiDefaults.put("LinkButtonUI", DefaultLinkButtonUI::class.java.name)
    fixPopupWeight()
    fixMenuIssues(uiDefaults)
    StartupUiUtil.initInputMapDefaults(uiDefaults)
    uiDefaults.put("Button.defaultButtonFollowsFocus", false)
    uiDefaults.put("Balloon.error.textInsets", JBInsets(3, 8, 3, 8).asUIResource())
    patchFileChooserStrings(uiDefaults)
    patchLafFonts(uiDefaults)

    patchTreeUI(uiDefaults, currentTheme)

    if (ExperimentalUI.isNewUI()) {
      applyDensity(uiDefaults)
    }

    //should be called last because this method modifies uiDefault values
    patchHiDPI(uiDefaults)
    // required for MigLayout logical pixels to work
    // super-huge DPI causes issues like IDEA-170295 if `laf.scaleFactor` property is missing
    uiDefaults.put("laf.scaleFactor", scale(1f))
    uiDefaults.put(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false))
    uiDefaults.put(RenderingHints.KEY_TEXT_LCD_CONTRAST, StartupUiUtil.getLcdContrastValue())
    uiDefaults.put(RenderingHints.KEY_FRACTIONALMETRICS, AppUIUtil.adjustFractionalMetrics(getPreferredFractionalMetricsValue()))
    usedValuesOfUiOptions = computeValuesOfUsedUiOptions()
    if (isFirstSetup) {
      coroutineScope.launch {
        val publisher = ApplicationManager.getApplication().messageBus.syncAndPreloadPublisher(LafManagerListener.TOPIC)
        withContext(RawSwingDispatcher) {
          val activity = StartUpMeasurer.startActivity("lookAndFeelChanged event processing")
          publisher.lookAndFeelChanged(this@LafManagerImpl)
          activity.end()
        }
      }
    }
    else {
      ExperimentalUI.getInstance().lookAndFeelChanged()
      notifyLookAndFeelChanged()
      for (frame in Frame.getFrames()) {
        updateUI(frame)
      }
    }
  }

  private fun computeValuesOfUsedUiOptions(): List<Any?> {
    val instance = UISettings.getInstance()
    return listOf(
      instance.overrideLafFonts,
      instance.fontFace,
      instance.fontSize2D,
      instance.ideAAType,
      instance.editorAAType,
      instance.ideScale,
      instance.presentationModeIdeScale,
    )
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
      val fontSize = (if (uiSettings.overrideLafFonts) uiSettings.fontSize2D else defaultFont.size2D) * currentScale
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
      val lf = if (currentTheme == null) null else lookAndFeelReference
      val lfDefaults: Map<String, Any?>? = myStoredDefaults[lf]
      return if (lfDefaults == null) null else lfDefaults["Label.font"] as Font?
    }

  private fun restoreOriginalFontDefaults(defaults: UIDefaults) {
    val laf = if (currentTheme == null) null else lookAndFeelReference
    val lafDefaults = myStoredDefaults.get(laf)
    if (lafDefaults != null) {
      for (resource in StartupUiUtil.patchableFontResources) {
        defaults.put(resource, lafDefaults.get(resource))
      }
    }
    setUserScaleFactor(getFontScale(JBFont.label().size.toFloat()))
  }

  private fun storeOriginalFontDefaults(defaults: UIDefaults) {
    val laf = if (currentTheme == null) null else lookAndFeelReference
    var lafDefaults = myStoredDefaults.get(laf)
    if (lafDefaults == null) {
      lafDefaults = HashMap()
      for (resource in StartupUiUtil.patchableFontResources) {
        lafDefaults.put(resource, defaults.get(resource))
      }
      myStoredDefaults.put(laf, lafDefaults)
    }
  }

  /**
   * Repaints all displayable windows.
   */
  override fun repaintUI() {
    val frames = Frame.getFrames()
    for (frame in frames) {
      repaintUI(frame)
    }
  }

  override fun getAutodetect(): Boolean = autodetect && autodetectSupported

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
  }

  override fun getAutodetectSupported(): Boolean = getOrCreateLafDetector.detectionSupported

  private val getOrCreateLafDetector: SystemDarkThemeDetector
    get() {
      var result = themeDetector
      if (result == null) {
        result = createDetector(::syncTheme)
        themeDetector = result
      }

      return result
    }

  override fun setPreferredDarkLaf(value: UIThemeLookAndFeelInfo) {
    preferredDarkTheme = value
  }

  override fun setPreferredLightLaf(value: UIThemeLookAndFeelInfo) {
    preferredLightTheme = value
  }

  override fun getPreviousSchemeForLaf(lookAndFeelInfo: UIThemeLookAndFeelInfo): EditorColorsScheme? {
    val schemeName = lafToPreviousScheme.get(lookAndFeelInfo.name) ?: return null
    return EditorColorsManager.getInstance().getScheme(schemeName)
  }

  override fun setRememberSchemeForLaf(rememberSchemeForLaf: Boolean) {
    this.rememberSchemeForLaf = rememberSchemeForLaf
  }

  override fun rememberSchemeForLaf(scheme: EditorColorsScheme) {
    val lookAndFeelInfo: UIThemeLookAndFeelInfo? = currentTheme
    if (rememberSchemeForLaf && lookAndFeelInfo != null) {
      if (isSchemeDefault(lookAndFeelInfo, scheme)) {
        lafToPreviousScheme.remove(lookAndFeelInfo.name)
      }
      else {
        lafToPreviousScheme.put(lookAndFeelInfo.name, scheme.name)
      }
    }
  }

  private fun isSchemeDefault(lookAndFeelInfo: UIThemeLookAndFeelInfo, scheme: EditorColorsScheme): Boolean {
    return lookAndFeelInfo.theme.editorSchemeName == scheme.displayName
  }

  private inner class UiThemeEpListener : ExtensionPointListener<UIThemeProvider> {
    override fun extensionAdded(extension: UIThemeProvider, pluginDescriptor: PluginDescriptor) {
      val uiThemeProviderListManager = UiThemeProviderListManager.getInstance()
      val newLaF = uiThemeProviderListManager.themeProviderAdded(extension) ?: return
      updateLafComboboxModel()

      // when updating a theme plugin that doesn't provide the current theme, don't select any of its themes as current
      val newTheme = newLaF.theme
      pluginDescriptor.pluginClassLoader?.let {
        newTheme.setProviderClassLoader(it)
      }
      if (!autodetect && (!isUpdatingPlugin || newTheme.id == themeIdBeforePluginUpdate)) {
        setLookAndFeelImpl(lookAndFeelInfo = newLaF, installEditorScheme = true, processChangeSynchronously = false)
        JBColor.setDark(newTheme.isDark)
        updateUI()
      }
    }

    override fun extensionRemoved(extension: UIThemeProvider, pluginDescriptor: PluginDescriptor) {
      val oldLaF = UiThemeProviderListManager.getInstance().themeProviderRemoved(extension) ?: return
      val oldTheme = oldLaF.theme
      oldTheme.setProviderClassLoader(null)
      val isDark = oldTheme.isDark
      val defaultLaF = if (oldLaF === currentUIThemeLookAndFeel) {
        if (isDark) defaultDarkLaf else defaultLightLaf
      }
      else {
        null
      }
      updateLafComboboxModel()
      if (defaultLaF != null) {
        setLookAndFeelImpl(lookAndFeelInfo = defaultLaF, installEditorScheme = true, processChangeSynchronously = true)
        if (ExperimentalUI.isNewUI()) {
          JBColor.setDark(defaultDarkTheme.isInitialized() && isDark)
        }
        else {
          JBColor.setDark(defaultClassicDarkTheme.isInitialized() && isDark)
        }
        updateUI()
      }
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
      val popup = JBPopupFactory.getInstance().createActionGroupPopup(IdeBundle.message("preferred.theme.text"), getLafGroups(), e.dataContext,
                                                                      true, null, Int.MAX_VALUE)
      val component = e.inputEvent!!.component
      HelpTooltip.setMasterPopup(component, popup)
      if (component is ActionButtonComponent) {
        popup.showUnderneathOf(component)
      }
      else {
        popup.showInCenterOf(component)
      }
    }

    private fun getLafGroups(): ActionGroup {
      val lightLaFs = ArrayList<UIThemeLookAndFeelInfo>()
      val darkLaFs = ArrayList<UIThemeLookAndFeelInfo>()
      for (lafInfo in ThemeListProvider.getInstance().getShownThemes().asSequence().flatten()) {
        if (lafInfo.theme.isDark) {
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
                                   lafs: List<UIThemeLookAndFeelInfo>,
                                   isDark: Boolean): Collection<AnAction> {
      if (lafs.isEmpty()) {
        return emptyList()
      }

      val result = ArrayList<AnAction>()
      result.add(Separator.create(separatorText))
      lafs.mapTo(result) { LafToggleAction(name = it.name, lafInfo = it, isDark = isDark) }
      return result
    }
  }

  private inner class LafToggleAction(name: @Nls String?,
                                      private val lafInfo: UIThemeLookAndFeelInfo,
                                      private val isDark: Boolean) : ToggleAction(name) {
    override fun isSelected(e: AnActionEvent): Boolean {
      return if (isDark) {
        (preferredDarkTheme?.name ?: defaultDarkLaf.name) == lafInfo.name
      }
      else {
        (preferredLightTheme?.name ?: defaultLightLaf.name) == lafInfo.name
      }
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (isDark) {
        if (preferredDarkTheme?.name != lafInfo.name) {
          preferredDarkTheme = if (lafInfo.name == defaultDarkLaf.name) null else lafInfo
          detectAndSyncLaf()
        }
      }
      else if (preferredLightTheme?.name != lafInfo.name) {
        preferredLightTheme = if (lafInfo.name == defaultLightLaf.name) null else lafInfo
        detectAndSyncLaf()
      }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun isDumbAware() = true
  }
}

private class LafCellRenderer : SimpleListCellRenderer<LafManager.LafReference>() {
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

  override fun getListCellRendererComponent(list: JList<out LafManager.LafReference>,
                                            value: LafManager.LafReference,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    return if (value === SEPARATOR) separator else super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
  }

  override fun customize(list: JList<out LafManager.LafReference>,
                         value: LafManager.LafReference,
                         index: Int,
                         selected: Boolean,
                         hasFocus: Boolean) {
    text = value.toString()
  }
}

open class IJColor internal constructor(color: Color?, private val name: String) : JBColor(Supplier { color }) {
  override fun getName(): String = name

  override fun toString(): String = "${super.toString()} Name: $name"
}

private class IJColorUIResource(color: Color?, name: String) : IJColor(color, name), UIResource

private val SEPARATOR = LafManager.LafReference("", null)

private val fileChooserTextKeys = arrayOf(
  "FileChooser.viewMenuLabelText", "FileChooser.newFolderActionLabelText",
  "FileChooser.listViewActionLabelText", "FileChooser.detailsViewActionLabelText", "FileChooser.refreshActionLabelText"
)

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
    const val WEIGHT_LIGHT: Int = 0
    const val WEIGHT_MEDIUM: Int = 1
    const val WEIGHT_HEAVY: Int = 2

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
    if (window !is RootPaneContainer || window === ComponentUtil.getWindow(owner)) {
      return popup
    }

    // disable popup caching by runtime
    popup = HeavyWeightPopup(popup, window)
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
    if ((IdeaPopupMenuUI.isUnderPopup(contents) || (SystemInfoRt.isWindows || IdeaPopupMenuUI.isUnderMainMenu(contents)))
        && WindowRoundedCornersManager.isAvailable()) {
      if ((SystemInfoRt.isMac && StartupUiUtil.isDarkTheme) || SystemInfoRt.isWindows) {
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
    return popup
  }
}

private fun writeThemeState(element: Element, attributeName: @NonNls String?, suggestedLaf: UIThemeLookAndFeelInfo?) {
  val laf = (if (suggestedLaf is TempUIThemeLookAndFeelInfo) suggestedLaf.previousLaf else suggestedLaf) ?: return
  val child = Element(attributeName)
  child.setAttribute(ATTRIBUTE_THEME_NAME, laf.theme.id)
  element.addContent(child)
}

private fun createLafReference(laf: UIThemeLookAndFeelInfo?): LafManager.LafReference {
  val themeId = if (laf is UIThemeLookAndFeelInfo) laf.theme.id else null
  return LafManager.LafReference(laf!!.name, themeId)
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
  return if (color is UIResource) IJColorUIResource(color, key) else IJColor(color, key)
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

private fun patchTreeUI(defaults: UIDefaults, currentLaf: UIThemeLookAndFeelInfo?) {
  defaults.put("TreeUI", DefaultTreeUI::class.java.name)
  defaults.put("Tree.repaintWholeRow", true)

  if (currentLaf is UIThemeLookAndFeelInfo &&
      defaults.containsKey("Tree.collapsedIcon") &&
      defaults.containsKey("Tree.collapsedSelectedIcon") &&
      defaults.containsKey("Tree.expandedIcon") &&
      defaults.containsKey("Tree.expandedSelectedIcon")) {
    // do not resolve lazy icons for Darcula and other modern UI themes
    return
  }

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
 * @return `true` if an icon is not specified or if it is declared in some Swing L&F
 * (such icons do not have a variant to paint in the selected row)
 */
private fun isUnsupported(icon: Icon?): Boolean {
  val name = icon?.javaClass?.name
  @Suppress("SpellCheckingInspection")
  return name == null || name.startsWith("javax.swing.plaf.") || name.startsWith("com.sun.java.swing.plaf.")
}

private fun patchHiDPI(defaults: UIDefaults) {
  val prevScaleVal = defaults.get("hidpi.scaleFactor")
  // used to normalize previously patched values
  val prevScale = if (prevScaleVal == null) 1f else prevScaleVal as Float

  // fix predefined row height if default system font size is not expected
  val prevRowHeightScale = if (prevScaleVal != null || SystemInfoRt.isMac || SystemInfoRt.isWindows) prevScale else getFontScale(12f)
  patchRowHeight(defaults, "List.rowHeight", prevRowHeightScale)
  patchRowHeight(defaults, "Table.rowHeight", prevRowHeightScale)
  patchRowHeight(defaults, JBUI.CurrentTheme.Tree.rowHeightKey(), prevRowHeightScale)
  patchRowHeight(defaults, JBUI.CurrentTheme.VersionControl.Log.rowHeightKey(), prevRowHeightScale)
  if (prevScale == scale(1f) && prevScaleVal != null) {
    return
  }

  val intKeys = hashSetOf("Tree.leftChildIndent", "Tree.rightChildIndent", "SettingsTree.rowHeight")
  val dimensionKeys = hashSetOf("Slider.horizontalSize",
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
 * popups to show JPopupMenu. The code below forces the creation of real heavyweight menus -
 * this increases the speed of popups and allows getting rid of some drawing artifacts.
 */
private fun fixPopupWeight() {
  var popupWeight = OurPopupFactory.WEIGHT_MEDIUM
  val property = System.getProperty("idea.popup.weight")?.lowercase()?.trim()
  if (SystemInfoRt.isMac) {
    // force heavy weight popups under Leopard, otherwise they don't have a shadow or any kind of border.
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
  return (ExperimentalUI.isNewUI() && SystemInfo.isJetBrainsJvm) || Registry.`is`("ide.ui.font.force.use.inter.font", false)
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
  for (aChildren in window.ownedWindows) {
    repaintUI(aChildren)
  }
}

private fun applyDensity(defaults: UIDefaults) {
  val densityKey = "ui.density"
  val oldDensityName = defaults.get(densityKey) as? String
  val newDensity = UISettings.getInstance().uiDensity
  if (oldDensityName == newDensity.name) {
    // re-applying the same density would break HiDPI-scalable values like Tree.rowHeight
    return
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
    // toolbars
    defaults.put(JBUI.CurrentTheme.Toolbar.horizontalInsetsKey(), cmInsets(2, 4))
    defaults.put(JBUI.CurrentTheme.Toolbar.verticalInsetsKey(), cmInsets(2, 4))
    // main toolbar
    defaults.put(JBUI.CurrentTheme.Toolbar.experimentalToolbarButtonSizeKey(), cmSize(30, 30))
    defaults.put(JBUI.CurrentTheme.Toolbar.experimentalToolbarButtonIconSizeKey(), 16)
    defaults.put(JBUI.CurrentTheme.Toolbar.experimentalToolbarFontKey(), Supplier { JBFont.medium().asUIResource() })
    defaults.put(JBUI.CurrentTheme.TitlePane.buttonPreferredSizeKey(), cmSize(44, 34))
    // tool window stripes
    defaults.put(JBUI.CurrentTheme.Toolbar.stripeToolbarButtonSizeKey(), cmSize(32, 32))
    defaults.put(JBUI.CurrentTheme.Toolbar.stripeToolbarButtonIconSizeKey(), 16)
    defaults.put(JBUI.CurrentTheme.Toolbar.stripeToolbarButtonIconPaddingKey(), cmInsets(4))
    defaults.put(JBUI.CurrentTheme.Toolbar.mainToolbarButtonInsetsKey(), cmInsets(2))
    // Run Widget
    defaults.put(JBUI.CurrentTheme.RunWidget.toolbarHeightKey(), 26)
    defaults.put(JBUI.CurrentTheme.RunWidget.toolbarBorderHeightKey(), 4)
    defaults.put(JBUI.CurrentTheme.RunWidget.configurationSelectorFontKey(), Supplier { JBFont.medium().asUIResource() })
    // trees
    defaults.put(JBUI.CurrentTheme.Tree.rowHeightKey(), 22)
    // lists
    defaults.put("List.rowHeight", 22)
    // popups
    defaults.put(JBUI.CurrentTheme.Popup.headerInsetsKey(), cmInsets(8, 10, 8, 10))
    defaults.put(JBUI.CurrentTheme.Advertiser.borderInsetsKey(), cmInsets(4, 20, 5, 20))
    defaults.put(JBUI.CurrentTheme.BigPopup.advertiserBorderInsetsKey(), cmInsets(4, 20, 5, 20))
    defaults.put(JBUI.CurrentTheme.CompletionPopup.Advertiser.borderInsetsKey(), cmInsets(2, 12, 2, 8))
    defaults.put(JBUI.CurrentTheme.CompletionPopup.selectionInnerInsetsKey(), cmInsets(0, 2, 0, 2))
    defaults.put(JBUI.CurrentTheme.FindPopup.scopesPanelInsetsKey(), cmInsets(1, 20))
    defaults.put(JBUI.CurrentTheme.FindPopup.bottomPanelInsetsKey(), cmInsets(1, 18))
    defaults.put(JBUI.CurrentTheme.ComplexPopup.headerInsetsKey(), cmInsets(10, 20, 8, 15))
    defaults.put(JBUI.CurrentTheme.ComplexPopup.textFieldInputInsetsKey(), cmInsets(4, 2))
    defaults.put(
      JBUI.CurrentTheme.ComplexPopup.innerBorderInsetsKey(),
      JBUI.CurrentTheme.ComplexPopup.innerBorderInsets().withTopAndBottom(2)
    )
    defaults.put(JBUI.CurrentTheme.TabbedPane.tabHeightKey(), 36)
    // status bar
    defaults.put(JBUI.CurrentTheme.StatusBar.Widget.insetsKey(), cmInsets(4, 8, 3, 8))
    defaults.put(JBUI.CurrentTheme.StatusBar.Breadcrumbs.navBarInsetsKey(), cmInsets(1, 0, 1, 4))
    defaults.put(JBUI.CurrentTheme.StatusBar.fontKey(), Supplier { JBFont.medium().asUIResource() })
    // separate navbar
    defaults.put(JBUI.CurrentTheme.NavBar.itemInsetsKey(), cmInsets(2))
    // editor search/replace
    defaults.put(JBUI.CurrentTheme.Editor.SearchField.borderInsetsKey(), cmInsets(3, 10, 3, 8))
    defaults.put(JBUI.CurrentTheme.Editor.SearchToolbar.borderInsetsKey(), cmInsets(0))
    defaults.put(JBUI.CurrentTheme.Editor.ReplaceToolbar.borderInsetsKey(), cmInsets(1, 0))
    defaults.put(JBUI.CurrentTheme.Editor.SearchReplaceModePanel.borderInsetsKey(), cmInsets(3))
    // editor tabs
    defaults.put(JBUI.CurrentTheme.EditorTabs.tabInsetsKey(), cmInsets(-2, 4, -2, 4))
    defaults.put(JBUI.CurrentTheme.EditorTabs.verticalTabInsetsKey(), cmInsets(2, 8, 1, 6))
    defaults.put(JBUI.CurrentTheme.EditorTabs.tabContentInsetsActionsRightKey(), cmInsets(0))
    defaults.put(JBUI.CurrentTheme.EditorTabs.tabContentInsetsActionsLeftKey(), cmInsets(0))
    defaults.put(JBUI.CurrentTheme.EditorTabs.tabContentInsetsActionsNoneKey(), cmInsets(0))
    defaults.put(JBUI.CurrentTheme.EditorTabs.fontKey(), Supplier { JBFont.medium().asUIResource() })
    // banner
    defaults.put(JBUI.CurrentTheme.Editor.Notification.borderInsetsKey(), cmInsets(6, 12))
    defaults.put(JBUI.CurrentTheme.Editor.Notification.borderInsetsKeyWithoutStatus(), cmInsets(6, 16))
    // toolwindows
    defaults.put(JBUI.CurrentTheme.ToolWindow.headerHeightKey(), 32)
    defaults.put(JBUI.CurrentTheme.ToolWindow.headerFontKey(), Supplier { JBFont.medium().asUIResource() })
    // run, debug tabs
    defaults.put(JBUI.CurrentTheme.DebuggerTabs.tabHeightKey(), 32)
    defaults.put(JBUI.CurrentTheme.DebuggerTabs.fontKey(), Supplier { JBFont.medium().asUIResource() })
    // VCS log
    defaults.put(JBUI.CurrentTheme.VersionControl.Log.rowHeightKey(), 24)
    defaults.put(JBUI.CurrentTheme.VersionControl.Log.verticalPaddingKey(), 4)
    // VCS Combined Diff
    defaults.put(JBUI.CurrentTheme.VersionControl.CombinedDiff.mainToolbarInsetsKey(), cmInsets(1, 10))
    defaults.put(JBUI.CurrentTheme.VersionControl.CombinedDiff.fileToolbarInsetsKey(), cmInsets(7, 10))
  }
}

private fun cmSize(width: Int, height: Int): Dimension = Dimension(width, height)

@Suppress("UseDPIAwareInsets")
private fun cmInsets(all: Int): Insets = Insets(all, all, all, all)

@Suppress("UseDPIAwareInsets")
private fun cmInsets(topAndBottom: Int, leftAndRight: Int): Insets = Insets(topAndBottom, leftAndRight, topAndBottom, leftAndRight)

@Suppress("UseDPIAwareInsets")
private fun cmInsets(top: Int, left: Int, bottom: Int, right: Int): Insets = Insets(top, left, bottom, right)

private fun JBInsets.withTopAndBottom(topAndBottom: Int) = JBInsets(topAndBottom, unscaled.left, topAndBottom, unscaled.right)

private fun defaultNonLaFSchemeName(dark: Boolean) = if (dark) DarculaLaf.NAME else EditorColorsScheme.DEFAULT_SCHEME_NAME
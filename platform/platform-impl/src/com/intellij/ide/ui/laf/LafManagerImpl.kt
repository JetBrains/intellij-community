// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.ui.laf

import com.intellij.CommonBundle
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.runActivity
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.QuickChangeLookAndFeel
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.ui.*
import com.intellij.ide.ui.laf.SystemDarkThemeDetector.Companion.createParametrizedDetector
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.ide.ui.laf.intellij.IdeaPopupMenuUI
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorSchemesSorter
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.Groups
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.options.Scheme
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.ui.*
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.icons.getDisabledIcon
import com.intellij.ui.mac.MacFullScreenControlsManager
import com.intellij.ui.popup.HeavyWeightPopup
import com.intellij.ui.scale.JBUIScale.getFontScale
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.ui.scale.JBUIScale.scaleFontSize
import com.intellij.ui.scale.JBUIScale.setUserScaleFactor
import com.intellij.ui.svg.setSelectionColorPatcherProvider
import com.intellij.util.EventDispatcher
import com.intellij.util.FontUtil
import com.intellij.util.IJSwingUtilities
import com.intellij.util.SVGLoader.colorPatcherProvider
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.ui.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
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
private const val ELEMENT_PREFERRED_LIGHT_EDITOR_SCHEME: @NonNls String = "preferred-light-editor-scheme"
private const val ELEMENT_PREFERRED_DARK_EDITOR_SCHEME: @NonNls String = "preferred-dark-editor-scheme"
private const val ATTRIBUTE_EDITOR_SCHEME_NAME: @NonNls String = "editorSchemeId"
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
       useLoadedStateAsExisting = false,
       storages = [Storage(value = "laf.xml", usePathMacroManager = false)],
       category = SettingsCategory.UI,
       reportStatistic = false)
class LafManagerImpl(private val coroutineScope: CoroutineScope) : LafManager(), PersistentStateComponent<Element> {
  private val eventDispatcher = EventDispatcher.create(LafManagerListener::class.java)

  private var currentTheme: UIThemeLookAndFeelInfo? = null

  private var preferredLightThemeId: String? = null
  private var preferredDarkThemeId: String? = null
  private var preferredLightEditorSchemeId: String? = null
  private var preferredDarkEditorSchemeId: String? = null

  private val storedDefaults = HashMap<String?, MutableMap<String, Any?>>()
  private val lafComboBoxModel = SynchronizedClearableLazy {
    LafComboBoxModel(ThemeListProvider.getInstance().getShownThemes())
  }

  // SystemDarkThemeDetector must be created as part of LafManagerImpl initialization and not on demand because system listeners are added
  private var themeDetector: SystemDarkThemeDetector? = null
  private var isFirstSetup = true
  private var autodetect = false
  private var isRestartRequired = false

  // we remember the last used editor scheme for each laf to restore it after switching laf
  private var rememberSchemeForLaf = true
  private val lafToPreviousScheme = HashMap<String, String>()

  /**
   * Stores values of options from [UISettings] which were used to set up current LaF.
   */
  private var usedValuesOfUiOptions: List<Any?> = emptyList()

  val defaultFont: Font
    get() {
      return when {
               useInterFont() -> defaultInterFont
               UISettings.getInstance().overrideLafFonts || UISettingsUtils.getInstance().currentIdeScale != 1f -> storedLafFont
               else -> null
             } ?: JBFont.label()
    }

  companion object {
    private var ourTestInstance: LafManagerImpl? = null

    @OptIn(DelicateCoroutinesApi::class)
    @TestOnly
    fun getTestInstance(): LafManagerImpl? {
      if (ourTestInstance == null) {
        ourTestInstance = LafManagerImpl(GlobalScope)
      }
      return ourTestInstance
    }
  }

  override fun getDefaultLightLaf(): UIThemeLookAndFeelInfo = getDefaultLaf(isDark = false)

  override fun getDefaultDarkLaf(): UIThemeLookAndFeelInfo = getDefaultLaf(isDark = true)

  @Suppress("removal")
  override fun addLafManagerListener(listener: LafManagerListener) {
    eventDispatcher.addListener(listener)
  }

  @Suppress("removal")
  override fun removeLafManagerListener(listener: LafManagerListener) {
    eventDispatcher.removeListener(listener)
  }

  override fun initializeComponent() {
    // must be after updateUI
    isFirstSetup = false

    if (EDT.isCurrentThreadEdt()) {
      initInEdt(ExperimentalUI.getInstance())
      addListeners()
    }
    else {
      // expected that applyInitState will be called
    }
  }

  @Internal
  suspend fun applyInitState() {
    span("laf initialization in EDT", RawSwingDispatcher) {
      initInEdt(serviceAsync<ExperimentalUI>())
    }
    addListeners()
  }

  private fun initInEdt(experimentalUi: ExperimentalUI) {
    val theme = currentTheme!!
    if (!theme.isInitialized) {
      doSetLaF(theme = theme, installEditorScheme = false)
    }
    selectComboboxModel()

    runActivity("new ui configuration") {
      experimentalUi.lookAndFeelChanged()
    }

    updateUI(isFirstSetup = true)
    detectAndSyncLaf()
  }

  private fun addListeners() {
    UIThemeProvider.EP_NAME.addExtensionPointListener(service<LafDynamicPluginManager>().createUiThemeEpListener(manager = this))
    @Suppress("ObjectLiteralToLambda")
    ApplicationManager.getApplication().messageBus.connect(coroutineScope).subscribe(UISettingsListener.TOPIC, object : UISettingsListener {
      override fun uiSettingsChanged(uiSettings: UISettings) {
        val newValues = computeValuesOfUsedUiOptions()
        if (newValues != usedValuesOfUiOptions) {
          updateUI()
        }
      }
    })
  }

  private fun detectAndSyncLaf(async: Boolean = true) {
    if (autodetect) {
      val lafDetector = getGetOrCreateLafDetector()
      if (lafDetector.detectionSupported) {
        lafDetector.check(async)
      }
    }
  }

  private fun syncThemeAndEditorScheme(systemIsDark: Boolean, async: Boolean?) {
    syncTheme(systemIsDark = systemIsDark, async = async ?: true)
    syncEditorScheme(systemIsDark)
  }

  private fun syncTheme(systemIsDark: Boolean, async: Boolean) {
    if (!autodetect) {
      return
    }

    val currentTheme = currentTheme
    val currentIsDark = currentTheme == null || currentTheme.isDark
    val expectedTheme = if (systemIsDark) {
      preferredDarkThemeId?.let { UiThemeProviderListManager.getInstance().findThemeById(it) } ?: defaultDarkLaf
    }
    else {
      preferredLightThemeId?.let { UiThemeProviderListManager.getInstance().findThemeById(it) } ?: defaultLightLaf
    }
    if (currentIsDark != systemIsDark || currentTheme !== expectedTheme) {
      QuickChangeLookAndFeel.switchLafAndUpdateUI(/* lafManager = */ this, /* laf = */ expectedTheme, /* async = */ async, false, true)
    }
  }

  private fun syncEditorScheme(systemIsDark: Boolean) {
    if (!autodetect) {
      return
    }

    validatePreferredSchemes()

    val expectedScheme =
      (if (systemIsDark) preferredDarkEditorSchemeId else preferredLightEditorSchemeId)?.let {
        EditorColorsManager.getInstance().getScheme(it)
      }
      ?: associatedToPreferredLafOrDefaultEditorColorSchemeName(systemIsDark).let {
        EditorColorsManager.getInstance().getScheme(it)
      }

    SwingUtilities.invokeLater {
      val currentScheme = EditorColorsManager.getInstance().globalScheme
      if (expectedScheme != null && currentScheme.baseName != expectedScheme.baseName) {
        EditorColorsManager.getInstance().setGlobalScheme(expectedScheme)
      }
    }
  }

  private fun validatePreferredSchemes() {
    preferredDarkEditorSchemeId?.let {
      if (EditorColorsManager.getInstance().getScheme(it) == null) {
        preferredDarkEditorSchemeId = null
      }
    }

    preferredLightEditorSchemeId?.let {
      if (EditorColorsManager.getInstance().getScheme(it) == null) {
        preferredLightEditorSchemeId = null
      }
    }
  }

  private fun associatedToPreferredLafOrDefaultEditorColorSchemeName(isDark: Boolean): String {
    val theme = preferredOrDefaultLaf(isDark)

    return getPreviousSchemeForLaf(theme)?.baseName
           ?: theme.editorSchemeId
           ?: defaultNonLaFSchemeName(isDark)
  }

  private fun preferredOrDefaultLaf(isDark: Boolean): UIThemeLookAndFeelInfo =
    if (isDark) {
      preferredDarkThemeId?.let { UiThemeProviderListManager.getInstance().findThemeById(it) } ?: defaultDarkLaf
    }
    else {
      preferredLightThemeId?.let { UiThemeProviderListManager.getInstance().findThemeById(it) } ?: defaultLightLaf
    }

  override fun loadState(element: Element) {
    autodetect = element.getAttributeBooleanValue(ATTRIBUTE_AUTODETECT)
    preferredLightThemeId = element.getChild(ELEMENT_PREFERRED_LIGHT_LAF)?.getThemeNameWithReplacingDeprecated()
    preferredDarkThemeId = element.getChild(ELEMENT_PREFERRED_DARK_LAF)?.getThemeNameWithReplacingDeprecated()
    preferredLightEditorSchemeId = element.getChild(ELEMENT_PREFERRED_LIGHT_EDITOR_SCHEME)?.getAttributeValue(ATTRIBUTE_EDITOR_SCHEME_NAME)
    preferredDarkEditorSchemeId = element.getChild(ELEMENT_PREFERRED_DARK_EDITOR_SCHEME)?.getAttributeValue(ATTRIBUTE_EDITOR_SCHEME_NAME)

    val lafToPreviousScheme = HashMap<String, String>()
    val child = element.getChild(ELEMENT_LAFS_TO_PREVIOUS_SCHEMES)
    child?.let { lafToSchemeElement ->
      for (lafToScheme in lafToSchemeElement.getChildren(ELEMENT_LAF_TO_SCHEME)) {
        lafToPreviousScheme.put(lafToScheme.getAttributeValue(ATTRIBUTE_LAF), lafToScheme.getAttributeValue(ATTRIBUTE_SCHEME))
      }
    }

    if (lafToPreviousScheme.isEmpty()) {
      this.lafToPreviousScheme.clear()
    }
    else {
      this.lafToPreviousScheme.putAll(lafToPreviousScheme)
    }

    if (autodetect) {
      // must be created as part of LafManagerImpl initialization and not on demand because system listeners are added
      getGetOrCreateLafDetector()
    }

    if (isFirstSetup) {
      currentTheme = try {
        loadThemeState(element).get()
      }
      catch (e: Throwable) {
        LOG.error(e)
        null
      } ?: loadDefaultTheme().get()
    }
    else {
      val oldTheme = currentTheme
      val newThemeSupplier = loadThemeState(element)
      val newTheme = newThemeSupplier.get()!!
      if (newThemeSupplier == oldTheme) {
        currentTheme = newTheme
      }
      else {
        QuickChangeLookAndFeel.switchLafAndUpdateUI(/* lafManager = */ this,
                                                    /* laf = */ newTheme,
                                                    /* async = */ true,
                                                    /* force = */ true,
                                                    /* lockEditorScheme = */ true)
      }
    }
  }

  private fun loadThemeState(element: Element): Supplier<out UIThemeLookAndFeelInfo?> {
    val themeProviderManager = UiThemeProviderListManager.getInstance()

    element.getChild(ELEMENT_LAF)?.getThemeNameWithReplacingDeprecated()?.let {
      themeProviderManager.findThemeSupplierById(it)
    }?.let {
      return it
    }

    // We try to read `class-name` attribute for backward compatibilities IDE's before 2023.3.
    // Only for Darcula theme. We could drop it in future when we will stop bundle Darcula.
    return if (element.getChild("laf")?.getAttributeValue("class-name") == "com.intellij.ide.ui.laf.darcula.DarculaLaf") {
      Supplier { themeProviderManager.findThemeByName("Darcula") }
    }
    else {
      loadDefaultTheme()
    }
  }

  override fun noStateLoaded() {
    val theme = loadDefaultTheme().get()!!
    currentTheme = theme
    preferredLightThemeId = null
    preferredDarkThemeId = null
    preferredLightEditorSchemeId = null
    preferredDarkEditorSchemeId = null
    autodetect = false
  }

  override fun getState(): Element {
    val element = Element("state")
    if (autodetect) {
      element.setAttribute(ATTRIBUTE_AUTODETECT, java.lang.Boolean.toString(autodetect))
    }
    val currentTheme = currentTheme
    if (currentTheme?.id != getDefaultThemeId()) {
      val laf = (if (currentTheme is TempUIThemeLookAndFeelInfo) currentTheme.previousLaf else currentTheme)
      if (laf != null) {
        val child = Element(ELEMENT_LAF)
        child.setAttribute(ATTRIBUTE_THEME_NAME, laf.id)
        element.addContent(child)
      }
    }
    if (preferredLightThemeId != null && preferredLightThemeId !== defaultLightLaf.id) {
      val child = Element(ELEMENT_PREFERRED_LIGHT_LAF)
      child.setAttribute(ATTRIBUTE_THEME_NAME, preferredLightThemeId)
      element.addContent(child)
    }
    if (preferredDarkThemeId != null && preferredDarkThemeId !== defaultDarkLaf.id) {
      val child = Element(ELEMENT_PREFERRED_DARK_LAF)
      child.setAttribute(ATTRIBUTE_THEME_NAME, preferredDarkThemeId)
      element.addContent(child)
    }
    if (preferredDarkEditorSchemeId != null && preferredDarkEditorSchemeId !== defaultEditorSchemeName(true)) {
      val child = Element(ELEMENT_PREFERRED_DARK_EDITOR_SCHEME)
      child.setAttribute(ATTRIBUTE_EDITOR_SCHEME_NAME, preferredDarkEditorSchemeId)
      element.addContent(child)
    }
    if (preferredLightEditorSchemeId != null && preferredLightEditorSchemeId !== defaultEditorSchemeName(false)) {
      val child = Element(ELEMENT_PREFERRED_LIGHT_EDITOR_SCHEME)
      child.setAttribute(ATTRIBUTE_EDITOR_SCHEME_NAME, preferredLightEditorSchemeId)
      element.addContent(child)
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

  private fun Element.getThemeNameWithReplacingDeprecated(): String? {
    val currentTheme = this.getAttributeValue(ATTRIBUTE_THEME_NAME)
    if (ExperimentalUI.isNewUI() && currentTheme == "JetBrainsLightTheme") {
      val newThemeId = "ExperimentalLightWithLightHeader"
      this.setAttribute(ATTRIBUTE_THEME_NAME, newThemeId)
      return newThemeId
    }
    return currentTheme
  }

  override fun getInstalledLookAndFeels(): Array<UIManager.LookAndFeelInfo> {
    return UiThemeProviderListManager.getInstance().getLaFs().map { it as UIThemeLookAndFeelInfoImpl }.toList().toTypedArray()
  }

  override fun getInstalledThemes(): Sequence<UIThemeLookAndFeelInfo> {
    return UiThemeProviderListManager.getInstance().getLaFs()
  }

  override fun getLafComboBoxModel(): CollectionComboBoxModel<LafReference> = lafComboBoxModel.value

  internal fun updateLafComboboxModel() {
    val oldModel = lafComboBoxModel.valueIfInitialized
    lafComboBoxModel.drop()

    // Let the model wrapper know that it's time to recreate the wrapped model.
    oldModel?.removeAll()
  }

  private fun selectComboboxModel() {
    if (lafComboBoxModel.isInitialized()) {
      val theme = currentTheme!!
      lafComboBoxModel.value.selectedItem = LafReference(theme.name, theme.id)
    }
  }

  override fun findLaf(themeId: String): UIThemeLookAndFeelInfo {
    return UiThemeProviderListManager.getInstance().getDescriptors()
             .singleOrNull { it.id == themeId }
             ?.theme
             ?.get()
           ?: error("Theme not found for themeId: $themeId")
  }

  @Suppress("removal")
  override fun getCurrentLookAndFeel(): UIManager.LookAndFeelInfo? = currentTheme as UIThemeLookAndFeelInfoImpl?

  override fun getCurrentUIThemeLookAndFeel(): UIThemeLookAndFeelInfo? = currentTheme

  override fun getLookAndFeelReference(): LafReference = currentTheme!!.let {
    LafReference(it.name, it.id)
  }

  override fun getLookAndFeelCellRenderer(component: JComponent): ListCellRenderer<LafReference> {
    val welcomeMode = WelcomeFrame.getInstance() != null
    return listCellRenderer {
      toolTipText = null
      text(value.name)
      if (value.themeId.isEmpty()) {
        separator { }
      }
      else {
        for (group in lafComboBoxModel.value.groupedThemes.infos) {
          val theme = group.items.find { info -> info.id == value.themeId}
          if (theme != null) {
            if (theme.isRestartRequired()) {
              icon(AllIcons.General.Beta)
              if (!welcomeMode && value.themeId != currentTheme?.id && group.items.find { info -> info.id == currentTheme?.id} == null) {
                icon(getDisabledIcon(AllIcons.Actions.Restart, null))
                toolTipText = IdeBundle.message("ide.restart.required.comment")
              }
            }
            else if (!welcomeMode && value.themeId != currentTheme?.id && currentTheme?.isRestartRequired() == true) {
              icon(getDisabledIcon(AllIcons.Actions.Restart, null))
              toolTipText = IdeBundle.message("ide.restart.required.comment")
            }
            break
          }
        }

        val groupWithSameFirstItem = lafComboBoxModel.value.groupedThemes.infos.firstOrNull { value.themeId == it.items.firstOrNull()?.id }
        if (groupWithSameFirstItem != null) {
          separator {
            text = groupWithSameFirstItem.title
          }
        }
      }
    }
  }

  override fun createSettingsToolbar(): JComponent {
    val group = DefaultActionGroup(PreferredLafAndSchemeAction())
    val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, group, true)
    toolbar.targetComponent = toolbar.component
    toolbar.component.isOpaque = false
    return toolbar.component
  }

  private fun loadDefaultTheme(): Supplier<out UIThemeLookAndFeelInfo?> {
    // use HighContrast theme for IDE in Windows if HighContrast desktop mode is set
    if (SystemInfoRt.isWindows && Toolkit.getDefaultToolkit().getDesktopProperty("win.highContrast.on") == true) {
      UiThemeProviderListManager.getInstance().findThemeSupplierById(HIGH_CONTRAST_THEME_ID)?.let {
        return it
      }
    }
    return Supplier { defaultDarkLaf }
  }

  private fun getDefaultThemeId(): String {
    // use HighContrast theme for IDE in Windows if HighContrast desktop mode is set
    if (SystemInfoRt.isWindows && Toolkit.getDefaultToolkit().getDesktopProperty("win.highContrast.on") == true) {
      UiThemeProviderListManager.getInstance().findThemeSupplierById(HIGH_CONTRAST_THEME_ID)?.let {
        return HIGH_CONTRAST_THEME_ID
      }
    }
    return defaultDarkLaf.id
  }

  /**
   * Sets current LAF. The method doesn't update component hierarchy.
   */
  override fun setCurrentLookAndFeel(lookAndFeelInfo: UIThemeLookAndFeelInfo, lockEditorScheme: Boolean) {
    setLookAndFeelImpl(lookAndFeelInfo = lookAndFeelInfo, installEditorScheme = !lockEditorScheme)
  }

  /**
   * Sets current LAF. The method doesn't update component hierarchy.
   */
  private fun setLookAndFeelImpl(lookAndFeelInfo: UIThemeLookAndFeelInfo, installEditorScheme: Boolean) {
    val oldLaf = currentTheme

    EditorColorsManager.getInstance().activeVisibleScheme ?.let {
      rememberSchemeForLaf(it)
    }

    if (oldLaf !== lookAndFeelInfo && oldLaf != null) {
      oldLaf.dispose()
    }
    if (doSetLaF(lookAndFeelInfo, installEditorScheme)) {
      return
    }

    currentTheme = lookAndFeelInfo
    selectComboboxModel()
    if (!isFirstSetup && installEditorScheme) {
      updateEditorSchemeIfNecessary(oldLaf = oldLaf)
      UISettings.getInstance().fireUISettingsChanged()
      ActionToolbarImpl.updateAllToolbarsImmediately()
    }
    isFirstSetup = false

    isRestartRequired = checkRestart(lookAndFeelInfo, oldLaf)
  }

  private fun checkRestart(lookAndFeelInfo: UIThemeLookAndFeelInfo, oldLaf: UIThemeLookAndFeelInfo?): Boolean {
    if (WelcomeFrame.getInstance() != null) {
      return false
    }
    if (!lookAndFeelInfo.isRestartRequired() && oldLaf?.isRestartRequired() == false) {
      return false
    }
    if (lookAndFeelInfo.isRestartRequired() && oldLaf?.isRestartRequired() == true) {
      val infos = ThemeListProvider.getInstance().getShownThemes().infos
      val group = infos.find { info -> info.items.find { element -> element.id == lookAndFeelInfo.id } != null }
      if (group != null && group.items.find { element -> element.id == oldLaf.id } != null) {
        return false
      }
    }
    return true
  }

  @Internal
  override fun checkRestart() {
    if (isRestartRequired) {
      isRestartRequired = false

      if (PluginManagerConfigurable.showRestartDialog(IdeBundle.message("dialog.title.restart.required"), Function {
          IdeBundle.message("dialog.message.must.be.restarted.for.changes.to.take.effect",
                            ApplicationNamesInfo.getInstance().fullProductName)
        }) == Messages.YES) {
        ApplicationManagerEx.getApplicationEx().restart(true)
      }
    }
  }

  @Suppress("unused")
  @Internal
  fun updateLafNoSave(lookAndFeelInfo: UIThemeLookAndFeelInfo): Boolean {
    return doSetLaF(theme = lookAndFeelInfo as UIThemeLookAndFeelInfoImpl, installEditorScheme = false)
  }

  private fun doSetLaF(theme: UIThemeLookAndFeelInfo, installEditorScheme: Boolean): Boolean {
    if (!isFirstSetup) {
      colorPatcherProvider = null
      setSelectionColorPatcherProvider(null)
    }

    val lafAdapter = LookAndFeelThemeAdapter(
      base = LookAndFeelThemeAdapter.preInitializedBaseLaf.get() ?: createBaseLaF().also { it.initialize() },
      theme = theme,
    )

    // set L&F
    try {
      UIManager.setLookAndFeel(lafAdapter)
      if (installEditorScheme) {
        theme.installEditorScheme(getPreviousSchemeForLaf(theme))
      }
    }
    catch (e: Exception) {
      LOG.error(e)
      Messages.showMessageDialog(
        IdeBundle.message("error.cannot.set.look.and.feel", theme.id, e.message),
        CommonBundle.getErrorTitle(),
        Messages.getErrorIcon()
      )
      return true
    }

    if (SystemInfoRt.isMac) {
      installMacosXFonts(UIManager.getLookAndFeelDefaults())
    }
    else if (SystemInfoRt.isLinux) {
      installLinuxFonts(UIManager.getLookAndFeelDefaults())
    }
    return false
  }

  override fun applyDensity() {
    val uiSettings = UISettings.getInstance()
    val ideScale = uiSettings.currentIdeScale

    // need to temporarily reset this to correctly apply new size values
    uiSettings.currentIdeScale = 1f
    uiSettings.fireUISettingsChanged()
    setCurrentLookAndFeel(currentTheme!!, true)
    updateUI()
    uiSettings.currentIdeScale = ideScale
    uiSettings.fireUISettingsChanged()

    if (SystemInfo.isMac) {
      MacFullScreenControlsManager.updateForCompactMode()
    }
  }

  private fun updateEditorSchemeIfNecessary(oldLaf: UIThemeLookAndFeelInfo?) {
    val currentTheme = currentTheme
    if (oldLaf is TempUIThemeLookAndFeelInfo || currentTheme is TempUIThemeLookAndFeelInfo) {
      return
    }
    if (currentTheme?.editorSchemeId != null) {
      return
    }

    val editorColorManager = EditorColorsManager.getInstance() as EditorColorsManagerImpl
    val current = editorColorManager.globalScheme
    if (currentTheme != null) {
      getPreviousSchemeForLaf(currentTheme)?.let {
        editorColorManager.setGlobalScheme(scheme = it, processChangeSynchronously = true)
        return
      }
    }

    val dark = StartupUiUtil.isDarkTheme
    val wasUITheme = oldLaf != null
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
        properties.setValue(toSavedEditorThemeKey, current.name, if (dark) EditorColorsScheme.getDefaultSchemeName() else DarculaLaf.NAME)
      }
      editorColorManager.getScheme(targetScheme)?.let {
        editorColorManager.setGlobalScheme(it, processChangeSynchronously = true)
      }
    }
  }

  /**
   * Updates LAF of all windows. The method also updates font of components as it's configured in `UISettings`.
   */
  override fun updateUI() {
    updateUI(isFirstSetup = false)
  }

  private fun updateUI(isFirstSetup: Boolean) {
    val uiDefaults = UIManager.getLookAndFeelDefaults()
    // for JBColor
    uiDefaults.put("*cache", ConcurrentHashMap<String, Color>())
    fixPopupWeight()
    initInputMapDefaults(uiDefaults)
    patchLafFonts(uiDefaults)

    if (ExperimentalUI.isNewUI()) {
      applyDensityOnUpdateUi(uiDefaults)
    }

    // should be called last because this method modifies uiDefault values
    patchHiDPI(uiDefaults)
    // required for MigLayout logical pixels to work
    // super-huge DPI causes issues like IDEA-170295 if `laf.scaleFactor` property is missing
    uiDefaults.put("laf.scaleFactor", scale(1f))
    uiDefaults.put(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false))
    uiDefaults.put(RenderingHints.KEY_TEXT_LCD_CONTRAST, StartupUiUtil.getLcdContrastValue())
    uiDefaults.put(RenderingHints.KEY_FRACTIONALMETRICS, AppUIUtil.getAdjustedFractionalMetricsValue())
    usedValuesOfUiOptions = computeValuesOfUsedUiOptions()
    if (!isFirstSetup) {
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
    val currentScale = uiSettings.currentIdeScale
    val overrideLafFonts = uiSettings.overrideLafFonts
    val useInterFont = useInterFont()
    LOG.debug { "patchLafFonts: scale=$currentScale, overrideLafFonts=$overrideLafFonts, useInterFont=$useInterFont" }
    if (overrideLafFonts || currentScale != 1f) {
      storeOriginalFontDefaults(uiDefaults)
      val fontFace = if (overrideLafFonts) uiSettings.fontFace else defaultFont.family
      val fontSize = (if (overrideLafFonts) uiSettings.fontSize2D else defaultFont.size2D) * currentScale
      LOG.debug { "patchLafFonts: using font '$fontFace' with size $fontSize" }
      initFontDefaults(uiDefaults, getFontWithFallback(fontFace, Font.PLAIN, fontSize))
      val userScaleFactor = if (useInterFont) fontSize / INTER_SIZE else getFontScale(fontSize)
      LOG.debug { "patchLafFonts: computed user scale factor $userScaleFactor from font size $fontSize" }
      setUserScaleFactor(userScaleFactor)
    }
    else if (useInterFont) {
      storeOriginalFontDefaults(uiDefaults)
      val interFont = defaultInterFont
      LOG.debug { "patchLafFonts: using Inter font with size ${interFont.size2D}" }
      initFontDefaults(uiDefaults, interFont)
      val userScaleFactor = defaultUserScaleFactor
      LOG.debug { "patchLafFonts: setting the default scale factor $userScaleFactor" }
      setUserScaleFactor(userScaleFactor)
    }
    else {
      restoreOriginalFontDefaults(uiDefaults)
    }
  }

  private val defaultUserScaleFactor: Float
    get() {
      val font = storedLafFont ?: JBFont.label()
      return getFontScale(font.size.toFloat())
    }

  private val defaultInterFont: FontUIResource
    get() {
      val userScaleFactor = defaultUserScaleFactor
      return getFontWithFallback(INTER_NAME, Font.PLAIN, scaleFontSize(INTER_SIZE.toFloat(), userScaleFactor).toFloat())
    }

  private val storedLafFont: Font?
    get() = storedDefaults.get(currentTheme?.id)?.get("Label.font") as Font?

  private fun restoreOriginalFontDefaults(defaults: UIDefaults) {
    val lafDefaults = storedDefaults.get(currentTheme?.id)
    if (lafDefaults != null) {
      for (resource in patchableFontResources) {
        defaults.put(resource, lafDefaults.get(resource))
      }
    }
    val fontScale = getFontScale(fontSize = JBFont.label().size.toFloat())
    LOG.debug { "restoreOriginalFontDefaults: setting the user scale factor to $fontScale" }
    setUserScaleFactor(fontScale)
  }

  private fun storeOriginalFontDefaults(defaults: UIDefaults) {
    val key = currentTheme?.id
    var lafDefaults = storedDefaults.get(key)
    if (lafDefaults == null) {
      lafDefaults = HashMap()
      for (resource in patchableFontResources) {
        lafDefaults.put(resource, defaults.get(resource))
      }
      storedDefaults.put(key, lafDefaults)
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

    if (value) {
      rememberSchemeForLaf(EditorColorsManager.getInstance().globalScheme)
    }

    autodetect = value

    // Notify autodetect is changed
    notifyLookAndFeelChanged()

    if (autodetect) {
      detectAndSyncLaf()
    }
  }

  override fun getAutodetectSupported(): Boolean = getGetOrCreateLafDetector().detectionSupported

  private fun getGetOrCreateLafDetector(): SystemDarkThemeDetector {
    var result = themeDetector
    if (result == null) {
      result = createParametrizedDetector(::syncThemeAndEditorScheme)
      themeDetector = result
    }

    return result
  }

  override fun setPreferredDarkLaf(value: UIThemeLookAndFeelInfo) {
    preferredDarkThemeId = value.id
  }

  override fun setPreferredLightLaf(value: UIThemeLookAndFeelInfo) {
    preferredLightThemeId = value.id
  }

  override fun resetPreferredEditorColorScheme() {
    preferredDarkEditorSchemeId = null
    preferredLightEditorSchemeId = null
  }

  private fun getPreviousSchemeForLaf(lookAndFeelInfo: UIThemeLookAndFeelInfo): EditorColorsScheme? {
    val schemeName = lafToPreviousScheme.get(lookAndFeelInfo.id)
                     ?: lafToPreviousScheme.get(lookAndFeelInfo.name)
                     ?: return null
    return EditorColorsManager.getInstance().getScheme(schemeName)
  }

  override fun setRememberSchemeForLaf(rememberSchemeForLaf: Boolean) {
    this.rememberSchemeForLaf = rememberSchemeForLaf
  }

  override fun rememberSchemeForLaf(scheme: EditorColorsScheme) {
    if (!rememberSchemeForLaf || autodetect) {
      return
    }

    val theme = currentTheme ?: return
    // Remove the mapping previously imported from 2023.2.
    lafToPreviousScheme.remove(theme.name)
    // Classic Light color scheme has id `EditorColorsScheme.DEFAULT_SCHEME_NAME` - save it as is
    if (scheme.isDefaultForTheme(theme)) {
      lafToPreviousScheme.remove(theme.id)
    }
    else {
      lafToPreviousScheme.put(theme.id, scheme.name)
    }
  }

  internal fun applyScheduledLaF(newLaF: UIThemeLookAndFeelInfo) {
    setLookAndFeelImpl(lookAndFeelInfo = newLaF, installEditorScheme = true)
    JBColor.setDark(newLaF.isDark)
    updateUI()
  }

  private inner class PreferredLafAndSchemeAction : DefaultActionGroup(PreferredLafAction(), PreferredEditorColorSchemeAction()), DumbAware {
    init {
      isPopup = true
      templatePresentation.icon = AllIcons.General.GearPlain
      templatePresentation.text = IdeBundle.message("preferred.theme.and.editor.color.scheme.text")
      templatePresentation.description = IdeBundle.message("preferred.theme.and.editor.color.scheme.description")
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabled = getInstance().autodetect
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  private inner class PreferredLafAction : DefaultActionGroup(), DumbAware {
    init {
      isPopup = true
      templatePresentation.text = IdeBundle.message("preferred.theme.text")
      templatePresentation.description = IdeBundle.message("preferred.theme.description")
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      val lightLaFs = ArrayList<UIThemeLookAndFeelInfo>()
      val darkLaFs = ArrayList<UIThemeLookAndFeelInfo>()
      for (lafInfo in ThemeListProvider.getInstance().getShownThemes().infos.asSequence().flatMap { it.items }) {
        if (lafInfo.isDark) {
          darkLaFs.add(lafInfo)
        }
        else {
          lightLaFs.add(lafInfo)
        }
      }

      return (createThemeActions(IdeBundle.message("preferred.theme.dark.header"), darkLaFs, isDark = true) +
              createThemeActions(IdeBundle.message("preferred.theme.light.header"), lightLaFs, isDark = false) +
              listOf( Separator(), GetMoreLafAction())).toTypedArray()
    }

    private fun createThemeActions(
      separatorText: @NlsContexts.Separator String,
      lafs: List<UIThemeLookAndFeelInfo>,
      isDark: Boolean,
    ): Collection<AnAction> {
      if (lafs.isEmpty()) {
        return emptyList()
      }

      val result = ArrayList<AnAction>()
      result.add(Separator.create(separatorText))
      lafs.mapTo(result) {
        LafToggleAction(name = it.name, themeId = it.id, isDark = isDark)
      }
      return result
    }
  }

  private class GetMoreLafAction : DumbAwareAction(IdeBundle.message("link.get.more.themes")) {
    override fun actionPerformed(e: AnActionEvent) {
      val themeTag = "/tag:Theme"
      val settings = Settings.KEY.getData(e.dataContext)

      if (settings == null) {
        ShowSettingsUtil.getInstance().showSettingsDialog(ProjectManager.getInstance().defaultProject,
                                                          PluginManagerConfigurable::class.java) { c: PluginManagerConfigurable ->
          c.enableSearch(themeTag)
        }
      }
      else {
        settings.select(settings.find("preferences.pluginManager"), themeTag)
      }
    }

  }

  private inner class PreferredEditorColorSchemeAction : DefaultActionGroup(PreferredDarkEditorColorSchemeAction(),
                                                                            PreferredLightEditorColorSchemeAction()),
                                                         DumbAware {
    init {
      isPopup = true
      templatePresentation.text = IdeBundle.message("preferred.editor.color.scheme.text")
      templatePresentation.description = IdeBundle.message("preferred.editor.color.scheme.description")
    }
  }

  private inner class PreferredLightEditorColorSchemeAction :
    PreferredEditorColorSchemeBaseAction(IdeBundle.message("preferred.editor.color.scheme.light.header"), false), DumbAware
  private inner class PreferredDarkEditorColorSchemeAction :
    PreferredEditorColorSchemeBaseAction(IdeBundle.message("preferred.editor.color.scheme.dark.header"), true), DumbAware

  private open inner class PreferredEditorColorSchemeBaseAction(@Nls title: String, val isDark: Boolean) : DefaultActionGroup(), DumbAware {
    init {
      isPopup = true
      templatePresentation.text = title
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      val groups = EditorColorSchemesSorter.getInstance().getOrderedSchemesFromSequence(EditorColorsManager.getInstance().allSchemes.asSequence())

      return groups.infos.flatMap { group ->
        createSchemeActions(group.title, group.items, isDark)
      }.toTypedArray()
    }

    private fun createSchemeActions(
      separatorText: @NlsContexts.Separator String?,
      schemes: List<EditorColorsScheme>,
      isDark: Boolean,
    ): Collection<AnAction> {
      if (schemes.isEmpty()) {
        return emptyList()
      }

      val result = ArrayList<AnAction>()
      result.add(Separator.create(separatorText))
      schemes.mapTo(result) { SchemeToggleAction(it, isDark = isDark) }
      return result
    }

    private inner class SchemeToggleAction(
      val scheme: EditorColorsScheme,
      private val isDark: Boolean,
    )
      : DumbAwareToggleAction(scheme.displayName) {

      override fun getActionUpdateThread() = ActionUpdateThread.BGT

      override fun isSelected(e: AnActionEvent): Boolean {
        return ((if (isDark) preferredDarkEditorSchemeId else preferredLightEditorSchemeId)
                ?: associatedToPreferredLafOrDefaultEditorColorSchemeName(isDark)) == scheme.baseName
      }

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        if (isDark) {
          if (preferredDarkEditorSchemeId != scheme.baseName) {
            preferredDarkEditorSchemeId = scheme.baseName
            detectAndSyncLaf()
          }
        }
        else if (preferredLightThemeId != scheme.baseName) {
          preferredLightEditorSchemeId = scheme.baseName
          detectAndSyncLaf()
        }
      }
    }
  }

  private fun defaultEditorSchemeName(isDark: Boolean) =
    (if (isDark) defaultDarkLaf.editorSchemeId else defaultLightLaf.editorSchemeId)
    ?: defaultNonLaFSchemeName(isDark)

  private inner class LafToggleAction(
    name: @Nls String?,
    private val themeId: String,
    private val isDark: Boolean,
  ) : DumbAwareToggleAction(name) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent): Boolean {
      return if (isDark) {
        (preferredDarkThemeId ?: defaultDarkLaf.id) == themeId
      }
      else {
        (preferredLightThemeId ?: defaultLightLaf.id) == themeId
      }
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (isDark) {
        if (preferredDarkThemeId != themeId) {
          preferredDarkThemeId = themeId.takeIf { it != defaultDarkLaf.id }
          detectAndSyncLaf(false)
        }
      }
      else if (preferredLightThemeId != themeId) {
        preferredLightThemeId = themeId.takeIf { it != defaultLightLaf.id }
        detectAndSyncLaf(false)
      }
    }
  }
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
      catch (_: InternalError) {
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

private fun patchHiDPI(defaults: UIDefaults) {
  val prevScaleVal = defaults.get("hidpi.scaleFactor")
  // used to normalize previously patched values
  val prevScale = if (prevScaleVal == null) 1f else prevScaleVal as Float

  // fix predefined row height if default system font size is not expected
  val prevRowHeightScale = prevScale
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
  val valuesToScale = mutableMapOf<Any, () -> Any>()
  // Concurrent creation of UI components can lead to the modification of the 'defaults' map.
  // To avoid ConcurrentModificationException-s, we perform the iteration under lock ('forEach' uses it internally).
  // And to avoid a potential deadlock, we scale the values in a separate pass (deadlock is possible as scaling itself can use UIDefaults:
  // JBUIScale.scale → computeUserScaleFactor → computeSystemScaleFactor → getSystemFontData → computeSystemFontData → UIManager.getFont)
  defaults.forEach { key, value ->
    val keyAsString = key.toString()
    if (value is Dimension) {
      if (value is UIResource || dimensionKeys.contains(keyAsString)) {
        valuesToScale[key] = { JBDimension.size(value).asUIResource() }
      }
    }
    else if (value is Insets) {
      if (value is UIResource) {
        valuesToScale[key] = { JBInsets.create((value as Insets)).asUIResource() }
      }
    }
    else if (value is Int) {
      if (keyAsString.endsWith(".maxGutterIconWidth") || intKeys.contains(keyAsString)) {
        val normValue = (value / prevScale).toInt()
        valuesToScale[key] = { Integer.valueOf(scale(normValue)) }
      }
    }
  }
  for ((key, valueSupplier) in valuesToScale) {
    defaults.put(key, valueSupplier())
  }
  defaults.put("hidpi.scaleFactor", scale(1f))
}

private fun patchRowHeight(defaults: UIDefaults, key: String, prevScale: Float) {
  val value = defaults.get(key)
  val rowHeight = if (value is Int) value else 0
  if (rowHeight <= 0) {
    LOG.warn("$key = $value in ${UIManager.getLookAndFeel().name}; it may lead to performance degradation")
  }
  val custom = intSystemPropertyValue("ide.override.$key", -1)
  defaults.put(key, if (custom >= 0) scale(custom) else if (rowHeight <= 0) 0 else scale((rowHeight / prevScale).toInt()))
}

private fun intSystemPropertyValue(name: String, defaultValue: Int): Int = runCatching {
  System.getProperty(name)?.toInt() ?: defaultValue
}.getOrNull() ?: defaultValue

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

private fun useInterFont(): Boolean {
  return ((ExperimentalUI.isNewUI() && SystemInfo.isJetBrainsJvm) || Registry.`is`("ide.ui.font.force.use.inter.font", false))
         && !shouldFallbackToSystemFont
}

private val LANGUAGE_WITH_PACK_LIST = listOf(Locale.CHINESE.language, Locale.KOREAN.language, Locale.JAPANESE.language)
private val shouldFallbackToSystemFont: Boolean get() = LANGUAGE_WITH_PACK_LIST.contains(Locale.getDefault().language)

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

private fun applyDensityOnUpdateUi(uiDefaults: UIDefaults) {
  val densityKey = "ui.density"
  val oldDensityName = uiDefaults.get(densityKey) as? String
  val newDensity = UISettings.getInstance().uiDensity
  if (oldDensityName == newDensity.name) {
    // re-applying the same density would break HiDPI-scalable values like Tree.rowHeight
    return
  }

  uiDefaults.put(densityKey, newDensity.name)
  if (newDensity == UIDensity.DEFAULT) {
    // Special case: we need to set this one to its default value even in non-compact mode, UNLESS it was already set by the theme.
    // If it's null, it can't be properly patched in patchRowHeight, which looks ugly with larger UI fonts.
    val vcsLogHeight = uiDefaults.get(JBUI.CurrentTheme.VersionControl.Log.rowHeightKey())
    // don't want to rely on putIfAbsent here, as UIDefaults is a rather messy combination of multiple hash tables
    if (vcsLogHeight == null) {
      uiDefaults.put(JBUI.CurrentTheme.VersionControl.Log.rowHeightKey(), JBUI.CurrentTheme.VersionControl.Log.defaultRowHeight())
    }
  }

  if (newDensity == UIDensity.COMPACT) {
    val compactValues = mutableMapOf<String, Any>()
    uiDefaults.forEach { key, value ->
      if ((key as? String?)?.endsWith(".compact") == true) {
        compactValues[key.removeSuffix(".compact")] = value
      }
    }
    uiDefaults.putAll(compactValues)
  }
}

@JvmField
internal val patchableFontResources: Array<String> = arrayOf("Button.font", "ToggleButton.font", "RadioButton.font",
                                                             "CheckBox.font", "ColorChooser.font", "ComboBox.font", "Label.font",
                                                             "List.font", "MenuBar.font",
                                                             "MenuItem.font",
                                                             "MenuItem.acceleratorFont", "RadioButtonMenuItem.font",
                                                             "CheckBoxMenuItem.font", "Menu.font",
                                                             "PopupMenu.font", "OptionPane.font",
                                                             "Panel.font", "ProgressBar.font", "ScrollPane.font", "Viewport.font",
                                                             "TabbedPane.font",
                                                             "Table.font", "TableHeader.font",
                                                             "TextField.font", "FormattedTextField.font", "Spinner.font",
                                                             "PasswordField.font",
                                                             "TextArea.font", "TextPane.font", "EditorPane.font",
                                                             "TitledBorder.font", "ToolBar.font", "ToolTip.font", "Tree.font")

internal fun initFontDefaults(defaults: UIDefaults, uiFont: FontUIResource) {
  val textFont = FontUIResource(uiFont)
  val monoFont = FontUIResource("Monospaced", Font.PLAIN, uiFont.size)
  for (fontResource in patchableFontResources) {
    defaults.put(fontResource, uiFont)
  }

  defaults.put("PasswordField.font", textFont)
  defaults.put("TextArea.font", monoFont)
  defaults.put("TextPane.font", textFont)
  defaults.put("EditorPane.font", textFont)
}

private fun computeValuesOfUsedUiOptions(): List<Any?> {
  val uiSettings = UISettings.getInstance()
  return listOf(
    uiSettings.overrideLafFonts,
    uiSettings.fontFace,
    uiSettings.fontSize2D,
    uiSettings.ideAAType,
    uiSettings.editorAAType,
    uiSettings.ideScale,
    uiSettings.presentationModeIdeScale,
  )
}

private fun installMacosXFonts(defaults: UIDefaults) {
  @Suppress("SpellCheckingInspection") val face = "Helvetica Neue"
  // ui font
  initFontDefaults(defaults, getFont(face, 13, Font.PLAIN))
  defaults.replaceAll { key, value ->
    if (key is String && key.endsWith("font", ignoreCase = true) && !key.contains("Menu") &&
        value is FontUIResource && (value.family == "Lucida Grande" || value.family == "Serif")) {
      getFont(face, value.size, value.style)
    }
    else {
      value
    }
  }
  defaults.put("TableHeader.font", getFont(face, 11, Font.PLAIN))
  @Suppress("SpellCheckingInspection") val buttonFont = getFont("Helvetica Neue", 13, Font.PLAIN)
  defaults.put("Button.font", buttonFont)
  val menuFont = getFont("Lucida Grande", 13, Font.PLAIN)
  defaults.put("Menu.font", menuFont)
  defaults.put("MenuItem.font", menuFont)
  defaults.put("MenuItem.acceleratorFont", menuFont)
}

private sealed interface DefaultThemeStrategy {
  fun getPlatformDefaultId(isDark: Boolean): String

  fun getProductDefaultId(isDark: Boolean, appInfo: ApplicationInfoEx): String?
}

private data object DefaultNewUiThemeStrategy : DefaultThemeStrategy {
  override fun getPlatformDefaultId(isDark: Boolean) = if (isDark) "ExperimentalDark" else "ExperimentalLight"

  override fun getProductDefaultId(isDark: Boolean, appInfo: ApplicationInfoEx): String? {
    return if (isDark) appInfo.defaultDarkLaf else appInfo.defaultLightLaf
  }
}

private data object DefaultClassicThemeStrategy : DefaultThemeStrategy {
  override fun getPlatformDefaultId(isDark: Boolean) = if (isDark) "Darcula" else "JetBrainsLightTheme"

  override fun getProductDefaultId(isDark: Boolean, appInfo: ApplicationInfoEx): String? {
    return if (isDark) appInfo.defaultClassicDarkLaf else appInfo.defaultClassicLightLaf
  }
}

internal fun getDefaultLaf(isDark: Boolean): UIThemeLookAndFeelInfo {
  val themeListManager = UiThemeProviderListManager.getInstance()

  val isNewUi = ExperimentalUI.isNewUI()
  val strategy = if (isNewUi) DefaultNewUiThemeStrategy else DefaultClassicThemeStrategy
  var id = strategy.getProductDefaultId(isDark, ApplicationInfoEx.getInstanceEx())
  if (id != null) {
    val theme = themeListManager.findThemeById(id) ?: themeListManager.findThemeByName(id)
    if (theme == null) {
      LOG.error("Default theme not found(id=$id, isDark=$isDark, isNewUI=$isNewUi)")
    }
    else {
      return theme
    }
  }

  id = strategy.getPlatformDefaultId(isDark)
  return themeListManager.findThemeById(id) ?: error("Default theme not found(id=$id, isDark=$isDark, isNewUI=$isNewUi)")
}

private class LafComboBoxModel(val groupedThemes: Groups<UIThemeLookAndFeelInfo>) : CollectionComboBoxModel<LafReference>(
  groupedThemes.infos.asSequence().flatMap { it.items }.map { LafReference(it.name, it.id) }.toList()
)

private val Scheme.baseName: String get() = Scheme.getBaseName(name)
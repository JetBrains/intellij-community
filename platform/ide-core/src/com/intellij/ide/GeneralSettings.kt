// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.ide.ui.UINumericRange
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.PlatformUtils
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Transient
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.SystemDependent

private const val SHOW_TIPS_ON_STARTUP_DEFAULT_VALUE_PROPERTY = "ide.show.tips.on.startup.default.value"
private const val CONFIGURED_PROPERTY = "GeneralSettings.initiallyConfigured"

@Suppress("unused", "EnumEntryName")
@State(name = "GeneralSettings", storages = [Storage(GeneralSettings.IDE_GENERAL_XML)], category = SettingsCategory.SYSTEM)
class GeneralSettings : PersistentStateComponent<GeneralSettingsState> {
  private var state = GeneralSettingsState()

  @get:Deprecated("Use {@link GeneralLocalSettings#getBrowserPath()} instead.")
  val browserPath: String?
    get() = state.browserPath

  var isShowTipsOnStartup: Boolean
    get() {
      return state.showTipsOnStartup
             ?: java.lang.Boolean.parseBoolean(System.getProperty(SHOW_TIPS_ON_STARTUP_DEFAULT_VALUE_PROPERTY, "true"))
    }
    set(value) {
      state.showTipsOnStartup = value
    }

  var isReopenLastProject: Boolean
    get() = state.reopenLastProject
    set(value) {
      state.reopenLastProject = value
    }

  var isSyncOnFrameActivation: Boolean
    get() = state.autoSyncFiles
    set(value) {
      state.autoSyncFiles = value
    }

  var isSaveOnFrameDeactivation: Boolean
    get() = state.autoSaveFiles
    set(value) {
      state.autoSaveFiles = value
    }

  /**
   * @return `true` if IDE saves all files after "idle" timeout.
   */
  var isAutoSaveIfInactive: Boolean
    get() = state.autoSaveIfInactive
    set(value) {
      val changed = state.autoSaveIfInactive != value
      state.autoSaveIfInactive = value
      if (changed) {
        propertyChanged(PropertyNames.autoSaveIfInactive)
      }
    }

  var isUseSafeWrite: Boolean
    get() = state.isUseSafeWrite
    set(value) {
      state.isUseSafeWrite = value
    }

  private val _propertyChangedFlow = MutableSharedFlow<PropertyNames>(extraBufferCapacity = 16,
                                                                      onBufferOverflow = BufferOverflow.DROP_OLDEST)

  val propertyChangedFlow: Flow<PropertyNames> = _propertyChangedFlow.asSharedFlow()

  //fun propertyChangedFlow()

  @get:Deprecated("Use {@link GeneralLocalSettings#getUseDefaultBrowser()} instead.")
  val isUseDefaultBrowser: Boolean
    get() = state.useDefaultBrowser

  var isSearchInBackground: Boolean
    get() = state.searchInBackground
    set(value) {
      state.searchInBackground = value
    }

  var isConfirmExit: Boolean
    get() = state.confirmExit
    set(value) {
      state.confirmExit = value
    }

  var isShowWelcomeScreen: Boolean
    get() = state.isShowWelcomeScreen
    set(value) {
      state.isShowWelcomeScreen = value
    }

  /**
   * [GeneralSettings.OPEN_PROJECT_NEW_WINDOW] if a new project should be opened in new window
   * [GeneralSettings.OPEN_PROJECT_SAME_WINDOW] if a new project should be opened in same window
   * [GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH] if a new project should be attached
   * [GeneralSettings.OPEN_PROJECT_ASK] if a confirmation dialog should be shown
   */
  @get:OpenNewProjectOption
  var confirmOpenNewProject: Int
    get() = state.confirmOpenNewProject2 ?: defaultConfirmNewProject()
    set(value) {
      state.confirmOpenNewProject2 = value
    }


  var processCloseConfirmation: ProcessCloseConfirmation
    get() = state.processCloseConfirmation
    set(value) {
      state.processCloseConfirmation = value
    }

  init {
    val app = ApplicationManager.getApplication()
    if (app != null && !app.isHeadlessEnvironment &&
        (PlatformUtils.isPyCharmEducational() || PlatformUtils.isRubyMine() || PlatformUtils.isWebStorm())) {
      val propertyManager = PropertiesComponent.getInstance()
      if (!propertyManager.isValueSet(CONFIGURED_PROPERTY)) {
        propertyManager.setValue(CONFIGURED_PROPERTY, true)
        state.showTipsOnStartup = false
      }
    }
  }

  companion object {
    const val IDE_GENERAL_XML = "ide.general.xml"
    const val OPEN_PROJECT_ASK = -1
    const val OPEN_PROJECT_NEW_WINDOW = 0
    const val OPEN_PROJECT_SAME_WINDOW = 1
    const val OPEN_PROJECT_SAME_WINDOW_ATTACH = 2
    @Suppress("SpellCheckingInspection")
    const val SUPPORT_SCREEN_READERS = "ide.support.screenreaders.enabled"
    private val SUPPORT_SCREEN_READERS_OVERRIDDEN = getSupportScreenReadersOverridden()

    val SAVE_FILES_AFTER_IDLE_SEC: UINumericRange = UINumericRange(15, 1, 300)

    @JvmStatic
    fun getInstance(): GeneralSettings = ApplicationManager.getApplication().service<GeneralSettings>()

    private fun getSupportScreenReadersOverridden(): Boolean? = System.getProperty(SUPPORT_SCREEN_READERS)?.toBoolean()

    fun isSupportScreenReadersOverridden(): Boolean = SUPPORT_SCREEN_READERS_OVERRIDDEN != null

    fun defaultConfirmNewProject(): Int = OPEN_PROJECT_ASK
  }

  enum class PropertyNames {
    inactiveTimeout,
    autoSaveIfInactive,
    supportScreenReaders,
  }

  var isSupportScreenReaders: Boolean
    get() = SUPPORT_SCREEN_READERS_OVERRIDDEN ?: state.supportScreenReaders
    set(value) {
      val changed = state.supportScreenReaders != value
      state.supportScreenReaders = value
      if (changed) {
        propertyChanged(PropertyNames.supportScreenReaders)
      }
    }

  /**
   * @return timeout in seconds after which IDE saves all files if there was no user activity.
   * The method always returns positive (more than zero) value.
   */
  var inactiveTimeout: Int
    get() = SAVE_FILES_AFTER_IDLE_SEC.fit(state.inactiveTimeout)
    set(value) {
      val newValue = SAVE_FILES_AFTER_IDLE_SEC.fit(value)
      val changed = state.inactiveTimeout != newValue
      state.inactiveTimeout = newValue
      if (changed) {
        propertyChanged(PropertyNames.inactiveTimeout)
      }
    }

  private fun propertyChanged(property: PropertyNames) {
    check(_propertyChangedFlow.tryEmit(property))
  }

  override fun getState(): GeneralSettingsState = state

  override fun loadState(state: GeneralSettingsState) {
    this.state = state
  }

  @Suppress("UNUSED_PARAMETER")
  @get:Deprecated("unused")
  @get:Transient
  @set:Deprecated("unused")
  var isConfirmExtractFiles: Boolean
    get() = true
    set(value) {}

  @MagicConstant(intValues = [OPEN_PROJECT_ASK.toLong(), OPEN_PROJECT_NEW_WINDOW.toLong(), OPEN_PROJECT_SAME_WINDOW.toLong(), OPEN_PROJECT_SAME_WINDOW_ATTACH.toLong()])
  internal annotation class OpenNewProjectOption

  @get:Deprecated("Use {@link GeneralLocalSettings#getDefaultProjectDirectory()} instead.")
  val defaultProjectDirectory: @SystemDependent String?
    get() = state.defaultProjectDirectory
}

data class GeneralSettingsState(
  @field:OptionTag("myDefaultProjectDirectory")
  @JvmField
  var defaultProjectDirectory: String? = "",

  @JvmField
  var browserPath: String? = "",

  @JvmField
  var showTipsOnStartup: Boolean? = null,
  @JvmField
  var reopenLastProject: Boolean = true,

  @JvmField
  var autoSyncFiles: Boolean = true,

  @JvmField
  var autoSaveFiles: Boolean = true,
  @JvmField
  var autoSaveIfInactive: Boolean = false,

  @JvmField
  var isUseSafeWrite: Boolean = true,

  @JvmField
  var useDefaultBrowser: Boolean = true,
  @JvmField
  var searchInBackground: Boolean = false,
  @JvmField
  var confirmExit: Boolean = true,
  @JvmField
  var isShowWelcomeScreen: Boolean = true,

  @ReportValue
  @JvmField
  var confirmOpenNewProject2: Int? = null,

  @JvmField
  var processCloseConfirmation: ProcessCloseConfirmation = ProcessCloseConfirmation.ASK,

  @JvmField
  var inactiveTimeout: Int = 15,

  @JvmField
  var supportScreenReaders: Boolean = false
)

enum class ProcessCloseConfirmation {
  ASK,
  TERMINATE,
  DISCONNECT
}
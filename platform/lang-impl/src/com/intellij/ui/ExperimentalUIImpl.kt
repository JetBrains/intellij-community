// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.actions.DistractionFreeModeController
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.ide.ui.*
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.IconPathPatcher
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

private val LOG: Logger
  get() = logger<ExperimentalUI>()

private class ExperimentalUIImpl : ExperimentalUI() {
  private val epIconMapperSuppressor = ExtensionPointName<Any>("com.intellij.iconMapperSuppressor")
  private var shouldUnsetNewUiSwitchKey: Boolean = true
  private val isIconPatcherSet = AtomicBoolean()
  private val isFirstCheck = AtomicBoolean(true)
  private val isResetLaf = AtomicBoolean()

  override fun earlyInitValue(): Boolean {
    val prevNewUI = super.earlyInitValue() && !forcedSwitchedUi
    val newUi = !epIconMapperSuppressor.hasAnyExtensions()

    if (isFirstCheck.compareAndSet(true, false)) {
      changeValue(prevNewUI, newUi)
    }

    return newUi
  }

  private fun changeValue(prevNewUi: Boolean, newUi: Boolean) {
    val application = ApplicationManager.getApplication()
    if (application.isHeadlessEnvironment || application.isUnitTestMode || !LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred) {
      return
    }

    val enabled: Boolean

    if (prevNewUi && !newUi) {
      isResetLaf.set(true)

      enabled = false

      LOG.info("=== UI: new -> old ===")
    }
    else if (!prevNewUi && newUi) {
      isResetLaf.set(true)
      wasThemeReset = true

      enabled = true
      setNewUiUsed()

      if (!DistractionFreeModeController.shouldMinimizeCustomHeader()) {
        UISettings.getInstance().hideToolStripes = false
      }

      LOG.info("=== UI: old -> new ===")
    }
    else {
      return
    }

    EP_LISTENER.forEachExtensionSafe {
      it.changeUI(enabled)
    }

    try {
      EarlyAccessRegistryManager.setBoolean(KEY, enabled)
      EarlyAccessRegistryManager.syncAndFlush()
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

  override fun lookAndFeelChanged() {
    if (isNewUI()) {
      patchUiDefaultsForNewUi()
    }
    if (isResetLaf.compareAndSet(true, false)) {
      resetLafSettingsToDefault()
    }
  }

  override fun installIconPatcher() {
    if (isNewUI()) {
      installIconPatcher { createPathPatcher(it) }
    }
    else {
      service<IconMapLoader>().loadIconMapping() // reset mappings
    }
  }

  private fun installIconPatcher(patcherProvider: (Map<ClassLoader, Map<String, String>>) -> IconPathPatcher) {
    val iconMapping = service<IconMapLoader>().loadIconMapping() ?: return
    if (!isIconPatcherSet.compareAndSet(false, true)) {
      return
    }

    val patcher = iconMapping.takeIf { it.isNotEmpty() }?.let { patcherProvider(it) }
    if (patcher != null) {
      IconLoader.installPostPathPatcher(patcher)
    }
  }

  fun appStarted() {
    if (isNewUI()) {
      val version = ApplicationInfo.getInstance().build.asStringWithoutProductCodeAndSnapshot()
      PropertiesComponent.getInstance().setValue(NEW_UI_USED_VERSION, version)
    }
  }

  fun appClosing() {
    if (shouldUnsetNewUiSwitchKey) {
      PropertiesComponent.getInstance().unsetValue(NEW_UI_SWITCH)
    }
  }

  override fun saveCurrentValueAndReapplyDefaultLaf() {
    // TODO: remove all callers
  }

  private fun setNewUiUsed() {
    val propertyComponent = PropertiesComponent.getInstance()
    if (isNewUiUsedOnce) {
      propertyComponent.unsetValue(NEW_UI_FIRST_SWITCH)
    }
    else {
      propertyComponent.setValue(NEW_UI_FIRST_SWITCH, true)
    }
    propertyComponent.setValue(NEW_UI_SWITCH, true)
    shouldUnsetNewUiSwitchKey = false
  }
}

/**
 * We can't implement AppLifecycleListener with ExperimentalUiImpl
 * because it would create another instance of ExperimentalUiImpl
 */
private class ExperimentalUiAppLifecycleListener : AppLifecycleListener {
  override fun appStarted() {
    (ExperimentalUI.getInstance() as? ExperimentalUIImpl)?.appStarted()
  }

  override fun appClosing() {
    (ExperimentalUI.getInstance() as? ExperimentalUIImpl)?.appClosing()
  }
}

// TODO: create new impl for RMD or remove
@ApiStatus.Internal
interface ExperimentalUIJetBrainsClientDelegate {
  companion object {
    fun getInstance() = service<ExperimentalUIJetBrainsClientDelegate>()
  }

  fun changeUi(isEnabled: Boolean, updateLocally: (Boolean) -> Unit)
}

private fun resetLafSettingsToDefault() {
  val lafManager = LafManager.getInstance()
  val defaultLightLaf = lafManager.defaultLightLaf ?: return
  val defaultDarkLaf = lafManager.defaultDarkLaf ?: return
  val laf = if (JBColor.isBright()) defaultLightLaf else defaultDarkLaf
  lafManager.currentUIThemeLookAndFeel = laf

  val editorColorsManager = EditorColorsManager.getInstance() as EditorColorsManagerImpl
  var editorSchemeId = laf.editorSchemeId
  if (editorSchemeId == null && !isNewUI() && DarculaLaf.NAME == laf.name) {
    editorSchemeId = DarculaLaf.NAME
  }
  val scheme = if (editorSchemeId == null) null else editorColorsManager.getScheme(editorSchemeId)
  editorColorsManager.setGlobalScheme(scheme, true)

  LOG.info("=== UI: reset laf ($laf | $editorSchemeId | $scheme) ===")

  if (lafManager.autodetect) {
    lafManager.setPreferredLightLaf(defaultLightLaf)
    lafManager.setPreferredDarkLaf(defaultDarkLaf)
    lafManager.resetPreferredEditorColorScheme()
  }
}

private fun patchUiDefaultsForNewUi() {
  if (SystemInfo.isJetBrainsJvm && EarlyAccessRegistryManager.getBoolean("ide.experimental.ui.inter.font")) {
    if (UISettings.getInstance().overrideLafFonts) {
      //todo[kb] add RunOnce
      NotRoamableUiSettings.getInstance().overrideLafFonts = false
    }
  }
}

private const val reflectivePathPrefix = "com.intellij.icons.AllIcons."
private const val iconPathPrefix = "expui/"

private fun createPathPatcher(paths: Map<ClassLoader, Map<String, String>>): IconPathPatcher {
  return object : IconPathPatcher() {
    private val dumpNotPatchedIcons = System.getProperty("ide.experimental.ui.dump.not.patched.icons").toBoolean()
    // https://youtrack.jetbrains.com/issue/IDEA-335974
    private val useReflectivePath
      get() = System.getProperty("ide.experimental.ui.use.reflective.path", "true").toBoolean()

    override fun patchPath(path: String, classLoader: ClassLoader?): String? {
      val mappings = classLoader?.let { paths.get(classLoader) } ?: return null
      val patchedPath = mappings.get(path.trimStart('/'))
      if (patchedPath == null && dumpNotPatchedIcons) {
        NotPatchedIconRegistry.registerNotPatchedIcon(path, classLoader)
      }

      // isRunningFromSources - don't care about broken "run from sources", dev mode should be used instead
      if (patchedPath != null &&
          useReflectivePath &&
          classLoader !is PluginAwareClassLoader &&
          patchedPath.startsWith(iconPathPrefix) &&
          !PluginManagerCore.isRunningFromSources()) {
        val builder = StringBuilder(reflectivePathPrefix.length + patchedPath.length)
        builder.append(reflectivePathPrefix)
        builder.append(patchedPath, iconPathPrefix.length, patchedPath.length - 4)
        return toReflectivePath(builder).toString()
      }

      return patchedPath
    }

    private fun toReflectivePath(name: StringBuilder): StringBuilder {
      var index = reflectivePathPrefix.length

      name.set(index, name[index].titlecaseChar())
      index++

      var appendIndex = index
      while (index < name.length) {
        val c = name[index]
        if (if (index == reflectivePathPrefix.length) Character.isJavaIdentifierStart(c) else Character.isJavaIdentifierPart(c)) {
          name[appendIndex++] = c
          index++
          continue
        }

        if (c == '-') {
          index++
          if (index == name.length) {
            break
          }
          name[appendIndex++] = name[index].uppercaseChar()
        }
        else if (c == '/') {
          name[appendIndex++] = '.'
          index++
          if (index == name.length) {
            break
          }
          name[appendIndex++] = name[index].uppercaseChar()
        }
        else {
          name[appendIndex++] = '_'
        }
        index++
      }
      name.setLength(appendIndex)

      // com.intellij.icons.ExpUiIcons.General.Up -> com.intellij.icons.ExpUiIcons.General.UP
      if (name.get(name.length - 3) == '.') {
        // the last two symbols should be upper-cased
        name[name.length - 1] = name[name.length - 1].uppercaseChar()
      }
      return name
    }

    override fun getContextClassLoader(path: String, originalClassLoader: ClassLoader?) = originalClassLoader
  }
}
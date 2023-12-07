// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.DistractionFreeModeController
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.ide.ui.*
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.IconPathPatcher
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.platform.feedback.newUi.NewUIInfoService
import com.intellij.util.PlatformUtils
import com.intellij.util.application
import java.util.concurrent.atomic.AtomicBoolean

private val LOG: Logger
  get() = logger<ExperimentalUI>()

/**
 * @author Konstantin Bulenkov
 */
private class ExperimentalUIImpl : ExperimentalUI() {
  private var shouldApplyOnClose: Boolean? = null
  private var shouldUnsetNewUiSwitchKey: Boolean = true

  private val isIconPatcherSet = AtomicBoolean()
  private var iconPathPatcher: IconPathPatcher? = null

  override fun lookAndFeelChanged() {
    if (isNewUI()) {
      installIconPatcher()
      patchUiDefaultsForNewUi()
    }
  }

  fun onRegistryValueChange(isEnabled: Boolean) {
    if (isEnabled) {
      installIconPatcher()
      patchUiDefaultsForNewUi()
      onValueChanged(isEnabled = true)
    }
    else if (isIconPatcherSet.compareAndSet(true, false)) {
      iconPathPatcher?.let {
        iconPathPatcher = null
        IconLoader.removePathPatcher(it)
      }
      onValueChanged(isEnabled = false)
    }
  }

  override fun installIconPatcher() {
    if (!isNewUI()) {
      return
    }

    val iconMapping = service<IconMapLoader>().loadIconMapping() ?: return
    if (!isIconPatcherSet.compareAndSet(false, true)) {
      return
    }

    val patcher = iconMapping.takeIf { it.isNotEmpty() }?.let { createPathPatcher(it) }
    iconPathPatcher = patcher
    if (patcher != null) {
      IconLoader.installPostPathPatcher(patcher)
    }
  }

  /**
   * For RD session, we take the newUI preference from the join link of IDE backend,
   * and we don't read from or write to a local thin client registry.
   *
   * For CWM session, we take the newUI preference from a local thin client registry,
   * and when a user changes the value, we write it to the local registry.
   *
   * Both for RD and CWM sessions an actual change of newUI preference is done by
   * [ExperimentalUIJetBrainsClientDelegate].
   *
   * For local IDE, we show a restart dialog on user action.
   * On app closing, we save new value stored in the [shouldApplyOnClose]
   */
  override fun setNewUIInternal(newUI: Boolean, suggestRestart: Boolean) {
    if (newUI == NewUiValue.isEnabled()) {
      LOG.warn("Setting the same value $newUI")
      return
    }

    if (PlatformUtils.isJetBrainsClient()) {
      changeUiWithDelegate(newUI)
    }
    else {
      onValueChanged(newUI)
      if (suggestRestart) {
        shouldApplyOnClose = newUI
        showRestartDialog()
      }
      else {
        saveNewValue(newUI)
      }
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
    val newValue = shouldApplyOnClose
    if (newValue != null && newValue != NewUiValue.isEnabled()) {
      saveNewValue(newValue)
    }
  }

  private fun onValueChanged(isEnabled: Boolean) {
    if (isEnabled) {
      setNewUiUsed()
    }

    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }

    if (isEnabled) {
      NewUIInfoService.getInstance().updateEnableNewUIDate()
      // Do not force enabling tool window stripes in DFM
      if (!DistractionFreeModeController.shouldMinimizeCustomHeader()) {
        UISettings.getInstance().hideToolStripes = false
      }
    }
    else {
      NewUIInfoService.getInstance().updateDisableNewUIDate()
    }

    // On the client, onValueChanged will not be called again as there's no real registry value change.
    // Set the override before calling resetLafSettingsToDefault to ensure the correct LaF is chosen.
    if (PlatformUtils.isJetBrainsClient()) {
      NewUiValue.overrideNewUiForOneRemDevSession(isEnabled)
    }
    resetLafSettingsToDefault()
  }

  private fun saveNewValue(enabled: Boolean) {
    try {
      LOG.info("Saving newUi=$enabled to registry")
      EarlyAccessRegistryManager.setBoolean(KEY, enabled)
      EarlyAccessRegistryManager.syncAndFlush()
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

  override fun saveCurrentValueAndReapplyDefaultLaf() {
    saveNewValue(NewUiValue.isEnabled())
    resetLafSettingsToDefault()
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

  private fun changeUiWithDelegate(isEnabled: Boolean) {
    val restartNow = MessageDialogBuilder.yesNo(
      title = IdeBundle.message("dialog.newui.title.user.interface"),
      message = IdeBundle.message("dialog.newui.message.need.restart.client.and.backend.to.apply.settings"),
      icon = AllIcons.General.QuestionDialog,
    ).yesText(IdeBundle.message("dialog.newui.message.new.ui.restart.now"))
      .noText(IdeBundle.message("dialog.newui.message.new.ui.restart.later"))
      .guessWindowAndAsk()
    fun changeUI() {
      val delegate = ExperimentalUIJetBrainsClientDelegate.getInstance()
      delegate.changeUi(isEnabled, updateLocally = {
        onValueChanged(isEnabled)
        saveNewValue(isEnabled)
      })
    }
    if (restartNow) {
      changeUI()
    }
    else {
      val disposable = Disposer.newDisposable("NewUI change")
      application.messageBus.connect(disposable).subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
        override fun appClosing() {
          Disposer.dispose(disposable)
          changeUI()
        }
      })
    }
  }

  private fun showRestartDialog() {
    val action = if (ApplicationManager.getApplication().isRestartCapable) {
      IdeBundle.message("ide.restart.action")
    }
    else {
      IdeBundle.message("ide.shutdown.action")
    }
    @Suppress("SpellCheckingInspection")
    val result = Messages.showYesNoDialog(
      /* message = */ IdeBundle.message("dialog.message.must.be.restarted.for.changes.to.take.effect",
                                        ApplicationNamesInfo.getInstance().fullProductName),
      /* title = */ IdeBundle.message("dialog.title.restart.required"),
      /* yesText = */ action,
      /* noText = */ IdeBundle.message("ide.notnow.action"),
      /* icon = */ Messages.getQuestionIcon()
    )

    if (result == Messages.YES) {
      ApplicationManagerEx.getApplicationEx().restart(true)
    }
  }
}

private fun resetLafSettingsToDefault() {
  val lafManager = LafManager.getInstance()
  val defaultLightLaf = lafManager.defaultLightLaf ?: return
  val defaultDarkLaf = lafManager.defaultDarkLaf ?: return
  val laf = if (JBColor.isBright()) defaultLightLaf else defaultDarkLaf
  lafManager.currentUIThemeLookAndFeel = laf
  if (lafManager.autodetect) {
    lafManager.setPreferredLightLaf(defaultLightLaf)
    lafManager.setPreferredDarkLaf(defaultDarkLaf)
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

interface ExperimentalUIJetBrainsClientDelegate {
  companion object {
    fun getInstance() = service<ExperimentalUIJetBrainsClientDelegate>()
  }

  fun changeUi(isEnabled: Boolean, updateLocally: (Boolean) -> Unit)
}

private fun patchUiDefaultsForNewUi() {
  if (SystemInfo.isJetBrainsJvm && EarlyAccessRegistryManager.getBoolean("ide.experimental.ui.inter.font")) {
    if (UISettings.getInstance().overrideLafFonts) {
      //todo[kb] add RunOnce
      NotRoamableUiSettings.getInstance().overrideLafFonts = false
    }
  }
}

internal class NewUiRegistryListener : RegistryValueListener {
  override fun afterValueChanged(value: RegistryValue) {
    // JetBrains Client has custom listener
    if (PlatformUtils.isJetBrainsClient() || value.key != ExperimentalUI.KEY) {
      return
    }

    (ExperimentalUI.getInstance() as? ExperimentalUIImpl)?.onRegistryValueChange(value.asBoolean())
  }
}

private const val reflectivePathPrefix = "com.intellij.icons.ExpUiIcons."
private const val iconPathPrefix = "expui/"

private fun createPathPatcher(paths: Map<ClassLoader, Map<String, String>>): IconPathPatcher {
  return object : IconPathPatcher() {
    private val dumpNotPatchedIcons = System.getProperty("ide.experimental.ui.dump.not.patched.icons").toBoolean()
    // https://youtrack.jetbrains.com/issue/IDEA-335974
    private val useReflectivePath = System.getProperty("ide.experimental.ui.use.reflective.path", "true").toBoolean()

    override fun patchPath(path: String, classLoader: ClassLoader?): String? {
      val mappings = paths.get(classLoader) ?: return null
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
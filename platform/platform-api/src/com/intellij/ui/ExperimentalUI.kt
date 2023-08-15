// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ui

import com.intellij.ide.ui.NotRoamableUiSettings
import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.util.IconLoader.installPathPatcher
import com.intellij.openapi.util.IconLoader.removePathPatcher
import com.intellij.openapi.util.IconPathPatcher
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.findIconUsingNewImplementation
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.UIDefaults
import javax.swing.UIManager

/**
 * Temporary utility class for migration to the new UI.
 * This is not a public API. For plugin development use [NewUI.isEnabled]
 *
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
abstract class ExperimentalUI {
  private val isIconPatcherSet = AtomicBoolean()
  private var iconPathPatcher: IconPathPatcher? = null

  open fun setNewUIInternal(newUI: Boolean, suggestRestart: Boolean) {}

  // used by the JBClient for cases where a link overrides new UI mode
  abstract fun saveCurrentValueAndReapplyDefaultLaf()

  @Suppress("unused")
  class NewUiRegistryListener : RegistryValueListener {
    override fun afterValueChanged(value: RegistryValue) {
      if (!isApplicable || value.key != KEY) {
        return
      }
      val isEnabled = value.asBoolean()
      val instance = getInstance()
      if (isEnabled) {
        patchUiDefaultsForNewUi()
        if (instance.isIconPatcherSet.compareAndSet(false, true)) {
          if (instance.iconPathPatcher != null) {
            removePathPatcher(instance.iconPathPatcher!!)
          }
          instance.iconPathPatcher = createPathPatcher(instance.getIconMappings())
          installPathPatcher(instance.iconPathPatcher!!)
        }
        instance.onExpUIEnabled(true)
      }
      else if (instance.isIconPatcherSet.compareAndSet(true, false)) {
        removePathPatcher(instance.iconPathPatcher!!)
        instance.iconPathPatcher = null
        instance.onExpUIDisabled(true)
      }
    }

    companion object {
      private val isApplicable: Boolean
        get() = // JetBrains Client has custom listener
          !PlatformUtils.isJetBrainsClient()
    }
  }

  fun lookAndFeelChanged() {
    if (!isNewUI()) {
      return
    }
    if (isIconPatcherSet.compareAndSet(false, true)) {
      if (iconPathPatcher != null) {
        removePathPatcher(iconPathPatcher!!)
      }
      iconPathPatcher = createPathPatcher(getIconMappings())
      installPathPatcher(iconPathPatcher!!)
    }
    patchUiDefaultsForNewUi()
  }

  abstract fun getIconMappings(): Map<ClassLoader, Map<String, String>>

  abstract fun onExpUIEnabled(suggestRestart: Boolean)

  abstract fun onExpUIDisabled(suggestRestart: Boolean)

  companion object {
    @Suppress("DEPRECATION")
    const val KEY: String = NewUiValue.KEY

    @Deprecated("please use {@link #isNewUiUsedOnce()} instead ")
    const val NEW_UI_USED_PROPERTY: String = "experimental.ui.used.once"

    // Last IDE version when New UI was enabled
    const val NEW_UI_USED_VERSION: String = "experimental.ui.used.version"
    const val NEW_UI_FIRST_SWITCH: String = "experimental.ui.first.switch"

    // Means that IDE is started after enabling the New UI (not necessary the first time).
    // Should be unset by the client, or it will be unset on the IDE close.
    const val NEW_UI_SWITCH: String = "experimental.ui.switch"
    const val NEW_UI_PROMO_BANNER_DISABLED_PROPERTY: String = "experimental.ui.promo.banner.disabled"

    init {
      NewUiValue.initialize(Supplier { EarlyAccessRegistryManager.getBoolean(KEY) })
    }

    @JvmStatic
    fun getInstance(): ExperimentalUI = ApplicationManager.getApplication().service<ExperimentalUI>()

    @JvmStatic
    fun isNewUI(): Boolean = NewUiValue.isEnabled()

    fun setNewUI(value: Boolean) {
      getInstance().setNewUIInternal(newUI = value, suggestRestart = true)
    }

    @JvmStatic
    val isNewNavbar: Boolean
      get() = NewUiValue.isEnabled() && Registry.`is`("ide.experimental.ui.navbar.scroll", isNewUI())

    val isEditorTabsWithScrollBar: Boolean
      get() = NewUiValue.isEnabled() && Registry.`is`("ide.experimental.ui.editor.tabs.scrollbar", true)

    @JvmStatic
    val isNewUiUsedOnce: Boolean
      /** Whether New UI was enabled at least once. Note: tracked since 2023.1  */
      get() {
        val propertiesComponent = PropertiesComponent.getInstance()
        @Suppress("DEPRECATION")
        return propertiesComponent.getValue(NEW_UI_USED_VERSION) != null || propertiesComponent.getBoolean(NEW_UI_USED_PROPERTY)
      }
  }
}

@Internal
object NotPatchedIconRegistry {
  private val paths = HashSet<Pair<String, ClassLoader?>>()

  fun getData(): List<IconModel> {
    val result = ArrayList<IconModel>(paths.size)
    for ((path, second) in paths) {
      val classLoader = second ?: NotPatchedIconRegistry::class.java.getClassLoader()
      val icon = findIconUsingNewImplementation(path, classLoader!!, null)
      result.add(IconModel(icon, path))
    }
    return result
  }

  fun registerNotPatchedIcon(path: String, classLoader: ClassLoader?) {
    paths.add(Pair(path, classLoader))
  }

  class IconModel(var icon: Icon?, var originalPath: String) {
    override fun toString(): String = originalPath
  }
}

private fun createPathPatcher(paths: Map<ClassLoader, Map<String, String>>): IconPathPatcher {
  return object : IconPathPatcher() {
    private val dumpNotPatchedIcons = System.getProperty("ide.experimental.ui.dump.not.patched.icons").toBoolean()
    override fun patchPath(path: String, classLoader: ClassLoader?): String? {
      val mappings = paths.get(classLoader) ?: return null
      val patchedPath = mappings.get(path.trimStart('/'))
      if (patchedPath == null && dumpNotPatchedIcons) {
        NotPatchedIconRegistry.registerNotPatchedIcon(path, classLoader)
      }
      return patchedPath
    }

    override fun getContextClassLoader(path: String, originalClassLoader: ClassLoader?): ClassLoader? {
      return originalClassLoader
    }
  }
}

private fun patchUiDefaultsForNewUi() {
  val defaults = UIManager.getDefaults()
  if (defaults.getColor("EditorTabs.hoverInactiveBackground") == null) {
    // avoid getting EditorColorsManager too early
    setUIProperty("EditorTabs.hoverInactiveBackground", UIDefaults.LazyValue { `__`: UIDefaults? ->
      val editorColorScheme = EditorColorsManager.getInstance().getGlobalScheme()
      ColorUtil.mix(JBColor.PanelBackground, editorColorScheme.getDefaultBackground(), 0.5)
    }, defaults)
  }
  if (SystemInfo.isJetBrainsJvm && EarlyAccessRegistryManager.getBoolean("ide.experimental.ui.inter.font")) {
    if (UISettings.getInstance().overrideLafFonts) {
      //todo[kb] add RunOnce
      NotRoamableUiSettings.getInstance().overrideLafFonts = false
    }
  }
}

private fun setUIProperty(@Suppress("SameParameterValue") key: String, value: Any, defaults: UIDefaults) {
  defaults.remove(key)
  defaults.put(key, value)
}


// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.ui.laf

import com.intellij.diagnostic.runActivity
import com.intellij.ide.ui.TargetUIType
import com.intellij.ide.ui.UITheme
import com.intellij.ide.ui.UIThemeProvider
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.util.concurrency.SynchronizedClearableLazy
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.function.Supplier

// separate service to avoid using LafManager in the EditorColorsManagerImpl initialization
@Service(Service.Level.APP)
@Internal
class UiThemeProviderListManager {
  companion object {
    fun getInstance(): UiThemeProviderListManager = service()

    internal const val DEFAULT_DARK_PARENT_THEME = "Darcula"
    internal const val DEFAULT_LIGHT_PARENT_THEME = "IntelliJ"
  }

  @Volatile
  private lateinit var themeDescriptors: List<LafEntry>

  init {
    themeDescriptors = java.util.List.copyOf(runActivity("compute LaF list") {
      UIThemeProvider.EP_NAME.filterableLazySequence()
        .mapNotNull { item ->
          val provider = item.instance ?: return@mapNotNull null
          val supplier = SynchronizedClearableLazy {
            val theme = when (provider.id) {
              DEFAULT_DARK_PARENT_THEME -> provider.createTheme(parentTheme = null,
                                                                defaultDarkParent = null,
                                                                defaultLightParent = null,
                                                                pluginDescriptor = item.pluginDescriptor)
              DEFAULT_LIGHT_PARENT_THEME -> provider.createTheme(
                parentTheme = findThemeBeanHolderById(DEFAULT_DARK_PARENT_THEME),
                defaultDarkParent = null,
                defaultLightParent = null,
                pluginDescriptor = item.pluginDescriptor,
              )
              else -> {
                @Suppress("DEPRECATION")
                val parentTheme = findParentTheme(themes = themeDescriptors, parentId = provider.parentTheme)
                provider.createTheme(
                  parentTheme = parentTheme,
                  defaultDarkParent = { findThemeBeanHolderById(DEFAULT_DARK_PARENT_THEME) },
                  defaultLightParent = { findThemeBeanHolderById(DEFAULT_LIGHT_PARENT_THEME) },
                  pluginDescriptor = item.pluginDescriptor,
                )
              }
            }
            theme?.let { UIThemeLookAndFeelInfoImpl(/* theme = */ it).also { it.setRestartRequired(provider.isRestartRequired) } }
          }

          LafEntry(
            theme = supplier,
            bean = provider,
            pluginDescriptor = item.pluginDescriptor,
          )
        }
        .toList()
    })
  }

  fun getLaFs(): Sequence<UIThemeLookAndFeelInfo> = themeDescriptors.asSequence().mapNotNull { it.theme.get() }

  fun getLaFListSize(): Int = themeDescriptors.size

  fun findThemeByName(name: String): UIThemeLookAndFeelInfo? {
    return getLaFs().firstOrNull { it.name == name }
  }

  fun findThemeById(id: String): UIThemeLookAndFeelInfo? =
    findThemeSupplierById(id)?.get()

  private fun findThemeBeanHolderById(id: String): UITheme? {
    return findLaFById(id)?.theme?.get()?.theme
  }

  internal fun findDefaultParent(isDark: Boolean, themeId: String): UITheme? {
    if (isDark) {
      if (themeId != DEFAULT_DARK_PARENT_THEME) {
        return findThemeBeanHolderById(DEFAULT_DARK_PARENT_THEME)
      }
    }
    else {
      if (themeId != DEFAULT_LIGHT_PARENT_THEME) {
        return findThemeBeanHolderById(DEFAULT_LIGHT_PARENT_THEME)
      }
    }
    return null
  }

  fun findThemeSupplierById(id: String): Supplier<out UIThemeLookAndFeelInfo?>? {
    return findLaFById(id)?.theme
  }

  internal fun getDescriptors(): List<LafEntry> = themeDescriptors

  fun getThemeJson(id: String): ByteArray? {
    val entry = findLaFById(id) ?: return null
    return entry.bean.getThemeJson(entry.pluginDescriptor)
  }

  fun getThemeListForTargetUI(targetUI: TargetUIType): Sequence<UIThemeLookAndFeelInfo> {
    return themeDescriptors.asSequence()
      .filter { it.targetUiType == targetUI }
      .mapNotNull { it.theme.get() }
  }

  fun getBundledThemeListForTargetUI(targetUI: TargetUIType): List<UIThemeLookAndFeelInfo> {
    val themes = mutableListOf<UIThemeLookAndFeelInfo>()
    getThemeListForTargetUI(targetUI).forEach { info ->
      if (!info.isThemeFromPlugin) themes.add(info)
    }
    return themes
  }

  internal fun themeProviderAdded(provider: UIThemeProvider, pluginDescriptor: PluginDescriptor): LafEntry? {
    if (findLaFByProviderId(provider) != null) {
      // provider is already registered
      return null
    }

    val lafEntry = LafEntry(SynchronizedClearableLazy {
      val parentTheme = findParentTheme(themes = themeDescriptors, parentId = provider.parentTheme)
      val theme = provider.createTheme(
        parentTheme = parentTheme,
        defaultDarkParent = { themeDescriptors.single { it.id == DEFAULT_DARK_PARENT_THEME }.theme.get()?.theme },
        defaultLightParent = { themeDescriptors.single { it.id == DEFAULT_LIGHT_PARENT_THEME }.theme.get()?.theme },
        pluginDescriptor = pluginDescriptor,
      ) ?: return@SynchronizedClearableLazy null
      UIThemeLookAndFeelInfoImpl(theme).also { it.setRestartRequired(provider.isRestartRequired) }
    }, bean = provider, pluginDescriptor = pluginDescriptor)
    themeDescriptors = themeDescriptors + lafEntry
    return lafEntry
  }

  internal fun themeProviderRemoved(provider: UIThemeProvider): UIThemeLookAndFeelInfoImpl? {
    val oldLaF = findLaFByProviderId(provider) ?: return null
    themeDescriptors = themeDescriptors - oldLaF
    return oldLaF.theme.get()
  }

  private fun findLaFById(id: String): LafEntry? {
    fun lookUp(id: String) =
      themeDescriptors.firstOrNull { it.id == id }

    val entry = lookUp(id)
    if (entry != null) return entry
    return UiThemeRemapper.EP_NAME.filterableLazySequence()
      .firstNotNullOfOrNull {
        val remappedId = it.instance?.mapLaFId(id)
        remappedId?.let(::lookUp)
      }
  }

  private fun findLaFByProviderId(provider: UIThemeProvider) = provider.id?.let { findLaFById(it) }
}

internal data class LafEntry(
  @JvmField val theme: Supplier<UIThemeLookAndFeelInfoImpl?>,
  @JvmField val bean: UIThemeProvider,
  @JvmField val pluginDescriptor: PluginDescriptor,
) {
  val targetUiType: TargetUIType
    get() = bean.targetUI

  val id: String
    get() = bean.id!!
}

private fun findParentTheme(themes: Collection<LafEntry>, parentId: String?): UITheme? {
  return if (parentId == null) null else themes.firstOrNull { it.id == parentId }?.theme?.get()?.theme
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.ui.laf

import com.intellij.diagnostic.runActivity
import com.intellij.ide.ui.TargetUIType
import com.intellij.ide.ui.UITheme
import com.intellij.ide.ui.UIThemeProvider
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.util.concurrency.SynchronizedClearableLazy
import java.util.function.Supplier

// separate service to avoid using LafManager in the EditorColorsManagerImpl initialization
@Service(Service.Level.APP)
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
      UIThemeProvider.EP_NAME.extensionList.map { provider ->
        val supplier = SynchronizedClearableLazy {
          val theme = when (provider.id) {
            DEFAULT_DARK_PARENT_THEME -> provider.createTheme(parentTheme = null, defaultDarkParent = null, defaultLightParent = null)
            DEFAULT_LIGHT_PARENT_THEME -> provider.createTheme(
              parentTheme = findThemeBeanHolderById(DEFAULT_DARK_PARENT_THEME),
              defaultDarkParent = null,
              defaultLightParent = null,
            )
            else -> {
              val parentTheme = findParentTheme(themes = themeDescriptors, parentId = provider.parentTheme)
              provider.createTheme(
                parentTheme = parentTheme,
                defaultDarkParent = { findThemeBeanHolderById(DEFAULT_DARK_PARENT_THEME) },
                defaultLightParent = { findThemeBeanHolderById(DEFAULT_LIGHT_PARENT_THEME) },
              )
            }
          }
          theme?.let { UIThemeLookAndFeelInfoImpl(/* theme = */ it) }
        }

        LafEntry(
          theme = supplier,
          targetUiType = provider.targetUI,
          id = provider.id!!,
        )
      }
    })
  }

  fun getLaFs(): Sequence<UIThemeLookAndFeelInfo> = themeDescriptors.asSequence().mapNotNull { it.theme.get() }

  fun getLaFListSize(): Int = themeDescriptors.size

  fun findThemeByName(name: String): UIThemeLookAndFeelInfo? {
    return getLaFs().firstOrNull { it.name == name }
  }

  fun findThemeById(id: String): UIThemeLookAndFeelInfo? {
    return themeDescriptors.firstOrNull { it.id == id }?.theme?.get()
  }

  private fun findThemeBeanHolderById(id: String): UITheme? {
    return themeDescriptors.firstOrNull { it.id == id }?.theme?.get()?.theme
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
    return themeDescriptors.firstOrNull { it.id == id }?.theme
  }

  internal fun getLaFsWithUITypes(): List<LafEntry> = themeDescriptors

  fun getThemeListForTargetUI(targetUI: TargetUIType): Sequence<UIThemeLookAndFeelInfo> {
    return themeDescriptors.asSequence()
      .filter { it.targetUiType == targetUI }
      .mapNotNull { it.theme.get() }
  }

  internal fun themeProviderAdded(provider: UIThemeProvider): UIThemeLookAndFeelInfo? {
    if (findLaFByProviderId(provider) != null) {
      // provider is already registered
      return null
    }

    val parentTheme = findParentTheme(themes = themeDescriptors, parentId = provider.parentTheme)
    val theme = provider.createTheme(
      parentTheme = parentTheme,
      defaultDarkParent = { themeDescriptors.single { it.id == DEFAULT_DARK_PARENT_THEME }.theme.get()?.theme },
      defaultLightParent = { themeDescriptors.single { it.id == DEFAULT_LIGHT_PARENT_THEME }.theme.get()?.theme },
    ) ?: return null
    editorColorManager.handleThemeAdded(theme)
    val newLaF = UIThemeLookAndFeelInfoImpl(theme)
    themeDescriptors = themeDescriptors + LafEntry(Supplier { newLaF }, provider.targetUI, provider.id!!)
    return newLaF
  }

  fun themeProviderRemoved(provider: UIThemeProvider): UIThemeLookAndFeelInfo? {
    val oldLaF = findLaFByProviderId(provider) ?: return null
    themeDescriptors = themeDescriptors - oldLaF
    val theme = oldLaF.theme.get() ?: return null
    editorColorManager.handleThemeRemoved(theme.theme)
    return theme
  }

  private fun findLaFById(id: String) = themeDescriptors.firstOrNull { it.id == id }

  private fun findLaFByProviderId(provider: UIThemeProvider) = provider.id?.let { findLaFById(it) }
}

internal data class LafEntry(
  @JvmField val theme: Supplier<UIThemeLookAndFeelInfoImpl?>,
  @JvmField val targetUiType: TargetUIType,
  @JvmField val id: String,
)

private fun findParentTheme(themes: Collection<LafEntry>, parentId: String?): UITheme? {
  return if (parentId == null) null else themes.firstOrNull { it.id == parentId }?.theme?.get()?.theme
}

private val editorColorManager: EditorColorsManagerImpl
  get() = EditorColorsManager.getInstance() as EditorColorsManagerImpl

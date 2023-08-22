// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ide.ui.laf

import com.intellij.ide.ui.TargetUIType
import com.intellij.ide.ui.UITheme
import com.intellij.ide.ui.UIThemeProvider
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.util.graph.DFSTBuilder
import com.intellij.util.graph.OutboundSemiGraph

// separate service to avoid using LafManager in the EditorColorsManagerImpl initialization
@Service(Service.Level.APP)
internal class UiThemeProviderListManager {
  companion object {
    fun getInstance(): UiThemeProviderListManager = service()
    internal const val DEFAULT_DARK_PARENT_THEME = "Darcula"
    internal const val DEFAULT_LIGHT_PARENT_THEME = "IntelliJ"
  }

  @Volatile
  private var lafMap: Map<UIThemeLookAndFeelInfo, TargetUIType> = computeMap()

  private val lafList: Set<UIThemeLookAndFeelInfo>
    get() = lafMap.keys

  fun getLaFs(): List<UIThemeLookAndFeelInfo> = lafList.toList()

  fun getLaFsWithUITypes(): Map<UIThemeLookAndFeelInfo, TargetUIType> = lafMap

  fun findJetBrainsLightTheme(): UIThemeLookAndFeelInfo? = findLaFById(DEFAULT_LIGHT_THEME_ID)

  fun themeProviderAdded(provider: UIThemeProvider): UIThemeLookAndFeelInfo? {
    if (findLaFByProviderId(provider) != null) {
      // provider is already registered
      return null
    }

    val parentTheme = findParentTheme(lafList, provider.parentTheme)
    val theme = provider.createTheme(parentTheme,
                                     lafMap.keys.single { it.theme.id == DEFAULT_DARK_PARENT_THEME }.theme,
                                     lafMap.keys.single { it.theme.id == DEFAULT_LIGHT_PARENT_THEME }.theme) ?: return null
    editorColorManager().handleThemeAdded(theme)
    val newLaF = UIThemeLookAndFeelInfoImpl(theme)
    lafMap = lafMap + Pair(newLaF, provider.targetUI)
    return newLaF
  }

  fun themeProviderRemoved(provider: UIThemeProvider): UIThemeLookAndFeelInfo? {
    val oldLaF = findLaFByProviderId(provider) ?: return null
    lafMap = lafMap - oldLaF
    editorColorManager().handleThemeRemoved(oldLaF.theme)
    return oldLaF
  }

  private fun findLaFById(id: String) = lafList.find { it.theme.id == id }

  private fun findLaFByProviderId(provider: UIThemeProvider) = findLaFById(provider.id)
}

private fun computeMap(): Map<UIThemeLookAndFeelInfo, TargetUIType> {
  val map = LinkedHashMap<UIThemeLookAndFeelInfo, TargetUIType>()
  val orderedProviders = sortTopologically(UIThemeProvider.EP_NAME.extensionList, { it.id }, { it.parentTheme })
  val darcula = orderedProviders.single{ it.id == UiThemeProviderListManager.DEFAULT_DARK_PARENT_THEME }
    .createTheme(null, null, null)
  val intelliJ = orderedProviders.single{ it.id == UiThemeProviderListManager.DEFAULT_LIGHT_PARENT_THEME }
    .createTheme(darcula, null, null)
  for (provider in orderedProviders) {
    val parentTheme = findParentTheme(map.keys, provider.parentTheme)
    val theme = UIThemeLookAndFeelInfoImpl(provider.createTheme(parentTheme, darcula, intelliJ) ?: continue)
    map.put(theme, provider.targetUI)
  }
  return map
}

private fun findParentTheme(themes: Collection<UIThemeLookAndFeelInfo>, parentId: String?): UITheme? {
  if (parentId == null) {
    return null
  }
  return themes.asSequence().map { it.theme }.find { it.id == parentId }
}

private fun <T, K> sortTopologically(list: List<T>, idFun: (T) -> K, parentIdFun: (T) -> K?): List<T> {
  val mapById = list.associateBy(idFun)
  val graph = object : OutboundSemiGraph<T> {
    override fun getNodes(): Collection<T> = list

    override fun getOut(n: T): Iterator<T> {
      val parent = mapById.get(parentIdFun(n))
      return listOfNotNull(parent).iterator()
    }
  }

  return DFSTBuilder(graph).sortedNodes.reversed()
}

private const val DEFAULT_LIGHT_THEME_ID = "JetBrainsLightTheme"

private fun editorColorManager() = EditorColorsManager.getInstance() as EditorColorsManagerImpl

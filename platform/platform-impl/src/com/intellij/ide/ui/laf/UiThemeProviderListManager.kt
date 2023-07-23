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
  }

  @Volatile
  private var lafMap: Map<UIThemeBasedLookAndFeelInfo, TargetUIType> = computeMap()

  private val lafList: Set<UIThemeBasedLookAndFeelInfo>
    get() = lafMap.keys

  fun getLaFs(): List<UIThemeBasedLookAndFeelInfo> = lafList.toList()

  fun getLaFsWithUITypes(): Map<UIThemeBasedLookAndFeelInfo, TargetUIType> = lafMap

  fun findJetBrainsLightTheme(): UIThemeBasedLookAndFeelInfo? = findLaFById(DEFAULT_LIGHT_THEME_ID)

  fun themeProviderAdded(provider: UIThemeProvider): UIThemeBasedLookAndFeelInfo? {
    if (findLaFByProviderId(provider) != null) {
      // provider is already registered
      return null
    }

    val parentTheme = findParentTheme(lafList, provider.parentTheme)
    val theme = provider.createTheme(parentTheme) ?: return null
    editorColorManager().handleThemeAdded(theme)
    val newLaF = UIThemeBasedLookAndFeelInfo(theme)
    lafMap = lafMap + Pair(newLaF, provider.targetUI)
    return newLaF
  }

  fun themeProviderRemoved(provider: UIThemeProvider): UIThemeBasedLookAndFeelInfo? {
    val oldLaF = findLaFByProviderId(provider) ?: return null
    lafMap = lafMap - oldLaF
    editorColorManager().handleThemeRemoved(oldLaF.theme)
    return oldLaF
  }

  private fun findLaFById(id: String) = lafList.find { it.theme.id == id }

  private fun findLaFByProviderId(provider: UIThemeProvider) = findLaFById(provider.id)
}

private fun computeMap(): Map<UIThemeBasedLookAndFeelInfo, TargetUIType> {
  val map = LinkedHashMap<UIThemeBasedLookAndFeelInfo, TargetUIType>()
  val orderedProviders = sortTopologically(UIThemeProvider.EP_NAME.extensionList, { it.id }, { it.parentTheme })
  for (provider in orderedProviders) {
    val parentTheme = findParentTheme(map.keys, provider.parentTheme)
    val theme = UIThemeBasedLookAndFeelInfo(provider.createTheme(parentTheme) ?: continue)
    map.put(theme, provider.targetUI)
  }
  return map
}

private fun findParentTheme(themes: Collection<UIThemeBasedLookAndFeelInfo>, parentId: String?): UITheme? {
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

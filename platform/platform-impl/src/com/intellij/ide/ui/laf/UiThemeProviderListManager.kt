// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ide.ui.laf

import com.intellij.ide.ui.UITheme
import com.intellij.ide.ui.TargetUIType
import com.intellij.ide.ui.UIThemeProvider
import com.intellij.ide.ui.laf.UiThemeProviderListManager.Companion.themesSortingComparator
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.ui.ExperimentalUI
import com.intellij.util.PlatformUtils
import java.util.*
import com.intellij.util.graph.DFSTBuilder
import com.intellij.util.graph.OutboundSemiGraph
import javax.swing.UIManager.LookAndFeelInfo

// separate service to avoid using LafManager in the EditorColorsManagerImpl initialization
@Service(Service.Level.APP)
internal class UiThemeProviderListManager {
  companion object {
    @JvmStatic
    fun getInstance(): UiThemeProviderListManager = service()

    private const val DEFAULT_LIGHT_THEME_ID = "JetBrainsLightTheme"

    var lafNameOrder: Map<String, Int> = when {
      ExperimentalUI.isNewUI() -> {
        java.util.Map.of(
          "Light", 0,
          "Dark", 1,
          "High contrast", 2
        )
      }
      PlatformUtils.isRider() -> {
        java.util.Map.of(
          "Rider Dark", 0,
          "Rider Light", 1,
          "IntelliJ Light", 2,
          "macOS Light", 3,
          "Windows 10 Light", 3,
          "Darcula", 4,
          "High contrast", 5
        )
      }
      else -> {
        java.util.Map.of(
          "IntelliJ Light", 0,
          "macOS Light", 1,
          "Windows 10 Light", 1,
          "Darcula", 2,
          "High contrast", 3
        )
      }
    }

    val themesSortingComparator = Comparator<LookAndFeelInfo> { t1, t2 ->
      val n1 = t1.name
      val n2 = t2.name
      if (n1 == n2) {
        return@Comparator 0
      }

      val o1 = lafNameOrder.get(n1)
      val o2 = lafNameOrder.get(n2)
      when {
        o1 != null && o2 != null -> o1 - o2
        o1 != null -> -1
        o2 != null -> 1
        else -> n1.compareTo(n2, ignoreCase = true)
      }
    }

    fun sortThemes(list: MutableList<out LookAndFeelInfo>) = list.sortWith(themesSortingComparator)

    private fun editorColorsManager() = EditorColorsManager.getInstance() as EditorColorsManagerImpl
  }

  @Volatile
  private var lafMap = computeMap()

  private val lafList
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
    editorColorsManager().handleThemeAdded(theme)
    val newLaF = UIThemeBasedLookAndFeelInfo(theme)
    lafMap = lafMap + Pair(newLaF, provider.targetUI)
    return newLaF
  }

  fun themeProviderRemoved(provider: UIThemeProvider): UIThemeBasedLookAndFeelInfo? {
    val oldLaF = findLaFByProviderId(provider) ?: return null

    lafMap = lafMap - oldLaF
    editorColorsManager().handleThemeRemoved(oldLaF.theme)
    return oldLaF
  }

  private operator fun <K, V> SortedMap<K, out V>.plus(pair: Pair<K, V>): SortedMap<K, V> {
    val res = TreeMap(this)
    res[pair.first] = pair.second
    return res
  }

  private operator fun <K, V> SortedMap<K, out V>.minus(key: K): SortedMap<K, V> {
    val res = TreeMap(this)
    res.remove(key)
    return res
  }

  private fun findLaFById(id: String) = lafList.find { it.theme.id == id }

  private fun findLaFByProviderId(provider: UIThemeProvider) = findLaFById(provider.id)
}

private fun computeMap(): SortedMap<UIThemeBasedLookAndFeelInfo, TargetUIType> {
  val map = TreeMap<UIThemeBasedLookAndFeelInfo, TargetUIType>(themesSortingComparator)

  val orderedProviders = sortTopologically(UIThemeProvider.EP_NAME.extensions.asList(), { it.id }, { it.parentTheme })
  for (provider in orderedProviders) {
    val parentTheme = findParentTheme(map.keys, provider.parentTheme)
    val theme = UIThemeBasedLookAndFeelInfo(provider.createTheme(parentTheme) ?: continue)
    map[theme] = provider.targetUI
  }

  return map
}

private fun findParentTheme(themes: Collection<UIThemeBasedLookAndFeelInfo>, parentId: String?): UITheme? {
  if (parentId == null) return null
  return themes.map { it.theme }.find { it.id == parentId }
}

private fun <T, K> sortTopologically(list: List<T>, idFun: (T) -> K, parentIdFun: (T) -> K?): List<T> {
  val mapById = list.associateBy(idFun)

  val graph: OutboundSemiGraph<T> = object : OutboundSemiGraph<T> {
    override fun getNodes(): Collection<T> {
      return list
    }

    override fun getOut(n: T): Iterator<T> {
      val parentId = parentIdFun(n)
      val parent = mapById[parentId]
      return listOfNotNull(parent).iterator()
    }
  }

  val builder: DFSTBuilder<T> = DFSTBuilder(graph)
  return builder.sortedNodes.reversed()
}

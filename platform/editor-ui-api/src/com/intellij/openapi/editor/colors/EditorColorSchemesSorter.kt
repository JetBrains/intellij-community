// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls

@Internal
interface EditorColorSchemesSorter {
  companion object {
    @JvmStatic
    fun getInstance(): EditorColorSchemesSorter = ApplicationManager.getApplication().service<EditorColorSchemesSorter>()
  }

  fun getOrderedSchemes(schemesMap: Map<String, EditorColorsScheme>): Groups<EditorColorsScheme>

  fun getOrderedSchemesFromArray(schemesMap: Array<EditorColorsScheme>): Groups<EditorColorsScheme> =
    getOrderedSchemesFromSequence(schemesMap.asSequence())

  fun getOrderedSchemesFromSequence(schemes: Sequence<EditorColorsScheme>): Groups<EditorColorsScheme> {
    val map = mutableMapOf<String, EditorColorsScheme>()
    schemes.forEach { map[it.name] = it }
    return getOrderedSchemes(map)
  }
}

@Internal
class Groups<T>(val infos: List<GroupInfo<T>>) {
  class GroupInfo<A>(val items: List<A>, val title: @Nls String? = null)

  companion object {
    fun <T>create(items: List<List<T>>): Groups<T> = Groups(items.map { GroupInfo(it) })
  }

  val items: List<T> get() = infos.flatMap { it.items }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors

import com.intellij.openapi.editor.colors.EditorColorSchemesSorter
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.Groups
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.WeakReference


internal class ColorAndFontOptionsModel {
  private var schemes: MutableMap<String, EditorColorsScheme> = mutableMapOf()
  var selectedScheme: EditorColorsScheme? = null
    private set
  var preselectedSchemeName: String? = null
    private set

  private val listeners: MutableList<ColorAndFontOptionsModelListener> = mutableListOf()
  private var batchedCounter = 0

  fun allSchemes(): Collection<EditorColorsScheme> = schemes.values
  fun getScheme(name: String) = schemes[name]
  fun putScheme(name: String, scheme: EditorColorsScheme, source: Any) {
    schemes += name to scheme
    notifyListeners(source)
  }
  fun dropSchemes(source: Any) {
    schemes = mutableMapOf()
    notifyListeners(source)
  }
  fun removeScheme(name: String, source: Any) {
    schemes.remove(name)
    notifyListeners(source)
  }

  fun addListener(listener: ColorAndFontOptionsModelListener) {
    listeners.add(listener)
  }

  fun removeListener(listener: ColorAndFontOptionsModelListener) {
    listeners.remove(listener)
  }

  fun schemesKeySet(): Set<String> = schemes.keys

  fun getOrderedSchemes(): Groups<EditorColorsScheme> {
    return EditorColorSchemesSorter.getInstance().getOrderedSchemes(schemes)
  }

  fun setSelectedScheme(scheme: EditorColorsScheme?, source: Any) {
    selectedScheme = scheme
    notifyListeners(source)
  }

  fun setPreselectedSchemeName(schemeName: String?, source: Any) {
    preselectedSchemeName = schemeName
    notifyListeners(source)
  }

  fun setSchemes(newSchemes: Map<String, EditorColorsScheme>, source: Any) {
    schemes = mutableMapOf()
    schemes.putAll(newSchemes)
    notifyListeners(source)
  }

  private fun notifyListeners(source: Any) {
    if (batchedCounter > 0) return
    listeners.forEach { if (it !== source) it.onChanged() }
  }

  fun runBatchedUpdate(source: Any, update: Runnable) {
    batchedCounter += 1
    try {
      update.run()
    }
    finally {
      batchedCounter -= 1
      notifyListeners(source)
    }
  }

  companion object {
    private var instance = WeakReference<ColorAndFontOptionsModel>(null);

    @JvmStatic
    fun getInstance(): ColorAndFontOptionsModel {
      instance.get()?.let { return it }
      val model = ColorAndFontOptionsModel()
      instance = WeakReference(model)
      return model
    }
  }
}

@ApiStatus.Internal
interface ColorAndFontOptionsModelListener {
  fun onChanged()
}
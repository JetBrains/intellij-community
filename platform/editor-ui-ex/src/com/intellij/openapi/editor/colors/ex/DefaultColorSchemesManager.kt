// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.ex

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme
import com.intellij.openapi.editor.colors.impl.EmptyColorScheme
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.ResourceUtil
import org.jetbrains.annotations.ApiStatus
import kotlin.concurrent.Volatile

private const val SCHEME_ELEMENT = "scheme"

@Service
@ApiStatus.Internal
class DefaultColorSchemesManager {
  @Volatile
  var allSchemes: List<DefaultColorsScheme> = emptyList()
    private set

  init {
    allSchemes = try {
      loadState(oldSchemes = emptyList())
    }
    catch (e: Exception) {
      thisLogger().error(e)
      listOf(EmptyColorScheme.INSTANCE)
    }
  }

  companion object {

    @JvmStatic
    fun getInstance(): DefaultColorSchemesManager = service<DefaultColorSchemesManager>()
  }

  fun reload() {
    allSchemes = try {
      loadState(oldSchemes = allSchemes)
    }
    catch (e: Exception) {
      thisLogger().error(e)
      emptyList()
    }
  }

  fun listNames(): List<String> = allSchemes.map { it.name }

  val firstScheme: DefaultColorsScheme
    get() = allSchemes.first()

  fun getScheme(name: String): EditorColorsScheme? = allSchemes.firstOrNull { name == it.name }
}


private fun loadState(oldSchemes: List<DefaultColorsScheme>): List<DefaultColorsScheme> {
  val state = JDOMUtil.load(ResourceUtil.getResourceAsBytes("DefaultColorSchemesManager.xml",
                                                            DefaultColorSchemesManager::class.java.getClassLoader())!!)

  val schemes = ArrayList<DefaultColorsScheme>()
  for (schemeElement in state.getChildren(SCHEME_ELEMENT)) {
    var isUpdated = false
    val nameAttr = schemeElement.getAttribute(AbstractColorsScheme.NAME_ATTR)
    if (nameAttr != null) {
      for (oldScheme in oldSchemes) {
        if (nameAttr.value == oldScheme.name) {
          oldScheme.readExternal(schemeElement)
          schemes.add(oldScheme)
          isUpdated = true
        }
      }
    }
    if (!isUpdated) {
      val newScheme = DefaultColorsScheme()
      newScheme.readExternal(schemeElement)
      schemes.add(newScheme)
    }
  }
  schemes.add(EmptyColorScheme.INSTANCE)
  return java.util.List.copyOf(schemes)
}


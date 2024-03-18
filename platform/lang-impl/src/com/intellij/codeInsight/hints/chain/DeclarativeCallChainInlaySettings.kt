// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.chain

import com.intellij.lang.Language
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "DeclarativeCallChainInlaySettings", storages = [Storage("editor.xml")], category = SettingsCategory.CODE)
internal class DeclarativeCallChainInlaySettings : SimplePersistentStateComponent<DeclarativeCallChainInlaySettings.CallChainsState>(CallChainsState()) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) : DeclarativeCallChainInlaySettings {
      return project.service()
    }
  }

  private val lock = Any()

  fun setLanguageCallChainLength(language: Language, length: Int, defaultLength: Int) {
    synchronized(lock) {
      if (defaultLength == length) {
        state.languageToLength.remove(language.id)
      } else {
        state.languageToLength[language.id] = length
      }
    }
  }

  fun getLanguageCallChainLength(language: Language) : Int? {
    return synchronized(lock) {
      state.languageToLength[language.id]
    }
  }

  internal class CallChainsState : BaseState() {
    var languageToLength: MutableMap<String, Int> by map()
  }
}

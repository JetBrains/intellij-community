// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.chain

import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.APP)
@State(name = "DeclarativeCallChainInlaySettings", storages = [Storage("editor.xml")], category = SettingsCategory.CODE)
class DeclarativeCallChainInlaySettings 
  : SimplePersistentStateComponent<DeclarativeCallChainInlaySettings.CallChainsState>(CallChainsState()) {
  companion object {
    @JvmStatic
    fun getInstance() : DeclarativeCallChainInlaySettings {
      return ApplicationManager.getApplication().service()
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

  class CallChainsState : BaseState() {
    var languageToLength: MutableMap<String, Int> by map()
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.editor.folding

import com.intellij.application.options.editor.CodeFoldingOptionsProvider
import com.intellij.json.JsonBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.BeanConfigurable
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "JsonFoldingSettings", storages = [Storage("editor.xml")], category = SettingsCategory.CODE)
class JsonFoldingSettings : PersistentStateComponent<JsonFoldingSettings?> {

  @JvmField
  var showKeyCount: Boolean = false

  @JvmField
  var showFirstKey: Boolean = false

  override fun getState(): JsonFoldingSettings? {
    return this
  }

  override fun loadState(state: JsonFoldingSettings) {
    XmlSerializerUtil.copyBean<JsonFoldingSettings>(state, this)
  }

  companion object {
    @JvmStatic
    fun getInstance(): JsonFoldingSettings = requireNotNull(ApplicationManager.getApplication().getService(JsonFoldingSettings::class.java)) {
      "JsonFoldingSettings service is not available"
    }
  }
}


class JsonFoldingOptionsProvider : BeanConfigurable<JsonFoldingSettings>(
    JsonFoldingSettings.getInstance(),
    JsonBundle.message("JsonFoldingSettings.title")),
                                   CodeFoldingOptionsProvider {
  init {
    checkBox(JsonBundle.message("JsonFoldingSettings.show.key.count"), instance::showKeyCount)
    checkBox(JsonBundle.message("JsonFoldingSettings.show.first.key"), instance::showFirstKey)
  }
}

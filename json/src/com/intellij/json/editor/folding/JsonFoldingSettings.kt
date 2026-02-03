// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.editor.folding

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.CodeFoldingOptionsProvider
import com.intellij.application.options.editor.checkBox
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.json.JsonBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.ConfigurableWithOptionDescriptors
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.xmlb.XmlSerializerUtil
import java.util.function.Function

@State(name = "JsonFoldingSettings", storages = [Storage("editor.xml")], category = SettingsCategory.CODE)
class JsonFoldingSettings : PersistentStateComponent<JsonFoldingSettings?> {

  @JvmField
  var showKeyCount: Boolean = true

  @JvmField
  var showFirstKey: Boolean = false

  override fun getState(): JsonFoldingSettings {
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

private val showKeyCount: CheckboxDescriptor
  get() = CheckboxDescriptor(JsonBundle.message("JsonFoldingSettings.show.key.count"), JsonFoldingSettings.getInstance()::showKeyCount)
private val showFirstKey: CheckboxDescriptor
  get() = CheckboxDescriptor(JsonBundle.message("JsonFoldingSettings.show.first.key"), JsonFoldingSettings.getInstance()::showFirstKey,
                             comment = JsonBundle.message("JsonFoldingSettings.show.first.key.description"))

class JsonFoldingOptionsProvider : UiDslUnnamedConfigurable.Simple(), CodeFoldingOptionsProvider, ConfigurableWithOptionDescriptors {

  override fun Panel.createContent() {
    group(JsonBundle.message("JsonFoldingSettings.title")) {
      row {
        checkBox(showKeyCount)
      }
      row {
        checkBox(showFirstKey)
      }
    }
  }

  override fun getOptionDescriptors(configurableId: String, nameConverter: Function<in String?, String?>): List<OptionDescription> {
    return listOf(showKeyCount, showFirstKey).map { it.asOptionDescriptor() }
  }
}

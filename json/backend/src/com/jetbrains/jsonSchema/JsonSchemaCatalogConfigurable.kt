// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema

import com.intellij.json.JsonBundle
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected

class JsonSchemaCatalogConfigurable(private val project: Project) : BoundConfigurable(
  JsonBundle.message("configurable.JsonSchemaCatalogConfigurable.display.name"), "settings.json.schema.catalog") {

  private lateinit var remoteCheckBox: JBCheckBox
  private lateinit var catalogCheckBox: JBCheckBox
  private lateinit var preferRemoteCheckBox: JBCheckBox

  override fun createPanel(): DialogPanel {
    return panel {
      row {
        remoteCheckBox = checkBox(JsonBundle.message("checkbox.allow.downloading.json.schemas.from.remote.sources"))
          .onChanged {
            if (!it.isSelected) {
              catalogCheckBox.isSelected = false
              preferRemoteCheckBox.isSelected = false
            }
          }.focused()
          .component
      }
      indent {
        row {
          catalogCheckBox = checkBox(JsonBundle.message("checkbox.use.schemastore.org.json.schema.catalog"))
            .comment(JsonBundle.message("schema.catalog.hint"))
            .component
        }

        row {
          preferRemoteCheckBox = checkBox(JsonBundle.message("checkbox.always.download.the.most.recent.version.of.schemas"))
            .comment(JsonBundle.message("schema.catalog.remote.hint"))
            .component
        }
      }.enabledIf(remoteCheckBox.selected)
    }
  }

  override fun isModified(): Boolean {
    val state = JsonSchemaCatalogProjectConfiguration.getInstance(project).state
    return super.isModified() || state == null
           || state.myIsCatalogEnabled != catalogCheckBox.isSelected
           || state.myIsPreferRemoteSchemas != preferRemoteCheckBox.isSelected
           || state.myIsRemoteActivityEnabled != remoteCheckBox.isSelected

  }

  override fun reset() {
    super.reset()

    val state = JsonSchemaCatalogProjectConfiguration.getInstance(project).state
    remoteCheckBox.isSelected = state == null || state.myIsRemoteActivityEnabled
    catalogCheckBox.isSelected = state == null || state.myIsCatalogEnabled
    preferRemoteCheckBox.isSelected = state == null || state.myIsPreferRemoteSchemas
  }

  override fun apply() {
    super.apply()

    JsonSchemaCatalogProjectConfiguration.getInstance(project).setState(catalogCheckBox.isSelected,
                                                                        remoteCheckBox.isSelected,
                                                                        preferRemoteCheckBox.isSelected)
  }
}
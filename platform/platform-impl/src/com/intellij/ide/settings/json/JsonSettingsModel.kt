// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.settings.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class JsonSettingsModel(val propertyMap: Map<String, PropertyDescriptor>) {

  enum class PropertyType {
    Boolean,
    Integer,
    String,
    Enum,
    StringList,
    StringSet,
    Unsupported
  }

  @Serializable
  data class ComponentsData (
    val components: List<ComponentInfo> = emptyList()
  )

  @Serializable
  data class ComponentInfo (
    val name: String?,
    val scope: String,
    val pluginId: String?,
    val classFqn: String? = null,
    val storage: String?,
    val properties: List<ComponentPropertyInfo> = emptyList()
  )

  @Serializable
  data class ComponentPropertyInfo (
    val name: String,
    val mapTo: String? = null,
    val type: PropertyType,
    val javaType: String? = null,
    val variants: List<VariantInfo> = emptyList()
  )

  data class PropertyDescriptor (
    val componentName: String,
    val name: String,
    val type: PropertyType,
    val storage: String,
    val mapTo: String,
    val variants: List<VariantInfo> = emptyList()
  )

  @Serializable
  data class VariantInfo (
    val value: String,
    val mapTo: String? = null
  )

  companion object {
    val instance: JsonSettingsModel = jsonDataToModel(loadFromJson())

    private fun loadFromJson(): ComponentsData {
      return JsonSettingsModel::class.java.getResourceAsStream("/settings/ide-settings-model.json")?.let { input ->
        val jsonString = input.bufferedReader().use { it.readText() }
        Json.decodeFromString<ComponentsData>(jsonString)
      } ?: ComponentsData()
    }

    private fun jsonDataToModel(jsonData: ComponentsData): JsonSettingsModel {
      val propertyMap = mutableMapOf<String, PropertyDescriptor>()
      jsonData.components.forEach { componentInfo ->
        componentInfo.properties.forEach { propertyInfo ->
          jsonDataToPropertyDescriptor(componentInfo, propertyInfo)?.let {
            val jsonName = "${componentInfo.pluginId}:${componentInfo.scope}:${componentInfo.name}.${propertyInfo.name}"
            propertyMap.putIfAbsent(jsonName, it)
          }
        }
      }
      return JsonSettingsModel(propertyMap)
    }

    private fun jsonDataToPropertyDescriptor(componentData: ComponentInfo, propertyData: ComponentPropertyInfo): PropertyDescriptor? {
      return if (componentData.name != null && componentData.storage != null) {
        PropertyDescriptor(componentData.name, propertyData.name, propertyData.type, componentData.storage,
                           propertyData.mapTo ?: propertyData.name, propertyData.variants)
      }
      else null
    }

  }
}
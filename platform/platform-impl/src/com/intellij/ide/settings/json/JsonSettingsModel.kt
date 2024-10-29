// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.settings.json

import com.intellij.openapi.diagnostic.logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.*
import java.util.function.IntPredicate

private val logger = logger<JsonSettingsModel>()

/**
 * Contains supported settings which are publicly available to end users and can be edited without UI using only Json schema.
 * See [Json Settings](https://youtrack.jetbrains.com/articles/IDEA-A-2100661939/Json-Settings)
 */
@ApiStatus.Internal
class JsonSettingsModel(val propertyMap: Map<String, PropertyDescriptor>) {

  private val propertyPluginIdMap: Map<String, String> by lazy {
    propertyMap.values.mapNotNull { property ->
      property.pluginId?.let { "${property.componentName}.${property.name}" to it }
    }.toMap()
  }


  /**
   * Supported property types.
   */
  enum class PropertyType {
    Boolean,
    Integer,
    String,
    Enum,
    StringList,
    StringSet,
    StringMap,
    Unsupported
  }

  /**
   * Contains a pregenerated raw list of Persistent State Components converted to JsonSettingsModel.
   */
  @VisibleForTesting
  @Serializable
  data class ComponentModel (
    val components: List<ComponentInfo> = emptyList()
  )

  @VisibleForTesting
  @Serializable
  data class ComponentInfo (
    val name: String?,
    val scope: String,
    val pluginId: String?,
    val classFqn: String? = null,
    val storage: String? = null,
    val properties: List<ComponentPropertyInfo> = emptyList()
  ) {
    fun getKey(): String? =
      getNormalizedName()?.let { normalized-> pluginId?.let { "${pluginId}:${scope}:${normalized}" }}

    private fun getNormalizedName() = name?.let { normalizeComponentName(it) }
  }

  @VisibleForTesting
  @Serializable
  data class ComponentPropertyInfo (
    val name: String,
    val mapTo: String? = null,
    val type: PropertyType,
    val javaType: String? = null,
    val variants: List<VariantInfo> = emptyList()
  )

  data class PropertyDescriptor (
    val pluginId: String?,
    val componentName: String,
    val name: String,
    val type: PropertyType,
    val storage: String,
    val mapTo: String,
    val variants: List<VariantInfo> = emptyList(),
    val value: Any? = null
  ) {
    fun getMappedValue(): String? =
      (value as? String)?.let { str ->
        variants.find { it.value == str }?.mapTo ?: value
      }
  }

  @Serializable
  data class VariantInfo (
    val value: String,
    val mapTo: String? = null
  )

  @Serializable
  internal class WhiteList (
    val properties: List<String> = emptyList()
  )

  /**
   * @return Real production Plugin ID instead of "com.intellij" when the IDE is launched in debug or test mode. Not to be used in
   * production.
   */
  @VisibleForTesting
  fun getPluginId(key: String): String? = propertyPluginIdMap[key]

  companion object {
    val instance: JsonSettingsModel by lazy { componentToSettingsModel(loadFromJson()) }

    private fun normalizeComponentName(name: String) = name.replace('.', '-')

    private fun loadFromJson(): ComponentModel {
      return JsonSettingsModel::class.java.getResourceAsStream("/settings/ide-settings-model.json")?.let { input ->
        val jsonString = input.bufferedReader().use { it.readText() }
        Json.decodeFromString<ComponentModel>(jsonString)
      } ?: ComponentModel()
    }

    @VisibleForTesting
    fun componentToSettingsModel(componentModel: ComponentModel): JsonSettingsModel {
      val propertyMap = mutableMapOf<String, PropertyDescriptor>()
      val filteredModel = filterSettings(componentModel)
      filteredModel.components.forEach { componentInfo ->
        componentInfo.properties.forEach { propertyInfo ->
          jsonDataToPropertyDescriptor(componentInfo, propertyInfo)?.let {
            val jsonName = "${componentInfo.getKey()}.${propertyInfo.name}"
            propertyMap.putIfAbsent(jsonName, it)
          }
        }
      }
      return JsonSettingsModel(propertyMap)
    }

    private fun jsonDataToPropertyDescriptor(componentInfo: ComponentInfo, propertyInfo: ComponentPropertyInfo): PropertyDescriptor? {
      return if (componentInfo.name != null && componentInfo.storage != null && componentInfo.pluginId != null) {
        PropertyDescriptor(componentInfo.pluginId, componentInfo.name, propertyInfo.name, propertyInfo.type, componentInfo.storage,
                           propertyInfo.mapTo ?: propertyInfo.name, propertyInfo.variants)
      }
      else null
    }

    private fun filterSettings(componentModel: ComponentModel): ComponentModel {
      val whiteList = JsonSettingsModel::class.java.getResourceAsStream("/settings/ide-settings-whitelist.json")?.let { input ->
        val jsonString = input.bufferedReader().use { it.readText() }
        Json.decodeFromString<WhiteList>(jsonString)
      } ?: WhiteList()
      val filterMap = whiteListToComponentMap(whiteList)
      return ComponentModel(componentModel.components.mapNotNull { filterComponentData(it, filterMap) })
    }

    private fun filterComponentData(componentData: ComponentInfo, filterMap: Map<String, ComponentInfo>): ComponentInfo? =
      componentData.getKey()?.let { componentKey ->
        filterMap[componentKey]?.let { filter ->
          componentData.copy(
            properties = filterProperties(componentData.properties, filter.properties.map { it.name })
          )
        }
      }

    /**
     * A primitive filter: either "*" (all) or a specific name.
     */
    private fun filterProperties(original: List<ComponentPropertyInfo>, nameFilter: List<String>): List<ComponentPropertyInfo> =
      (if (nameFilter.first() == "*") original else original.filter { nameFilter.contains(it.name) })
        .filter { it.type != PropertyType.Unsupported }

    private fun whiteListToComponentMap(whiteList: WhiteList): Map<String, ComponentInfo> {
      val result = mutableMapOf<String, ComponentInfo>()
      whiteList.properties.forEach { propertyName ->
        val componentInfo = parseJsonName(propertyName)
        componentInfo.getKey()?.let { key ->
          val properties = result[key]?.let { it.properties + componentInfo.properties } ?: componentInfo.properties
          result[key] = componentInfo.copy(properties = properties)
        }
      }
      return result
    }

    private fun parseJsonName(jsonName: String): ComponentInfo {
      val chunks = jsonName.split(":")
      if (chunks.size < 3) logger.error("Invalid name: ${jsonName}")
      val propertyParts = chunks[2].split(".")
      if (propertyParts.size < 2) logger.error("Invalid property: ${chunks[3]}")
      val propertyInfo = ComponentPropertyInfo(name = propertyParts[1], type = PropertyType.Unsupported)
      return ComponentInfo(
        name = propertyParts[0],
        pluginId = chunks[0],
        scope = chunks[1],
        properties = listOf(propertyInfo)
      )
    }

    fun toJsonName(original: String): String = allUpperCaseToLowerCase(original)

    private fun allUpperCaseToLowerCase(name: String): String =
      if (name.chars().allMatch(IntPredicate {
          Character.isUpperCase(it) || !Character.isAlphabetic(it)
        })) {
        name.lowercase(Locale.ENGLISH)
      }
      else name

  }
}
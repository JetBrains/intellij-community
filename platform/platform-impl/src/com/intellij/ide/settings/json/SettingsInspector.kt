// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.settings.json

import com.intellij.codeWithMe.ClientId
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.client.ClientSessionsManager
import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.serialization.MutableAccessor
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.getBeanAccessors
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@ApiStatus.Internal
@VisibleForTesting
fun buildComponentModel(): JsonSettingsModel.ComponentModel =
  JsonSettingsModel.ComponentModel(listAppComponents().map { descriptor ->
    JsonSettingsModel.ComponentInfo(
      name = descriptor.state?.name,
      scope = "app",
      pluginId = descriptor.pluginDescriptor?.pluginId?.idString,
      classFqn = descriptor.aClass.name,
      storage = descriptor.findStorage()?.value,
      properties = descriptor.collectFieldInfo()
    )
  })


internal fun listAppComponents(): List<ComponentDescriptor> {
  val descriptors = mutableListOf<ComponentDescriptor>()
  fun processImplementationClass(aClass: Class<*>, descriptor: PluginDescriptor?) {
    if (PersistentStateComponent::class.java.isAssignableFrom(aClass)) {
      val state = getState(aClass)
      @Suppress("UNCHECKED_CAST")
      descriptors.add(
        ComponentDescriptor(
          descriptor?.name?.toString() ?: "",
          aClass as Class<PersistentStateComponent<*>>,
          descriptor,
          state
        )
      )
    }
  }

  val componentManager = ApplicationManager.getApplication() as ComponentManagerImpl
  val localAppSession = ClientSessionsManager.getAppSession(ClientId.localId) as ComponentManagerImpl
  componentManager.processAllImplementationClasses(::processImplementationClass)
  localAppSession.processAllImplementationClasses(::processImplementationClass)

  descriptors.sortWith(
    compareBy<ComponentDescriptor> { it.name }.thenBy { it.aClass.name }
  )
  return descriptors
}

private fun getState(aClass: Class<*>): State? {
  aClass.getAnnotation(State::class.java)?.let { return it }
  aClass.superclass?.let { return getState(it) }
  return null
}


internal data class ComponentDescriptor(
  val name: String,
  val aClass: Class<PersistentStateComponent<*>>,
  val pluginDescriptor: PluginDescriptor?,
  val state: State?
) {
  fun getRoamingTypeString(): String {
    if (state != null) {
      var roamingType: String? = null
      state.storages.forEach {
        if (!it.deprecated) {
          val storageRoamingType =
            if (it.value == StoragePathMacros.NON_ROAMABLE_FILE ||
                it.value == StoragePathMacros.CACHE_FILE ||
                it.value == StoragePathMacros.WORKSPACE_FILE) "DISABLED"
            else it.roamingType.toString()
          if (roamingType == null) {
            roamingType = storageRoamingType
          }
          else {
            if (roamingType != storageRoamingType) {
              roamingType = "MIXED"
            }
          }
        }
      }
      return roamingType ?: ""
    }
    return ""
  }

  fun getCategoryString(): String {
    val roamingType = getRoamingTypeString()
    if (roamingType != RoamingType.DISABLED.toString() && pluginDescriptor != null) {
      if (pluginDescriptor.name == PluginManagerCore.SPECIAL_IDEA_PLUGIN_ID.idString) {
        return state?.category?.name ?: ""
      }
      else {
        return ComponentCategorizer.getPluginCategory(aClass, pluginDescriptor).toString()
      }
    }
    else {
      return ""
    }
  }

  fun findStorage(): Storage? {
    state?.storages?.forEach { storage ->
      if (!storage.deprecated) {
        return storage
      }
    }
    return null
  }

  fun collectFieldInfo(): List<JsonSettingsModel.ComponentPropertyInfo> {
    val infoList = mutableListOf<JsonSettingsModel.ComponentPropertyInfo>()
    @Suppress("IncorrectServiceRetrieving")
    ApplicationManager.getApplication().getService(aClass)?.let { component ->
      component.state?.let { componentState ->
        val accessors = getBeanAccessors(componentState::class.java)
        accessors.forEach {
          val internalName = it.getInternalName()
          val jsonName = JsonSettingsModel.toJsonName(internalName)
          infoList += JsonSettingsModel.ComponentPropertyInfo(jsonName,
                                                              if (internalName == jsonName) null else internalName,
                                                              toModelType(it),
                                                              it.valueClass.typeName,
                                                              getVariants(it.valueClass))
        }
      }
    }
    return infoList
  }

  private fun MutableAccessor.getInternalName(): String {
    this.getAnnotation(OptionTag::class.java)?.let {
      if (it.value.isNotEmpty()) {
        return it.value
      }
    }
    return this.name
  }


  private fun toModelType(accessor: MutableAccessor): JsonSettingsModel.PropertyType {
    val valueClass = accessor.valueClass
    val original = valueClass.typeName
    if (valueClass.isEnum) return JsonSettingsModel.PropertyType.Enum
    when (original) {
      "java.lang.Integer", "int" -> return JsonSettingsModel.PropertyType.Integer
      "java.lang.String" -> return JsonSettingsModel.PropertyType.String
      "java.lang.Boolean", "boolean" -> return JsonSettingsModel.PropertyType.Boolean
    }
    when (accessor.genericType.typeName) {
      "java.util.List<java.lang.String>" -> return JsonSettingsModel.PropertyType.StringList
      "java.util.Set<java.lang.String>" -> return JsonSettingsModel.PropertyType.StringSet
      "java.util.Collection<java.lang.String>" -> return JsonSettingsModel.PropertyType.StringList
      "java.util.Map<java.lang.String, java.lang.String>" -> return JsonSettingsModel.PropertyType.StringMap
    }
    return JsonSettingsModel.PropertyType.Unsupported
  }

  private fun getVariants(valueClass: Class<*>): List<JsonSettingsModel.VariantInfo> {
    if (valueClass.isEnum) {
      return valueClass.enumConstants.map { JsonSettingsModel.VariantInfo(getVariantString(it.toString()), it.toString()) }
    }
    return emptyList()
  }


  private fun getVariantString(original: String): String {
    val buf = StringBuilder()
    original.forEach { c->
      if (Character.isLetter(c) || Character.isDigit(c) || c == '_' || c == '-') {
        buf.append(c)
      }
      else if (c == ' ') {
        buf.append('_')
      }
    }
    return buf.toString().lowercase()
  }
}
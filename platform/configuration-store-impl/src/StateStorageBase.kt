// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")
@file:Internal
@file:OptIn(SettingsInternalApi::class, IntellijInternalApi::class)

package com.intellij.configurationStore

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.ComponentStorageUtil
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.vfs.LargeFileWriteRequestor
import com.intellij.openapi.vfs.SafeWriteRequestor
import com.intellij.platform.settings.*
import com.intellij.serialization.SerializationException
import com.intellij.serialization.xml.createSettingKey
import com.intellij.serialization.xml.deserializeAsJdomElement
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.xmlb.*
import kotlinx.serialization.json.JsonElement
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicReference

@Internal
abstract class StateStorageBase<T : Any> : StateStorage {
  private var isSavingDisabled = false

  @JvmField
  protected val storageDataRef: AtomicReference<T> = AtomicReference()

  protected open val saveStorageDataOnReload: Boolean
    get() = true

  abstract val controller: SettingsController?
  abstract val roamingType: RoamingType?

  final override fun <T : Any> getState(
    component: Any?,
    componentName: String,
    pluginId: PluginId,
    stateClass: Class<T>,
    mergeInto: T?,
    reload: Boolean,
  ): T? {
    val stateElement = getSerializedState(
      storageData = getStorageData(reload = reload),
      component = component,
      componentName = componentName,
      archive = false,
    )
    return deserializeStateWithController(
      stateElement = stateElement,
      stateClass = stateClass,
      mergeInto = mergeInto,
      controller = controller,
      componentName = componentName,
      pluginId = pluginId,
      roamingType = roamingType,
    )
  }

  fun getStorageData(): T = getStorageData(reload = false)

  abstract fun getSerializedState(storageData: T, component: Any?, componentName: String, archive: Boolean): Element?

  protected fun getStorageData(reload: Boolean): T {
    val currentStorageData = storageDataRef.get()
    if (currentStorageData != null && !reload) {
      return currentStorageData
    }

    val newStorageData = loadData()
    if (reload && !saveStorageDataOnReload) {
      // it means that you MUST invoke "save all settings" after reload
      if (storageDataRef.compareAndSet(currentStorageData, null)) {
        return newStorageData
      }
      else {
        return getStorageData(reload = true)
      }
    }
    else if (storageDataRef.compareAndSet(currentStorageData, newStorageData)) {
      return newStorageData
    }
    else {
      return getStorageData(reload = false)
    }
  }

  protected abstract fun loadData(): T

  fun disableSaving() {
    LOG.debug { "Disable saving: ${toString()}" }
    isSavingDisabled = true
  }

  fun enableSaving() {
    LOG.debug { "Enable saving: ${toString()}" }
    isSavingDisabled = false
  }

  protected fun checkIsSavingDisabled(): Boolean {
    if (isSavingDisabled) {
      LOG.debug { "Saving disabled: ${toString()}" }
      return true
    }
    else {
      return false
    }
  }
}

@Internal
abstract class SaveSessionProducerBase : SaveSessionProducer, SafeWriteRequestor, LargeFileWriteRequestor {
  abstract val controller: SettingsController?
  abstract val roamingType: RoamingType?

  final override fun setState(component: Any?, componentName: String, pluginId: PluginId, state: Any?) {
    if (state == null) {
      if (ClientId.isCurrentlyUnderLocalId) {
        setSerializedState(componentName = componentName, element = null)
      }
      return
    }

    val element: Element?
    try {
      element = serializeState(state = state, componentName = componentName, pluginId = pluginId, controller = controller, roamingType = roamingType)
    }
    catch (e: WriteExternalException) {
      LOG.debug(e)
      return
    }
    catch (e: Throwable) {
      LOG.error("Unable to serialize $componentName state", e)
      return
    }

    if (ClientId.isCurrentlyUnderLocalId) {
      setSerializedState(componentName = componentName, element = element)
    }
  }

  abstract fun setSerializedState(componentName: String, element: Element?)
}

internal fun serializeState(state: Any, componentName: String, pluginId: PluginId, controller: SettingsController?, roamingType: RoamingType?): Element? {
  @Suppress("DEPRECATION")
  when (state) {
    is Element -> {
      if (controller != null) {
        val key = SettingDescriptor(
          key = createSettingKey(componentName = componentName, binding = null),
          pluginId = pluginId,
          tags = createTags(componentName, state::class.java, roamingType, extraTag = null),
          serializer = JsonElementSettingSerializerDescriptor,
        )

        val result = controller.doSetItem(key = key, value = jdomToJson(state))
        if (result != SetResult.inapplicable()) {
          return null
        }
      }
      return state
    }
    is com.intellij.openapi.util.JDOMExternalizable -> {
      val element = Element(ComponentStorageUtil.COMPONENT)
      state.writeExternal(element)
      return element
    }
    else -> {
      try {
        val filter = jdomSerializer.getDefaultSerializationFilter()
        val rootBinding = __platformSerializer().getRootBinding(state.javaClass)
        if (controller != null) {
          if (rootBinding is BeanBinding) {
            return serializeWithController(
              rootBinding = rootBinding,
              state = state,
              filter = filter,
              componentName = componentName,
              pluginId = pluginId,
              controller = controller,
              roamingType = roamingType,
            )
          }
          else if (rootBinding is KotlinxSerializationBinding) {
            val keyTags = createTags(componentName, state::class.java, roamingType, extraTag = null)
            val key = SettingDescriptor(
              key = createSettingKey(componentName = componentName, binding = null),
              pluginId = pluginId,
              tags = keyTags,
              serializer = JsonElementSettingSerializerDescriptor,
            )
            val result = controller.doSetItem(key = key, value = rootBinding.toJson(state, filter))
            if (result != SetResult.inapplicable()) {
              return null
            }
          }
        }

        return (rootBinding as RootBinding).serialize(bean = state, filter = filter)
      }
      catch (e: SerializationException) {
        throw e
      }
      catch (e: Exception) {
        throw XmlSerializationException("Can't serialize state (componentName=$componentName, class=${state.javaClass})", e)
      }
    }
  }
}

private fun createTags(componentName: String, stateClass: Class<*>, roamingType: RoamingType?, extraTag: SettingTag?): List<SettingTag> {
  val componentPropertyTag = PersistenceStateComponentPropertyTag(componentName, stateClass)
  if (roamingType == RoamingType.DISABLED) {
    if (extraTag == null) {
      return java.util.List.of(componentPropertyTag, NonShareableTag)
    }
    else {
      return java.util.List.of(componentPropertyTag, NonShareableTag, extraTag)
    }
  }
  else {
    if (extraTag == null) {
      return java.util.List.of(componentPropertyTag)
    }
    else {
      return java.util.List.of(componentPropertyTag, extraTag)
    }
  }
}

private fun serializeWithController(
  rootBinding: BeanBinding,
  state: Any,
  filter: SkipDefaultsSerializationFilter,
  componentName: String,
  pluginId: PluginId,
  controller: SettingsController,
  roamingType: RoamingType?
): Element? {
  val keyTags = createTags(componentName, state::class.java, roamingType, extraTag = null)
  var element: Element? = null
  for (binding in rootBinding.bindings!!) {
    val isPropertySkipped = isPropertySkipped(filter = filter, binding = binding, bean = state, rootBinding = rootBinding, isFilterPropertyItself = true)
    val key = SettingDescriptor(key = createSettingKey(componentName, binding), pluginId = pluginId, tags = keyTags, serializer = JsonElementSettingSerializerDescriptor)
    val result = controller.doSetItem(key = key, value = if (isPropertySkipped) null else binding.toJson(state, filter))
    if (isPropertySkipped) {
      continue
    }

    var effectiveState = state
    if (result != SetResult.inapplicable()) {
      val value = result.value
      if (value is JsonElement) {
        // substituted value
        effectiveState = rootBinding.newInstance()
        binding.setFromJson(effectiveState, value)
      }
      else {
        continue
      }
    }

    if (element == null) {
      element = Element(rootBinding.tagName)
    }

    binding.serialize(bean = effectiveState, parent = element, filter = filter)
  }
  return element
}

@Suppress("DEPRECATION", "UNCHECKED_CAST")
internal fun <T : Any> deserializeStateWithController(
  stateElement: Element?,
  stateClass: Class<T>,
  mergeInto: T?,
  controller: SettingsController?,
  componentName: String,
  pluginId: PluginId,
  roamingType: RoamingType?,
): T? {
  if (stateClass === Element::class.java) {
    if (controller == null) {
      return stateElement as T? ?: mergeInto
    }
    else {
      return deserializeAsJdomElement(
        localValue = stateElement,
        controller = controller,
        componentName = componentName,
        pluginId = pluginId,
        tags = createTags(componentName, stateClass, roamingType, extraTag = OldLocalValueSupplierTag(supplier = SynchronizedClearableLazy {
          stateElement?.let {
            jdomToJson(it)
          }
        })),
      ) as T? ?: mergeInto
    }
  }
  else if (com.intellij.openapi.util.JDOMExternalizable::class.java.isAssignableFrom(stateClass)) {
    if (stateElement == null) {
      return mergeInto
    }
    if (mergeInto != null) {
      LOG.error("State is ${stateClass.name}, merge into is $mergeInto, state element text is ${JDOMUtil.writeElement(stateElement)}")
    }
    return deserializeJdomExternalizable(stateClass = stateClass, stateElement = stateElement)
  }

  if (stateElement == null && controller == null) {
    return mergeInto
  }

  val serializer = __platformSerializer()
  val rootBinding = serializer.getRootBinding(stateClass)
  try {
    if (mergeInto == null) {
      if (controller != null) {
        if (rootBinding is BeanBinding) {
          return getXmlSerializedState<T>(
            oldData = stateElement,
            mergeInto = null,
            rootBinding = rootBinding,
            componentName = componentName,
            stateClass = stateClass,
            pluginId = pluginId,
            controller = controller,
            roamingType = roamingType,
          )
        }
        else if (rootBinding is KotlinxSerializationBinding) {
          return getKotlinxSerializedState(
            oldData = stateElement,
            rootBinding = rootBinding,
            componentName = componentName,
            stateClass = stateClass,
            pluginId = pluginId,
            controller = controller,
            roamingType = roamingType,
          )
        }
      }
      return rootBinding.deserialize(context = null, element = stateElement ?: return null, JdomAdapter) as T
    }
    else {
      if (controller == null) {
        if (stateElement == null) {
          return mergeInto
        }
        (rootBinding as BeanBinding).deserializeInto(bean = mergeInto, element = stateElement)
      }
      else {
        return getXmlSerializedState(
          oldData = stateElement,
          mergeInto = mergeInto,
          rootBinding = rootBinding as BeanBinding,
          componentName = componentName,
          stateClass = stateClass,
          pluginId = pluginId,
          controller = controller,
          roamingType = roamingType,
        )
      }
      return mergeInto
    }
  }
  catch (e: SerializationException) {
    throw e
  }
  catch (e: Exception) {
    throw XmlSerializationException("Cannot deserialize class ${stateClass.name}", e)
  }
}

private val NULL_OLD_LOCAL_VALUE_SUPPLIER = OldLocalValueSupplierTag { null }

private fun <T : Any> getXmlSerializedState(
  oldData: Element?,
  mergeInto: T?,
  rootBinding: BeanBinding,
  componentName: String,
  stateClass: Class<T>,
  pluginId: PluginId,
  controller: SettingsController,
  roamingType: RoamingType?,
): T? {
  var result = mergeInto
  val bindings = rootBinding.bindings!!

  var currentBinding: NestedBinding? = null

  val oldDataSupplierLazyValue: SynchronizedClearableLazy<JsonElement?>?
  val oldValueTagSupplier: OldLocalValueSupplierTag
  if (oldData == null) {
    oldDataSupplierLazyValue = null
    oldValueTagSupplier = NULL_OLD_LOCAL_VALUE_SUPPLIER
  }
  else {
    oldDataSupplierLazyValue = SynchronizedClearableLazy {
      deserializeNestedBindingToJson(oldData, currentBinding!!)
    }
    oldValueTagSupplier = OldLocalValueSupplierTag(oldDataSupplierLazyValue)
  }

  val componentPropertyTag = PersistenceStateComponentPropertyTag(componentName, stateClass)
  val keyTags = if (roamingType == RoamingType.DISABLED) {
    java.util.List.of(oldValueTagSupplier, componentPropertyTag, NonShareableTag)
  }
  else {
    java.util.List.of(oldValueTagSupplier, componentPropertyTag)
  }

  for (binding in bindings) {
    oldDataSupplierLazyValue?.drop()
    currentBinding = binding

    val key = SettingDescriptor(
      key = createSettingKey(componentName = componentName, binding = binding),
      pluginId = pluginId,
      tags = keyTags,
      serializer = JsonElementSettingSerializerDescriptor,
    )
    val value = try {
      controller.doGetItem(key)
    }
    catch (e: Throwable) {
      LOG.error("Cannot deserialize value for $key", e)
      GetResult.inapplicable()
    }

    if (value.isResolved) {
      val jsonElement = value.get()
      if (jsonElement != null) {
        if (result == null) {
          // create a result only if we have some data - do not return empty state class
          @Suppress("UNCHECKED_CAST")
          result = rootBinding.newInstance() as T
        }

        binding.setFromJson(result, jsonElement)
      }
    }
    else if (oldData != null) {
      if (result == null) {
        // create a result only if we have some data - do not return empty state class
        @Suppress("UNCHECKED_CAST")
        result = rootBinding.newInstance() as T
      }
      deserializeNestedBindingInto(result = result, element = oldData, binding = binding)
    }
  }
  return result
}


private fun <T : Any> getKotlinxSerializedState(
  oldData: Element?,
  rootBinding: KotlinxSerializationBinding,
  componentName: String,
  stateClass: Class<T>,
  pluginId: PluginId,
  controller: SettingsController,
  roamingType: RoamingType?,
): T? {
  val oldValueTagSupplier = if (oldData == null) {
    NULL_OLD_LOCAL_VALUE_SUPPLIER
  }
  else {
    OldLocalValueSupplierTag(SynchronizedClearableLazy {
      rootBinding.deserializeToJson(oldData)
    })
  }

  val componentPropertyTag = PersistenceStateComponentPropertyTag(componentName, stateClass)
  val keyTags = if (roamingType == RoamingType.DISABLED) {
    java.util.List.of(oldValueTagSupplier, componentPropertyTag, NonShareableTag)
  }
  else {
    java.util.List.of(oldValueTagSupplier, componentPropertyTag)
  }

  val key = SettingDescriptor(
    key = createSettingKey(componentName = componentName, binding = null),
    pluginId = pluginId,
    tags = keyTags,
    serializer = JsonElementSettingSerializerDescriptor,
  )
  val value = try {
    controller.doGetItem(key)
  }
  catch (e: Throwable) {
    LOG.error("Cannot deserialize value for $key", e)
    GetResult.inapplicable()
  }

  if (value.isResolved) {
    val jsonElement = value.get()
    if (jsonElement != null) {
      @Suppress("UNCHECKED_CAST")
      return rootBinding.fromJson(currentValue = null, element = jsonElement) as T?
    }
  }
  else if (oldData != null) {
    @Suppress("UNCHECKED_CAST")
    return rootBinding.deserialize(context = null, element = oldData, adapter = JdomAdapter) as T?
  }
  return null
}
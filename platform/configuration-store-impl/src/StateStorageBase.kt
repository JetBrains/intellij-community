// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package com.intellij.configurationStore

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.ComponentStorageUtil
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.vfs.LargeFileWriteRequestor
import com.intellij.openapi.vfs.SafeWriteRequestor
import com.intellij.platform.settings.*
import com.intellij.serialization.SerializationException
import com.intellij.util.xmlb.*
import kotlinx.serialization.json.jsonObject
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
    )
  }

  @Internal
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


abstract class SaveSessionProducerBase : SaveSessionProducer, SafeWriteRequestor, LargeFileWriteRequestor {
  abstract val controller: SettingsController?
  abstract val roamingType: RoamingType?

  final override fun setState(component: Any?, componentName: String, pluginId: PluginId, state: Any?) {
    if (state == null) {
      setSerializedState(componentName = componentName, element = null)
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

    setSerializedState(componentName = componentName, element = element)
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
          tags = createTags(componentName, roamingType),
          serializer = JsonElementSettingSerializerDescriptor,
        )

        val result = controller.doSetItem(key = key, value = jdomToJson(state))
        if (result != SetResult.INAPPLICABLE) {
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
        if (rootBinding is BeanBinding) {
          if (controller == null) {
            return rootBinding.serializeProperties(bean = state, preCreatedElement = null, filter = filter)
          }
          else {
            return serializeWithController(
              rootBinding = rootBinding,
              bean = state,
              filter = filter,
              componentName = componentName,
              pluginId = pluginId,
              controller = controller,
              roamingType = roamingType,
            )
          }
        }
        else {
          // maybe ArrayBinding
          return (rootBinding as RootBinding).serialize(bean = state, filter = filter) as Element
        }
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

private fun createTags(componentName: String, roamingType: RoamingType?): List<SettingTag> {
  val componentPropertyTag = PersistenceStateComponentPropertyTag(componentName)
  return if (roamingType == RoamingType.DISABLED) java.util.List.of(NonShareableTag, componentPropertyTag) else java.util.List.of(componentPropertyTag)
}

private fun serializeWithController(
  rootBinding: BeanBinding,
  bean: Any,
  filter: SkipDefaultsSerializationFilter,
  componentName: String,
  pluginId: PluginId,
  controller: SettingsController,
  roamingType: RoamingType?
): Element? {
  val keyTags = createTags(componentName, roamingType)
  var element: Element? = null
  for (binding in rootBinding.bindings!!) {
    val isPropertySkipped = isPropertySkipped(filter = filter, binding = binding, bean = bean, isFilterPropertyItself = true)
    val key = SettingDescriptor(key = createSettingKey(componentName, binding), pluginId = pluginId, tags = keyTags, serializer = JsonElementSettingSerializerDescriptor)
    val result = controller.doSetItem(key = key, value = if (isPropertySkipped) null else binding.toJson(bean, filter))
    if (result != SetResult.INAPPLICABLE) {
      continue
    }

    if (isPropertySkipped) {
      continue
    }

    if (element == null) {
      element = Element(rootBinding.tagName)
    }

    binding.serialize(bean = bean, parent = element, filter = filter)
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
): T? {
  if (stateClass === Element::class.java) {
    return deserializeAsJdomElement(
      controller = controller,
      componentName = componentName,
      pluginId = pluginId,
      stateElement = stateElement,
    ) as T? ?: mergeInto
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
  // KotlinxSerializationBinding here is possible, do not cast to BeanBinding
  val rootBinding = serializer.getRootBinding(stateClass)
  try {
    if (mergeInto == null) {
      if (rootBinding !is BeanBinding || controller == null) {
        if (stateElement == null) {
          return null
        }
        return rootBinding.deserialize(context = null, element = stateElement, JdomAdapter) as T
      }

      return getXmlSerializedState<T>(
        oldData = stateElement,
        mergeInto = null,
        rootBinding = rootBinding,
        componentName = componentName,
        pluginId = pluginId,
        controller = controller,
      )
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
          pluginId = pluginId,
          controller = controller,
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

// todo OldLocalValueSupplierTag for bean /

private fun deserializeAsJdomElement(
  controller: SettingsController?,
  componentName: String,
  pluginId: PluginId,
  stateElement: Element?,
): Element? {
  try {
    val tags = java.util.List.of(PersistenceStateComponentPropertyTag(componentName), OldLocalValueSupplierTag(supplier = lazy {
      stateElement?.let {
        jdomToJson(it)
      }
    }))
    val key = SettingDescriptor(
      key = createSettingKey(componentName = componentName, binding = null),
      pluginId = pluginId,
      tags = tags,
      serializer = JsonElementSettingSerializerDescriptor,
    )
    val item = controller?.doGetItem(key) ?: GetResult.inapplicable()
    if (item.isResolved) {
      val jsonObject = item.get() ?: return null
      return jsonDomToXml(jsonObject.jsonObject)
    }
  }
  catch (e: Throwable) {
    LOG.error("Cannot deserialize value for $componentName", e)
  }
  return stateElement
}

private fun <T : Any> getXmlSerializedState(
  oldData: Element?,
  mergeInto: T?,
  rootBinding: BeanBinding,
  componentName: String,
  pluginId: PluginId,
  controller: SettingsController,
): T? {
  var result = mergeInto
  val bindings = rootBinding.bindings!!

  val keyTags = java.util.List.of(PersistenceStateComponentPropertyTag(componentName))
  for (binding in bindings) {
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
      val l = deserializeBeanInto(result = result, element = oldData, binding = binding, checkAttributes = true)
      if (l != null) {
        (binding as MultiNodeBinding).deserializeList(currentValue = result, elements = l, adapter = JdomAdapter)
      }
    }
  }
  return result
}

private fun createSettingKey(componentName: String, binding: NestedBinding?): String {
  val normalizedComponentName = componentName.replace('.', '-')
  if (binding == null) {
    return normalizedComponentName
  }
  else {
    return "$normalizedComponentName.${binding.propertyName}"
  }
}
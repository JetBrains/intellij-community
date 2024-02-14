// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SafeStAXStreamBuilder
import com.intellij.platform.settings.GetResult
import com.intellij.platform.settings.RawSettingSerializerDescriptor
import com.intellij.platform.settings.SettingDescriptor
import com.intellij.platform.settings.SettingsController
import com.intellij.serialization.SerializationException
import com.intellij.util.xml.dom.createXmlStreamReader
import com.intellij.util.xmlb.BeanBinding
import com.intellij.util.xmlb.NotNullDeserializeBinding
import com.intellij.util.xmlb.XmlSerializationException
import com.intellij.util.xmlb.deserializeBeanInto
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

abstract class StorageBaseEx<T : Any> : StateStorageBase<T>() {
  internal fun <S : Any> createGetSession(
    component: PersistentStateComponent<S>,
    componentName: String,
    pluginId: PluginId,
    stateClass: Class<S>,
    reload: Boolean = false,
  ): StateGetter<S> {
    return StateGetterImpl(
      component = component,
      componentName = componentName,
      storageData = getStorageData(reload),
      stateClass = stateClass,
      storage = this,
      pluginId = pluginId,
    )
  }

  /**
   * serializedState is null if state equals to default (see XmlSerializer.serializeIfNotDefault)
   */
  abstract fun archiveState(storageData: T, componentName: String, serializedState: Element?)
}

internal fun <S : Any> createStateGetter(
  isUseLoadedStateAsExisting: Boolean,
  storage: StateStorage,
  component: PersistentStateComponent<S>,
  componentName: String,
  pluginId: PluginId,
  stateClass: Class<S>,
  reloadData: Boolean,
): StateGetter<S> {
  if (isUseLoadedStateAsExisting && storage is StorageBaseEx<*>) {
    return storage.createGetSession(
      component = component,
      componentName = componentName,
      stateClass = stateClass,
      reload = reloadData,
      pluginId = pluginId,
    )
  }

  return object : StateGetter<S> {
    override fun getState(mergeInto: S?): S? {
      return storage.getState(
        component = component,
        componentName = componentName,
        pluginId = pluginId,
        stateClass = stateClass,
        mergeInto = mergeInto,
        reload = reloadData,
      )
    }

    override fun archiveState(): S? = null
  }
}

@ApiStatus.Internal
interface StateGetter<S : Any> {
  fun getState(mergeInto: S? = null): S?

  fun archiveState(): S?
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
    try {
      val item = controller?.doGetItem(createSettingDescriptor(key = componentName, pluginId = pluginId)) ?: GetResult.inapplicable()
      if (item.isResolved) {
        val xmlData = item.get() ?: return null
        val xmlStreamReader = createXmlStreamReader(xmlData)
        try {
          return SafeStAXStreamBuilder.buildNsUnawareAndClose(xmlStreamReader) as T
        }
        finally {
          xmlStreamReader.close()
        }
      }
    }
    catch (e: Throwable) {
      LOG.error("Cannot deserialize value for $componentName", e)
    }
    return stateElement as T?
  }
  else if (com.intellij.openapi.util.JDOMExternalizable::class.java.isAssignableFrom(stateClass)) {
    if (stateElement == null) {
      return mergeInto
    }

    if (mergeInto != null) {
      LOG.error("State is ${stateClass.name}, merge into is $mergeInto, state element text is ${JDOMUtil.writeElement(stateElement)}")
    }

    val t = MethodHandles.privateLookupIn(stateClass, MethodHandles.lookup())
      .findConstructor(stateClass, MethodType.methodType(Void.TYPE))
      .invoke() as com.intellij.openapi.util.JDOMExternalizable
    t.readExternal(stateElement)
    return t as T
  }

  if (stateElement == null && controller == null) {
    return mergeInto
  }

  val serializer = __platformSerializer()
  // KotlinxSerializationBinding here is possible, do not cast to BeanBinding
  val rootBinding = serializer.getRootBinding(stateClass) as NotNullDeserializeBinding
  try {
    if (mergeInto == null) {
      if (rootBinding !is BeanBinding || controller == null) {
        if (stateElement == null) {
          return null
        }
        return rootBinding.deserialize(null, stateElement) as T
      }

      return getXmlSerializationState<T>(
        oldData = stateElement,
        mergeInto = null,
        rootBinding = rootBinding,
        controller = controller,
        componentName = componentName,
        pluginId = pluginId,
      )
    }
    else {
      if (controller == null) {
        if (stateElement == null) {
          return mergeInto
        }
        (rootBinding as BeanBinding).deserializeInto(mergeInto, stateElement)
      }
      else {
        return getXmlSerializationState(
          oldData = stateElement,
          mergeInto = mergeInto,
          rootBinding = rootBinding as BeanBinding,
          controller = controller,
          componentName = componentName,
          pluginId = pluginId,
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

private fun <T : Any> getXmlSerializationState(
  oldData: Element?,
  mergeInto: T?,
  rootBinding: BeanBinding,
  componentName: String,
  pluginId: PluginId,
  controller: SettingsController,
): T? {
  var result = mergeInto
  val bindings = rootBinding.bindings!!

  for ((index, binding) in bindings.withIndex()) {
    val data = getXmlDataFromController(
      key = createSettingDescriptor(key = "$componentName.${binding.accessor.name}", pluginId = pluginId),
      controller = controller,
    )
    if (!data.isResolved) {
      if (oldData != null) {
        if (result == null) {
          // create a result only if we have some data - do not return empty state class
          @Suppress("UNCHECKED_CAST")
          result = rootBinding.newInstance() as T
        }
        deserializeBeanInto(result = result, element = oldData, bindings = bindings, start = index, end = index + 1)
      }
      continue
    }

    val element = data.get()
    if (element != null) {
      if (result == null) {
        // create a result only if we have some data - do not return empty state class
        @Suppress("UNCHECKED_CAST")
        result = rootBinding.newInstance() as T
      }

      deserializeBeanInto(result = result, element = element, bindings = bindings, start = index, end = index + 1)
    }
  }
  return result
}

private fun getXmlDataFromController(key: SettingDescriptor<ByteArray>, controller: SettingsController): GetResult<Element> {
  try {
    val item = controller.doGetItem(key)
    if (item.isResolved) {
      val xmlData = item.get() ?: return GetResult.resolved(null)
      val xmlStreamReader = createXmlStreamReader(xmlData)
      try {
        return GetResult.resolved(SafeStAXStreamBuilder.buildNsUnawareAndClose(xmlStreamReader))
      }
      finally {
        xmlStreamReader.close()
      }
    }
  }
  catch (e: Throwable) {
    LOG.error("Cannot deserialize value for $key", e)
  }
  return GetResult.inapplicable()
}

internal fun createSettingDescriptor(key: String, pluginId: PluginId): SettingDescriptor<ByteArray> {
  return SettingDescriptor(
    key = key,
    pluginId = pluginId,
    tags = emptyList(),
    serializer = RawSettingSerializerDescriptor,
  )
}

private class StateGetterImpl<S : Any, T : Any>(
  private val component: PersistentStateComponent<S>,
  private val componentName: String,
  private val pluginId: PluginId,
  private val storageData: T,
  private val stateClass: Class<S>,
  private val storage: StorageBaseEx<T>,
) : StateGetter<S> {
  private var serializedState: Element? = null

  override fun getState(mergeInto: S?): S? {
    LOG.assertTrue(serializedState == null)

    serializedState = storage.getSerializedState(
      storageData = storageData,
      component = component,
      componentName = componentName,
      archive = false,
    )
    return deserializeStateWithController(
      stateElement = serializedState,
      stateClass = stateClass,
      mergeInto = mergeInto,
      controller = storage.controller,
      componentName = componentName,
      pluginId = pluginId,
    )
  }

  override fun archiveState(): S? {
    if (serializedState == null) {
      return null
    }

    val stateAfterLoad = try {
      component.state
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      PluginException.logPluginError(LOG, "Cannot get state after load", e, component.javaClass)
      null
    }

    val serializedStateAfterLoad = if (stateAfterLoad == null) {
      serializedState
    }
    else {
      serializeState(state = stateAfterLoad, componentName = componentName)?.normalizeRootName()?.takeIf {
        !it.isEmpty
      }
    }

    if (ApplicationManager.getApplication().isUnitTestMode &&
        serializedState != serializedStateAfterLoad &&
        (serializedStateAfterLoad == null || !JDOMUtil.areElementsEqual(serializedState, serializedStateAfterLoad))) {
      LOG.debug("$componentName (from ${component.javaClass.name}) state changed after load. " +
                "\nOld: ${JDOMUtil.writeElement(serializedState!!)}\n" +
                "\nNew: ${serializedStateAfterLoad?.let { JDOMUtil.writeElement(it) } ?: "null"}\n")
    }

    storage.archiveState(storageData = storageData, componentName = componentName, serializedState = serializedStateAfterLoad)
    return stateAfterLoad
  }
}
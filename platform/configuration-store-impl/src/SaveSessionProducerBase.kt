// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.impl.stores.FileStorageCoreUtil
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.vfs.LargeFileWriteRequestor
import com.intellij.openapi.vfs.SafeWriteRequestor
import org.jdom.Element

abstract class SaveSessionProducerBase : SaveSessionProducer, SafeWriteRequestor, LargeFileWriteRequestor {
  final override fun setState(component: Any?, componentName: String, state: Any?) {
    if (state == null) {
      setSerializedState(componentName = componentName, element = null)
      return
    }

    val element: Element?
    try {
      element = serializeState(state)
    }
    catch (e: WriteExternalException) {
      LOG.debug(e)
      return
    }
    catch (e: Throwable) {
      LOG.error("Unable to serialize $componentName state", e)
      return
    }

    setSerializedState(componentName, element)
  }

  abstract fun setSerializedState(componentName: String, element: Element?)
}

internal fun serializeState(state: Any): Element? {
  @Suppress("DEPRECATION")
  return when (state) {
    is Element -> state
    is com.intellij.openapi.util.JDOMExternalizable -> {
      val element = Element(FileStorageCoreUtil.COMPONENT)
      state.writeExternal(element)
      element
    }
    else -> serialize(state)
  }
}

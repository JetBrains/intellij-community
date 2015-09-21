/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.configurationStore

import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.util.JDOMExternalizable
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.vfs.SafeWriteRequestor
import com.intellij.reference.SoftReference
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element

abstract class SaveSessionBase : StateStorage.SaveSession, StateStorage.ExternalizationSession, SafeWriteRequestor {
  override final fun setState(component: Any?, componentName: String, state: Any) {
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

  protected abstract fun setSerializedState(componentName: String, element: Element?)
}

private val skipDefaultsSerializationFilter = ThreadLocal<SoftReference<SkipDefaultsSerializationFilter>>()

fun serializeState(state: Any): Element? {
  if (state is Element) {
    return state
  }
  else if (state is JDOMExternalizable) {
    val element = Element("temp_element")
    state.writeExternal(element)
    return element
  }
  else {
    var serializationFilter = SoftReference.dereference(skipDefaultsSerializationFilter.get())
    if (serializationFilter == null) {
      serializationFilter = SkipDefaultsSerializationFilter()
      skipDefaultsSerializationFilter.set(SoftReference(serializationFilter))
    }
    return XmlSerializer.serializeIfNotDefault(state, serializationFilter)
  }
}

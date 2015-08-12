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

import com.intellij.openapi.components.impl.stores.StateMap
import com.intellij.openapi.components.impl.stores.StateMap.getNewByteIfDiffers
import com.intellij.openapi.components.impl.stores.StorageDataBase
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.SmartHashSet
import org.jdom.Attribute
import org.jdom.Element
import java.util.Arrays

open class StorageData : StorageDataBase {
  val states: StateMap

  public constructor() {
    states = StateMap()
  }

  protected constructor(storageData: StorageData) {
    states = StateMap(storageData.states)
  }

  public open fun save(newLiveStates: Map<String, Element>, rootElementName: String): Element? {
    if (states.isEmpty()) {
      return null
    }

    val rootElement = Element(rootElementName)
    val componentNames = ArrayUtil.toStringArray(states.keys())
    Arrays.sort(componentNames)
    for (componentName in componentNames) {
      assert(componentName != null)
      val element = states.getElement(componentName, newLiveStates)
      // name attribute should be first
      val elementAttributes = element.getAttributes()
      if (elementAttributes.isEmpty()) {
        element.setAttribute(StorageDataBase.NAME, componentName)
      }
      else {
        var nameAttribute: Attribute? = element.getAttribute(StorageDataBase.NAME)
        if (nameAttribute == null) {
          nameAttribute = Attribute(StorageDataBase.NAME, componentName)
          elementAttributes.add(0, nameAttribute)
        }
        else {
          nameAttribute.setValue(componentName)
          if (elementAttributes.get(0) != nameAttribute) {
            elementAttributes.remove(nameAttribute)
            elementAttributes.add(0, nameAttribute)
          }
        }
      }

      rootElement.addContent(element)
    }
    return rootElement
  }

  open fun clone(): StorageData = StorageData(this)

  override fun hasState(componentName: String) = states.hasState(componentName)
}

fun setStateAndCloneIfNeed(componentName: String, newState: Element?, storageData: StorageData, newLiveStates: MutableMap<String, Element>): StorageData? {
  val oldState = storageData.states.get(componentName)
  if (newState == null || JDOMUtil.isEmpty(newState)) {
    if (oldState == null) {
      return null
    }

    val newStorageData = storageData.clone()
    newStorageData.states.remove(componentName)
    return newStorageData
  }

  prepareElement(newState)

  newLiveStates.put(componentName, newState)

  var newBytes: ByteArray? = null
  if (oldState is Element) {
    if (JDOMUtil.areElementsEqual(oldState as Element?, newState)) {
      return null
    }
  }
  else if (oldState != null) {
    newBytes = getNewByteIfDiffers(componentName, newState, oldState as ByteArray)
    if (newBytes == null) {
      return null
    }
  }

  val newStorageData = storageData.clone()
  newStorageData.states.put(componentName, if (newBytes == null) newState else newBytes)
  return newStorageData
}

fun prepareElement(state: Element) {
  if (state.getParent() != null) {
    LOG.warn("State element must not have parent ${JDOMUtil.writeElement(state)}")
    state.detach()
  }
  state.setName(StorageDataBase.COMPONENT)
}

fun StateMap.setState(componentName: String, newState: Element?, newLiveStates: MutableMap<String, Element>): Any? {
  if (newState == null || JDOMUtil.isEmpty(newState)) {
    return remove(componentName)
  }

  prepareElement(newState)

  newLiveStates.put(componentName, newState)

  val oldState = get(componentName)

  var newBytes: ByteArray? = null
  if (oldState is Element) {
    if (JDOMUtil.areElementsEqual(oldState as Element?, newState)) {
      return null
    }
  }
  else if (oldState != null) {
    newBytes = getNewByteIfDiffers(componentName, newState, oldState as ByteArray)
    if (newBytes == null) {
      return null
    }
  }

  put(componentName, if (newBytes == null) newState else newBytes)
  return newState
}

// newStorageData - myStates contains only live (unarchived) states
fun StateMap.getChangedComponentNames(newStorageData: StorageData): Set<String> {
  val bothStates = SmartHashSet(keys())
  bothStates.retainAll(newStorageData.states.keys())

  val diffs = SmartHashSet<String>()
  diffs.addAll(newStorageData.states.keys())
  diffs.addAll(keys())
  diffs.removeAll(bothStates)

  for (componentName in bothStates) {
    compare(componentName, newStorageData.states, diffs)
  }
  return diffs
}

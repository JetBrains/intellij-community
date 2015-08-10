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

import com.intellij.openapi.components.PathMacroSubstitutor
import com.intellij.openapi.components.impl.stores.StateMap
import com.intellij.openapi.components.impl.stores.StorageDataBase
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.containers.SmartHashSet
import org.jdom.Attribute
import org.jdom.Element

import java.io.IOException
import java.util.Arrays

import com.intellij.openapi.components.impl.stores.StateMap.getNewByteIfDiffers

public open class StorageData : StorageDataBase {
  private val myStates: StateMap

  public open fun isDirty(): Boolean = false

  public constructor() {
    myStates = StateMap()
  }

  protected constructor(storageData: StorageData) {
    myStates = StateMap(storageData.myStates)
  }

  override fun getComponentNames() = myStates.keys()

  public open fun load(rootElement: Element, pathMacroSubstitutor: PathMacroSubstitutor?, intern: Boolean) {
    StorageDataBase.load(myStates, rootElement, pathMacroSubstitutor, intern)
  }

  public open fun save(newLiveStates: Map<String, Element>, rootElementName: String): Element? {
    if (myStates.isEmpty()) {
      return null
    }

    val rootElement = Element(rootElementName)
    val componentNames = ArrayUtil.toStringArray(myStates.keys())
    Arrays.sort(componentNames)
    for (componentName in componentNames) {
      assert(componentName != null)
      val element = myStates.getElement(componentName, newLiveStates)
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
          if (elementAttributes.get(0) !== nameAttribute) {
            elementAttributes.remove(nameAttribute)
            elementAttributes.add(0, nameAttribute)
          }
        }
      }

      rootElement.addContent(element)
    }
    return rootElement
  }

  public fun getState(name: String): Element? = myStates.getState(name)

  public fun getStateAndArchive(name: String): Element? = myStates.getStateAndArchive(name)

  public fun setState(componentName: String, newState: Element?, newLiveStates: MutableMap<String, Element>): Any? {
    if (newState == null || JDOMUtil.isEmpty(newState)) {
      return myStates.remove(componentName)
    }

    prepareElement(newState)

    newLiveStates.put(componentName, newState)

    val oldState = myStates.get(componentName)

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

    myStates.put(componentName, if (newBytes == null) newState else newBytes)
    return newState
  }

  open fun clone(): StorageData = StorageData(this)

  // newStorageData - myStates contains only live (unarchived) states
  public open fun getChangedComponentNames(newStorageData: StorageData, substitutor: PathMacroSubstitutor?): Set<String> {
    val bothStates = SmartHashSet(myStates.keys())
    bothStates.retainAll(newStorageData.myStates.keys())

    val diffs = SmartHashSet<String>()
    diffs.addAll(newStorageData.myStates.keys())
    diffs.addAll(myStates.keys())
    diffs.removeAll(bothStates)

    for (componentName in bothStates) {
      myStates.compare(componentName, newStorageData.myStates, diffs)
    }
    return diffs
  }

  override fun hasState(componentName: String) = myStates.hasState(componentName)

  companion object {
    public fun setStateAndCloneIfNeed(componentName: String, newState: Element?, storageData: StorageData, newLiveStates: MutableMap<String, Element>): StorageData? {
      val oldState = storageData.myStates.get(componentName)
      if (newState == null || JDOMUtil.isEmpty(newState)) {
        if (oldState == null) {
          return null
        }

        val newStorageData = storageData.clone()
        newStorageData.myStates.remove(componentName)
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
      newStorageData.myStates.put(componentName, if (newBytes == null) newState else newBytes)
      return newStorageData
    }

    private fun prepareElement(state: Element) {
      if (state.getParent() != null) {
        LOG.warn("State element must not have parent " + JDOMUtil.writeElement(state))
        state.detach()
      }
      state.setName(StorageDataBase.COMPONENT)
    }
  }
}

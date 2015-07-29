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

import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.PathMacroSubstitutor
import com.intellij.openapi.components.impl.stores.StateStorageManager
import com.intellij.openapi.components.impl.stores.StorageData
import org.jdom.Element
import kotlin.properties.Delegates

abstract class BaseFileConfigurableStoreImpl(protected val pathMacroManager: PathMacroManager) : ComponentStoreImpl() {
  val storageManager by Delegates.lazy { createStorageManager() }

  override fun getStateStorageManager() = storageManager

  override fun getPathMacroManagerForDefaults() = pathMacroManager

  protected abstract fun createStorageManager(): StateStorageManager
}

open class ProjectStorageData : StorageData {
  companion object {
    val CURRENT_FORMAT_VERSION = 4
    val VERSION_OPTION: String = "version"
  }

  private var version = CURRENT_FORMAT_VERSION

  constructor(rootElementName: String) : super(rootElementName) {
  }

  protected constructor(storageData: ProjectStorageData) : super(storageData) {
  }

  override fun load(rootElement: Element, pathMacroSubstitutor: PathMacroSubstitutor?, intern: Boolean) {
    super.load(rootElement, pathMacroSubstitutor, intern)

    version = rootElement.getAttributeValue(VERSION_OPTION)?.toInt() ?: CURRENT_FORMAT_VERSION
  }

  override fun save(newLiveStates: Map<String, Element>): Element {
    var root = super.save(newLiveStates)
    if (root == null) {
      root = Element(myRootElementName)
    }
    writeOptions(root, Integer.toString(version))
    return root
  }

  override fun clone() = ProjectStorageData(this)

  protected open fun writeOptions(root: Element, versionString: String) {
    root.setAttribute(VERSION_OPTION, "4")
  }

  override fun getChangedComponentNames(newStorageData: StorageData, substitutor: PathMacroSubstitutor?): Set<String>? {
    if (version != (newStorageData as ProjectStorageData).version) {
      return null
    }
    return super.getChangedComponentNames(newStorageData, substitutor)
  }
}

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
import com.intellij.openapi.project.impl.ProjectManagerImpl
import org.jdom.Element

val VERSION_OPTION: String = "version"

abstract class BaseFileConfigurableStoreImpl(protected val myPathMacroManager: PathMacroManager) : ComponentStoreImpl() {
  private var myStateStorageManager: StateStorageManager? = null

  public open class BaseStorageData : StorageData {
    private var myVersion = ProjectManagerImpl.CURRENT_FORMAT_VERSION

    public constructor(rootElementName: String) : super(rootElementName) {
    }

    protected constructor(storageData: BaseStorageData) : super(storageData) {
    }

    override fun load(rootElement: Element, pathMacroSubstitutor: PathMacroSubstitutor?, intern: Boolean) {
      super.load(rootElement, pathMacroSubstitutor, intern)

      val v = rootElement.getAttributeValue(VERSION_OPTION)
      myVersion = if (v == null) ProjectManagerImpl.CURRENT_FORMAT_VERSION else Integer.parseInt(v)
    }

    override fun save(newLiveStates: Map<String, Element>): Element {
      var root = super.save(newLiveStates)
      if (root == null) {
        root = Element(myRootElementName)
      }
      writeOptions(root, Integer.toString(myVersion))
      return root
    }

    protected open fun writeOptions(root: Element, versionString: String) {
      root.setAttribute(VERSION_OPTION, versionString)
    }

    override fun clone(): StorageData {
      return BaseStorageData(this)
    }

    override fun getChangedComponentNames(newStorageData: StorageData, substitutor: PathMacroSubstitutor?): Set<String>? {
      val data = newStorageData as BaseStorageData
      if (myVersion != data.myVersion) {
        return null
      }
      return super.getChangedComponentNames(newStorageData, substitutor)
    }
  }

  override fun getPathMacroManagerForDefaults(): PathMacroManager {
    return myPathMacroManager
  }

  override fun getStateStorageManager(): StateStorageManager {
    if (myStateStorageManager == null) {
      myStateStorageManager = createStateStorageManager()
    }
    return myStateStorageManager!!
  }

  protected abstract fun createStateStorageManager(): StateStorageManager
}

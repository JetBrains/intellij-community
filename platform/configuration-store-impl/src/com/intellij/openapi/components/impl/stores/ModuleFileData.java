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
import com.intellij.openapi.components.impl.stores.StorageData
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.OptionManager
import com.intellij.openapi.util.text.StringUtil
import org.jdom.Element
import java.util.TreeMap

class ModuleFileData : BaseFileConfigurableStoreImpl.BaseStorageData, OptionManager {
  private var options: TreeMap<String, String>? = null
  private val module: Module

  private var dirty = true

  override fun isDirty() = dirty

  public constructor(rootElementName: String, module: Module) : super(rootElementName) {
    this.module = module
    options = TreeMap<String, String>()
  }

  private constructor(storageData: ModuleFileData) : super(storageData) {
    module = storageData.module
    dirty = storageData.dirty
    options = TreeMap(storageData.options)
  }

  override fun load(rootElement: Element, pathMacroSubstitutor: PathMacroSubstitutor?, intern: Boolean) {
    super<BaseFileConfigurableStoreImpl.BaseStorageData>.load(rootElement, pathMacroSubstitutor, intern)

    for (attribute in rootElement.getAttributes()) {
      val name = attribute.getName()
      if (name != VERSION_OPTION && !StringUtil.isEmpty(name)) {
        options!!.put(name, attribute.getValue())
      }
    }

    dirty = false
  }

  override fun writeOptions(root: Element, versionString: String) {
    if (!options!!.isEmpty()) {
      for (key in options!!.keySet()) {
        val value = options!!.get(key)
        if (value != null) {
          root.setAttribute(key, value)
        }
      }
    }
    // need be last for compat reasons
    super<BaseFileConfigurableStoreImpl.BaseStorageData>.writeOptions(root, versionString)

    dirty = false
  }

  override fun clone(): StorageData {
    return ModuleFileData(this)
  }

  override fun getChangedComponentNames(newStorageData: StorageData, substitutor: PathMacroSubstitutor?): Set<String>? {
    val data = newStorageData as ModuleFileData
    if (options != data.options) {
      return null
    }

    return super<BaseFileConfigurableStoreImpl.BaseStorageData>.getChangedComponentNames(newStorageData, substitutor)
  }

  override fun setOption(key: String, value: String) {
    if (value != options!!.put(key, value)) {
      dirty = true
    }
  }

  override fun clearOption(key: String) {
    if (options!!.remove(key) != null) {
      dirty = true
    }
  }

  override fun getOptionValue(key: String): String? {
    return options!!.get(key)
  }
}

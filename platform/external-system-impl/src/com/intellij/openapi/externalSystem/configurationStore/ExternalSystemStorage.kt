/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.configurationStore

import com.intellij.configurationStore.*
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.io.ByteSequence
import org.jdom.Element

internal class ExternalProjectStorage(private val module: Module, storageManager: StateStorageManager) : XmlElementStorage(StoragePathMacros.MODULE_FILE, null, storageManager.macroSubstitutor, RoamingType.DISABLED) {
  private val manager = StreamProviderFactory.EP_NAME.getExtensions(module.project).first { it is ExternalSystemStreamProviderFactory } as ExternalSystemStreamProviderFactory

  override fun loadLocalData() = manager.readModuleData(module.name)

  override fun createSaveSession(states: StateMap) = object : XmlElementStorageSaveSession<ExternalProjectStorage>(states, this) {
    override fun saveLocally(element: Element?) {
      // our customizeStorageSpecs on write will not return our storage for not applicable module, so, we don't need to check it here
      if (element == null) {
        manager.moduleStorage.remove(module.name)
      }
      else {
        val byteOut = BufferExposingByteArrayOutputStream()
        serializeElementToBinary(element, byteOut)
        manager.moduleStorage.put(module.name, ByteSequence(byteOut.internalBuffer, 0, byteOut.size()))
      }
    }
  }
}
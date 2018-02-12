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
package com.intellij.openapi.externalSystem

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.util.text.StringUtil
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ExternalProjectSystemRegistryImpl : ExternalProjectSystemRegistry {
  private val idToSource = ConcurrentHashMap<String, ProjectModelExternalSource>()

  override fun getExternalSource(module: Module): ProjectModelExternalSource? {
    val modulePropertyManager = ExternalSystemModulePropertyManager.getInstance(module)
    val externalSystemId = modulePropertyManager.getExternalSystemId()
    if (externalSystemId != null) {
      return getSourceById(externalSystemId)
    }

    if (modulePropertyManager.isMavenized()) {
      return getSourceById(ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID)
    }

    return null
  }

  override fun getSourceById(id: String): ProjectModelExternalSource = idToSource.computeIfAbsent(id, { ProjectModelExternalSourceImpl(it) })

  private class ProjectModelExternalSourceImpl(private val myId: String) : ProjectModelExternalSource {
    private val displayName = StringUtil.capitalize(myId.toLowerCase(Locale.US))

    init {
      //todo[nik] specify display name explicitly instead, the current code is copied from ProjectSystemId constructor
    }

    override fun getDisplayName() = displayName

    override fun getId() = myId
  }
}
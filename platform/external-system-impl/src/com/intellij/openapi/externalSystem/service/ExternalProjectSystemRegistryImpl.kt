// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.serialization.SerializationConstants
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
class ExternalProjectSystemRegistryImpl : ExternalProjectSystemRegistry {
  private val idToSource = ConcurrentHashMap<String, ProjectModelExternalSource>()

  override fun getExternalSource(module: Module): ProjectModelExternalSource? {
    val modulePropertyManager = ExternalSystemModulePropertyManager.getInstance(module)
    val externalSystemId = modulePropertyManager.getExternalSystemId()
    if (externalSystemId != null) {
      return getSourceById(externalSystemId)
    }

    if (modulePropertyManager.isMavenized()) {
      return getSourceById(SerializationConstants.MAVEN_EXTERNAL_SOURCE_ID)
    }

    return null
  }

  override fun getSourceById(id: String): ProjectModelExternalSource = idToSource.computeIfAbsent(id) { ProjectModelExternalSourceImpl(it) }

  private class ProjectModelExternalSourceImpl(private val myId: String) : ProjectModelExternalSource {
    //todo specify display name explicitly instead, the current code is copied from ProjectSystemId constructor
    private val displayName = StringUtil.capitalize(myId.lowercase(Locale.US))

    override fun getDisplayName() = displayName

    override fun getId() = myId
  }
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis

import com.intellij.java.workspace.entities.javaSettings
import com.intellij.openapi.module.Module
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleId

internal class VirtualManifestProviderImpl : VirtualManifestProvider {
  override fun getValue(module: Module, attribute: String): String? {
    val project = module.project
    val storage = WorkspaceModel.getInstance(project).currentSnapshot
    val moduleName = module.name
    return storage.resolve(ModuleId(moduleName))?.javaSettings?.manifestAttributes?.get(attribute)
  }
}
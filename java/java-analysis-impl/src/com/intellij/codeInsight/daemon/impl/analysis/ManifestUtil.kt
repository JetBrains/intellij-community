// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ManifestUtil")
package com.intellij.codeInsight.daemon.impl.analysis

import com.intellij.java.workspace.entities.javaSettings
import com.intellij.openapi.module.Module
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleId
import org.jetbrains.annotations.Contract

/**
 * Retrieve attribute value from [module] stored through build scripts.
 */
@Contract(pure = true)
fun lightManifestAttributeValue(module: Module, attribute: String): String? {
  return WorkspaceModel.getInstance(module.project)
    .currentSnapshot.resolve(ModuleId(module.name))
    ?.javaSettings
    ?.manifestAttributes
    ?.get(attribute)
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import com.jetbrains.jsonSchema.impl.JsonValidationError
import com.jetbrains.jsonSchema.impl.NetworkntValidationBridge

/**
 * Delegates to [NetworkntValidationService] from the `intellij.json.networknt.wrapper` module.
 *
 * The bridge interface lives in `intellij.json.backend` to avoid a circular dependency.
 */
@Service(Service.Level.PROJECT)
class NetworkntValidationBridgeImpl(private val project: Project) : NetworkntValidationBridge {

  override fun validate(
    schemaFile: VirtualFile,
    walker: JsonLikePsiWalker,
    rootElement: PsiElement,
    schemaVersion: JsonSchemaVersion,
  ): Map<PsiElement, JsonValidationError> {
    val validationService = NetworkntValidationService.getInstance(project)
    return validationService.validateForAnnotation(schemaFile, walker, rootElement, schemaVersion)
  }
}

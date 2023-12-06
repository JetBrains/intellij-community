// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.documentation.impl

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation

internal object EmptyDocumentationTarget : DocumentationTarget, Pointer<EmptyDocumentationTarget> {

  override fun createPointer(): Pointer<out EmptyDocumentationTarget> = this

  override fun dereference(): EmptyDocumentationTarget = this

  override fun computePresentation(): TargetPresentation = TargetPresentation.builder("").presentation()

  override fun computeDocumentation(): DocumentationResult? = null

  val request: DocumentationRequest = DocumentationRequest(this, computePresentation())
}

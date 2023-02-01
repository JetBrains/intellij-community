// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.impl

import com.intellij.model.Pointer
import com.intellij.navigation.TargetPresentation
import com.intellij.platform.documentation.DocumentationResult
import com.intellij.platform.documentation.DocumentationTarget

internal object EmptyDocumentationTarget : DocumentationTarget, Pointer<EmptyDocumentationTarget> {

  override fun createPointer(): Pointer<out EmptyDocumentationTarget> = this

  override fun dereference(): EmptyDocumentationTarget = this

  override fun presentation(): TargetPresentation = TargetPresentation.builder("").presentation()

  override fun computeDocumentation(): DocumentationResult? = null

  val request: DocumentationRequest = DocumentationRequest(this, presentation())
}

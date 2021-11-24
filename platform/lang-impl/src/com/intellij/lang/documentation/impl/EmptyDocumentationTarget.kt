// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.impl

import com.intellij.lang.documentation.DocumentationResult
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.model.Pointer
import com.intellij.navigation.TargetPresentation

internal object EmptyDocumentationTarget : DocumentationTarget, Pointer<EmptyDocumentationTarget> {

  override fun createPointer(): Pointer<out EmptyDocumentationTarget> = this

  override fun dereference(): EmptyDocumentationTarget = this

  override val presentation: TargetPresentation = TargetPresentation.builder("").presentation()

  override fun computeDocumentation(): DocumentationResult? = null

  val request: DocumentationRequest = DocumentationRequest(this, presentation)
}

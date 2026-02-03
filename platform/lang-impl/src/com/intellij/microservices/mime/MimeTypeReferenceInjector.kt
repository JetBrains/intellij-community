// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.mime

import com.intellij.icons.AllIcons
import com.intellij.microservices.MicroservicesBundle
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.injection.ReferenceInjector
import com.intellij.util.ProcessingContext
import javax.swing.Icon

internal class MimeTypeReferenceInjector : ReferenceInjector() {
  override fun getId(): String = "mime-type-reference"

  override fun getIcon(): Icon = AllIcons.Nodes.PpWeb

  override fun getDisplayName(): String = MicroservicesBundle.message("inject.mime.type.reference")

  override fun getReferences(element: PsiElement, context: ProcessingContext, range: TextRange): Array<PsiReference> {
    return MimeTypeReference.forElement(element, range, true)
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.http

import com.intellij.icons.AllIcons
import com.intellij.microservices.MicroservicesBundle
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.injection.ReferenceInjector
import com.intellij.util.ProcessingContext
import javax.swing.Icon

internal class HttpHeaderReferenceInjector : ReferenceInjector() {
  override fun getId(): String = "http-header-reference"

  override fun getIcon(): Icon = AllIcons.Nodes.PpWeb

  override fun getDisplayName(): String = MicroservicesBundle.message("inject.http.header.reference")

  override fun getReferences(element: PsiElement, context: ProcessingContext, range: TextRange): Array<PsiReference> {
    return HttpHeaderReference.forElement(element, range)
  }
}
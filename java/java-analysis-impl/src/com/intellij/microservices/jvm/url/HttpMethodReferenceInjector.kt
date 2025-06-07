// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.jvm.url

import com.intellij.icons.AllIcons
import com.intellij.microservices.http.HttpMethodReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.injection.ReferenceInjector
import com.intellij.util.ProcessingContext
import javax.swing.Icon

internal class HttpMethodReferenceInjector : ReferenceInjector() {
  override fun getId(): String = "http-method-reference"

  override fun getDisplayName(): String = MicroservicesJvmUrlBundle.message("inject.http.method.reference")

  override fun getIcon(): Icon = AllIcons.Nodes.PpWeb

  override fun getReferences(element: PsiElement, context: ProcessingContext, range: TextRange): Array<PsiReference> {
    return arrayOf(HttpMethodReference(element, ElementManipulators.getValueTextRange(element)))
  }
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.jvm.url

import com.intellij.icons.AllIcons
import com.intellij.microservices.url.HTTP_SCHEMES
import com.intellij.microservices.url.WS_SCHEMES
import com.intellij.microservices.url.references.UrlPathReferenceInjector
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.injection.ReferenceInjector
import com.intellij.psi.util.PartiallyKnownString
import com.intellij.util.ProcessingContext
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.expressions.UStringConcatenationsFacade
import org.jetbrains.uast.toUElementOfType
import javax.swing.Icon

internal abstract class UrlPathReferenceContextInjector : ReferenceInjector() {

  abstract val schemes: List<String>

  protected val injector by lazy {
    UrlPathReferenceInjector.forPartialStringFrom<PsiElement> { host ->
      host.toUElementOfType<UExpression>()?.let { uExpression ->
        return@forPartialStringFrom buildUastPartialString(uExpression)
      }
      PartiallyKnownString(ElementManipulators.getValueText(host), host, ElementManipulators.getValueTextRange(host))
    }.withSchemesSupport(schemes)
  }

  private fun buildUastPartialString(uExpression: UExpression): PartiallyKnownString? {
    val uContext = getContextExpression(uExpression) ?: uExpression
    return UStringConcatenationsFacade.createFromUExpression(uContext)?.asPartiallyKnownString()
  }

  override fun getReferences(host: PsiElement, context: ProcessingContext, range: TextRange): Array<PsiReference> =
    injector.buildAbsoluteOrRelativeReferences(host, host)
}

internal class HttpUrlReferenceInjector : UrlPathReferenceContextInjector() {
  override fun getId(): String = "http-url-reference"

  override fun getDisplayName(): String = MicroservicesJvmUrlBundle.message("inject.http.url.reference")

  override fun getIcon(): Icon = AllIcons.Nodes.PpWeb

  override val schemes: List<String> get() = HTTP_SCHEMES
}

internal class WSUrlReferenceInjector : UrlPathReferenceContextInjector() {
  override fun getId(): String = "ws-reference"

  override fun getDisplayName(): String = MicroservicesJvmUrlBundle.message("inject.http.ws.reference")

  override val schemes: List<String> get() = WS_SCHEMES

  override fun getIcon(): Icon = AllIcons.Webreferences.WebSocket
}
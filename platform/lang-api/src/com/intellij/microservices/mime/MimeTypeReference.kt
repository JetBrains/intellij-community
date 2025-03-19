package com.intellij.microservices.mime

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.microservices.HttpReferenceService
import com.intellij.microservices.MicroservicesBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.util.ArrayUtil

class MimeTypeReference @JvmOverloads constructor(element: PsiElement, range: TextRange, val isInjected: Boolean = false)
  : PsiReferenceBase<PsiElement>(element, range), EmptyResolveMessageProvider {

  override fun resolve(): PsiElement? {
    val value = value
    if (!MimeTypes.MIME_PATTERN.matcher(value).matches()) return null

    return service<HttpReferenceService>().resolveMimeReference(element, value)
  }

  override fun getUnresolvedMessagePattern(): String {
    return MicroservicesBundle.message("mime.type.element.error")
  }

  override fun isReferenceTo(element: PsiElement): Boolean {
    return service<HttpReferenceService>().isReferenceToMimeElement(element, value)
  }

  override fun getVariants(): Array<Any> {
    return ArrayUtil.toObjectArray(MimeTypes.PREDEFINED_MIME_VARIANTS.map {
      LookupElementBuilder.create(it).withIcon(AllIcons.Nodes.Type)
    })
  }

  companion object {
    @JvmStatic
    fun forElement(injectionHost: PsiElement): Array<PsiReference> {
      return forElement(injectionHost, ElementManipulators.getValueTextRange(injectionHost))
    }

    @JvmStatic
    @JvmOverloads
    fun forElement(injectionHost: PsiElement, range: TextRange, isInjected: Boolean = false): Array<PsiReference> {
      val valueText = ElementManipulators.getValueText(injectionHost)
      val charSequences = StringUtil.split(valueText, ";")
      if (charSequences.size > 0) {
        val mimeName = charSequences[0]
        val subRange = TextRange(range.startOffset,
                                 range.startOffset + mimeName.length)
        return arrayOf(MimeTypeReference(injectionHost, subRange, isInjected))
      }
      // show unresolved reference in case of empty string
      return arrayOf(MimeTypeReference(injectionHost, range, isInjected))
    }
  }
}
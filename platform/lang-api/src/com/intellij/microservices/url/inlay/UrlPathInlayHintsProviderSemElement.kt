package com.intellij.microservices.url.inlay

import com.intellij.codeInsight.hints.InlayPresentationFactory
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.microservices.url.references.UrlPathContext
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.semantic.SemElement
import com.intellij.semantic.SemKey

interface UrlPathInlayHintsProviderSemElement : SemElement {
  val inlayHints: List<UrlPathInlayHint>

  val groupInfo: ProviderGroupInfo
    get() = DEFAULT_PROVIDER_GROUP_INFO

  companion object {
    @JvmField
    val INLAY_HINT_SEM_KEY: SemKey<UrlPathInlayHintsProviderSemElement> =
      SemKey.createKey("UrlInlayHintSemProvider")
  }
}

interface UrlPathInlayHint {
  val offset: Int
  val priority: Int
  val style: Style

  fun getAvailableActions(file: PsiFile): List<UrlPathInlayAction> {
    return service<UrlPathInlayActionService>().getAvailableActions(file, this)
  }

  fun getPresentation(editor: Editor, factory: InlayPresentationFactory): InlayPresentation

  val context: UrlPathContext

  val attachedTo: SmartPsiElementPointer<PsiElement>?
    get() = null

  enum class Style { BLOCK, INLINE }
}

data class ProviderGroupInfo(val key: ProviderGroupKey, val priority: Int)

data class ProviderGroupKey(private val id: String) {
  companion object {
    val DEFAULT_KEY: ProviderGroupKey = ProviderGroupKey("default.group.key")
  }
}

val DEFAULT_PROVIDER_GROUP_INFO: ProviderGroupInfo = ProviderGroupInfo(ProviderGroupKey.DEFAULT_KEY, -1)

class PsiElementUrlPathInlayHint(psiElement: PsiElement, override val context: UrlPathContext) : UrlPathInlayHint {
  override val offset: Int = psiElement.textRange.startOffset
  override val priority: Int = 0
  override val style: UrlPathInlayHint.Style = UrlPathInlayHint.Style.INLINE

  override val attachedTo: SmartPsiElementPointer<PsiElement> = SmartPointerManager.createPointer(psiElement)

  override fun getPresentation(editor: Editor, factory: InlayPresentationFactory): InlayPresentation {
    return service<UrlPathInlayActionService>().buildPresentation(editor, factory)
  }
}
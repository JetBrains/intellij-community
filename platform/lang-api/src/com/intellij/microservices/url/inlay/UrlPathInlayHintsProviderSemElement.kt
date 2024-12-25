package com.intellij.microservices.url.inlay

import com.intellij.codeInsight.hints.InlayPresentationFactory
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.ScaleAwarePresentationFactory
import com.intellij.icons.AllIcons
import com.intellij.microservices.url.references.UrlPathContext
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
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

  fun getAvailableActions(file: PsiFile): List<UrlPathInlayAction> = UrlPathInlayHintsProvider.EP_NAME.extensions.filter {
    it.isAvailable(file, this)
  }

  fun getPresentation(editor: Editor, factory: PresentationFactory): InlayPresentation

  val context: UrlPathContext

  val attachedTo: SmartPsiElementPointer<PsiElement>?
    get() = null

  enum class Style { BLOCK, INLINE }
}

data class ProviderGroupInfo(val key: ProviderGroupKey, val priority: Int)

private val priorityComparator: Comparator<UrlPathInlayHintsProviderSemElement> = Comparator.comparingInt { it.groupInfo.priority }

data class ProviderGroupKey(private val id: String) {
  companion object {
    internal val DEFAULT_KEY: ProviderGroupKey = ProviderGroupKey("default.group.key")
  }
}

val DEFAULT_PROVIDER_GROUP_INFO: ProviderGroupInfo = ProviderGroupInfo(ProviderGroupKey.DEFAULT_KEY, -1)

internal fun Sequence<UrlPathInlayHintsProviderSemElement>.selectProvidersFromGroups(): Sequence<UrlPathInlayHintsProviderSemElement> {
  return groupingBy { it.groupInfo.key }
    .aggregate { key, accumulator: MutableList<UrlPathInlayHintsProviderSemElement>?, element, _ ->
      if (key == ProviderGroupKey.DEFAULT_KEY) {
        accumulator?.let { it.apply { add(element) } } ?: mutableListOf(element)
      }
      else {
        mutableListOf(accumulator?.singleOrNull()?.let { maxOf(it, element, priorityComparator) } ?: element)
      }
    }.asSequence()
    //FIXME: KT-41117
    .flatMap { it.value!!.asSequence() }
}

fun ScaleAwarePresentationFactory.urlInlayPresentation(): InlayPresentation =
  lineCentered(
    container(
      container(
        seq(
          icon(AllIcons.Actions.InlayGlobe, debugName = "globe_image", fontShift = 1),
          inset(
            icon(AllIcons.Actions.InlayDropTriangle, debugName = "popup_image", fontShift = 1),
            top = 4
          )
        ),
        background = editor.colorsScheme.getColor(DefaultLanguageHighlighterColors.INLINE_REFACTORING_SETTINGS_DEFAULT),
        roundedCorners = InlayPresentationFactory.RoundedCorners(6, 6)
      )
    )
  )

class PsiElementUrlPathInlayHint(psiElement: PsiElement, override val context: UrlPathContext) : UrlPathInlayHint {
  override val offset: Int = psiElement.textRange.startOffset
  override val priority: Int = 0
  override val style: UrlPathInlayHint.Style = UrlPathInlayHint.Style.INLINE

  override val attachedTo: SmartPsiElementPointer<PsiElement> = SmartPointerManager.createPointer(psiElement)

  override fun getPresentation(editor: Editor, factory: PresentationFactory): InlayPresentation =
    with(ScaleAwarePresentationFactory(editor, factory)) {
      urlInlayPresentation()
    }
}
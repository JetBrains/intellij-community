package com.intellij.microservices.url.inlay

import com.intellij.codeInsight.hints.InlayPresentationFactory
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.ScaleAwarePresentationFactory
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors

private val priorityComparator: Comparator<UrlPathInlayHintsProviderSemElement> = Comparator.comparingInt { it.groupInfo.priority }

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
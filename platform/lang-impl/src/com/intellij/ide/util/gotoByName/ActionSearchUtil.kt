// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.psi.codeStyle.WordPrefixMatcher
import com.intellij.util.DefaultBundleService
import com.intellij.util.text.Matcher
import org.jetbrains.annotations.Nls
import kotlin.math.max

private const val BONUS_FOR_SPACE_IN_PATTERN = 100
private const val SETTINGS_PENALTY = 100


fun getActionText(value: Any?): @Nls String? {
  if (value is OptionDescription) return value.hit
  if (value is AnAction) return getAnActionText(value)
  if (value is GotoActionModel.ActionWrapper) return getAnActionText(value.action)
  return null
}

fun getAnActionText(value: AnAction): @Nls String? {
  val presentation = value.templatePresentation.clone()
  value.applyTextOverride(ActionPlaces.ACTION_SEARCH, presentation)
  return presentation.text
}

fun buildMatcher(pattern: String): Matcher {
  return if (pattern.contains(" ")) WordPrefixMatcher(pattern)
  else NameUtil.buildMatcher("*$pattern", NameUtil.MatchingCaseSensitivity.NONE)
}

fun calcElementWeight(element: Any, pattern: String, matcher: MinusculeMatcher): Int? {
  var degree = calculateDegree(matcher, getActionText(element))
  if (degree == null) return null

  if (degree == 0) {
    degree = calculateDegree(matcher, DefaultBundleService.getInstance().compute {
      getAnActionOriginalText(getAction(element))
    })
    if (degree == null) return null
  }

  if (pattern.trim { it <= ' ' }.contains(" ")) degree += BONUS_FOR_SPACE_IN_PATTERN
  if (element is OptionDescription && degree > 0) degree -= SETTINGS_PENALTY

  return max(degree, 0)
}

fun calculateDegree(matcher: MinusculeMatcher, text: String?): Int? {
  if (text == null) return null
  return matcher.matchingDegree(text)
}

private fun getAnActionOriginalText(value: AnAction?): String? {
  if (value == null) return null
  val presentation = value.templatePresentation.clone()
  value.applyTextOverride(ActionPlaces.ACTION_SEARCH, presentation)
  val mnemonic = presentation.textWithPossibleMnemonic.get()
  if (mnemonic == null) return null

  return mnemonic.text
}

private fun getAction(value: Any): AnAction? = when (value) {
  is AnAction -> value
  is GotoActionModel.ActionWrapper -> value.action
  else -> null
}
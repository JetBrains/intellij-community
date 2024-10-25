// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.ide.actions.ApplyIntentionAction
import com.intellij.ide.ui.RegistryTextOptionDescriptor
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.util.gotoByName.GotoActionModel.ActionWrapper
import com.intellij.ide.util.gotoByName.GotoActionModel.GotoActionListCellRenderer.calcHit
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.impl.ActionPresentationDecorator
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.psi.codeStyle.WordPrefixMatcher
import com.intellij.searchEverywhere.ActionItemPresentation
import com.intellij.searchEverywhere.OptionItemPresentation
import com.intellij.searchEverywhere.SearchEverywhereItemPresentation
import com.intellij.util.DefaultBundleService
import com.intellij.util.text.Matcher
import com.intellij.util.text.nullize
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NotNull
import kotlin.math.max

private const val BONUS_FOR_SPACE_IN_PATTERN = 100
private const val SETTINGS_PENALTY = 100


object ActionPresentationProvider: (GotoActionModel.MatchedValue) -> SearchEverywhereItemPresentation {
  override fun invoke(matchedValue: GotoActionModel.MatchedValue): SearchEverywhereItemPresentation {
    val showIcon = UISettings.getInstance().showIconsInMenus
    val value = matchedValue.value
    if (value is ActionWrapper) {
      var presentation = ActionItemPresentation(name = "")

      val anAction = value.action
      val actionPresentation = value.presentation

      val toggle = anAction is ToggleAction
      if (toggle) {
        presentation = presentation.run { copy(switcherState = Toggleable.isSelected(actionPresentation)) }
      }

      val groupName = if (anAction is ApplyIntentionAction) null else value.getGroupName()
      if (groupName != null) {
        presentation = presentation.run { copy(location = groupName) }
      }

      if (showIcon) {
        presentation = presentation.run { copy(icon = actionPresentation.icon) }
        //if (isSelected && presentation.getSelectedIcon() != null) {
        //  icon = presentation.getSelectedIcon();
        //}
      }

      //if (anAction instanceof PromoAction promoAction) {
      //  customizePromoAction(promoAction, bg, eastBorder, groupFg, panel);
      //}

      @NlsSafe val actionId = ActionManager.getInstance().getId(anAction)
      val shortcuts = KeymapUtil.getActiveKeymapShortcuts(actionId).getShortcuts()
      val shortcutText = KeymapUtil.getPreferredShortcutText(shortcuts).nullize(true)?.let {
        presentation = presentation.run { copy(shortcut = it) }
      }

      //val text = ActionPresentationDecorator.decorateTextIfNeeded(anAction, actionPresentation.text)
      val text = actionPresentation.text
      presentation = presentation.run { copy(name = text) }

      return presentation
    }
    else if (value is OptionDescription) {
      val hit = calcHit(value)
      var presentation = OptionItemPresentation(name = hit)

      (value as? BooleanOptionDescription)?.isOptionEnabled.let {
        presentation = presentation.run { copy(switcherState = it) }
      }

      presentation = presentation.run { copy(location = getGroupName(value)) }
      return presentation
    }

    return ActionItemPresentation(name = "Unknown item")
  }
}

fun getGroupName(@NotNull description: OptionDescription): @Nls @NotNull String {
  if (description is RegistryTextOptionDescriptor) return LangBundle.message("group.registry")
  val groupName: String? = description.groupName
  val settings: String = LangBundle.message("group.settings")
  return if (groupName == null || groupName == description.hit) settings else "$settings > $groupName"
}

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
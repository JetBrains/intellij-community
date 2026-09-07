// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.jvm.actions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager

/**
 * Shows the same action for several target classes as one action with a target chooser.
 *
 * There are two kinds of groupable action. A [JvmGroupModCommandAction] is grouped into a
 * [ChooseTargetClassAction], and an old-style [JvmGroupIntentionAction] into a
 * [JvmClassIntentionActionGroup]. The two kinds do not mix, so an action group which holds both
 * kinds produces two entries.
 */
public fun List<IntentionAction>.groupActionsByType(language: Language): List<IntentionAction> {
  if (ApplicationManager.getApplication().isUnitTestMode) {
    return this
  }
  val result = ArrayList<IntentionAction>()
  val modCommandActions = LinkedHashMap<JvmActionGroup, MutableList<Pair<IntentionAction, JvmGroupModCommandAction>>>()
  val intentionActions = LinkedHashMap<JvmActionGroup, MutableList<JvmGroupIntentionAction>>()

  for (action in this) {
    val modCommandAction = action.asModCommandAction()
    if (modCommandAction is JvmGroupModCommandAction) {
      modCommandActions.getOrPut(modCommandAction.getActionGroup()) { ArrayList() } += action to modCommandAction
    }
    else if (action is JvmGroupIntentionAction) {
      intentionActions.getOrPut(action.actionGroup) { ArrayList() } += action
    }
    else {
      result += action
    }
  }

  for ((actionGroup, actions) in modCommandActions) {
    result += if (actions.size == 1) {
      actions[0].first
    }
    else {
      ChooseTargetClassAction(actions.map { it.second }, actionGroup, language).asIntention()
    }
  }
  for ((actionGroup, actions) in intentionActions) {
    result += if (actions.size == 1) {
      actions[0]
    }
    else {
      JvmClassIntentionActionGroup(actions, actionGroup, language)
    }
  }
  return result
}

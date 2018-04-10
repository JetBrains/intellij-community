// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.java.JavaLanguage

internal fun List<IntentionAction>.groupActionsByType(): List<IntentionAction> {
  val groupableActions = filterIsInstance<JvmGroupIntentionAction>()
  val result = minus(groupableActions).toMutableList()
  val typeActions = groupableActions.groupBy { it.actionGroup }
  for ((type, actions) in typeActions) {
    result += if (actions.size == 1) {
      actions[0]
    }
    else {
      JvmClassIntentionActionGroup(actions, type, JavaLanguage.INSTANCE)
    }
  }
  return result
}

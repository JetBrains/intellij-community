// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.jvm.actions

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.lang.Language
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.Presentation

/**
 * One intention which stands for the same action on several target classes.
 * It asks the user for the target class, and then performs the action of that class.
 *
 * This is the [ModCommandAction] counterpart of [JvmClassIntentionActionGroup].
 */
internal class ChooseTargetClassAction(
  private val actions: List<JvmGroupModCommandAction>,
  private val actionGroup: JvmActionGroup,
  private val callSiteLanguage: Language,
) : ModCommandAction {

  override fun getFamilyName(): String = message("create.member.from.usage.family")

  override fun getPresentation(context: ActionContext): Presentation? {
    val available = available(context)
    if (available.isEmpty()) return null
    return Presentation.of(getGroupText(available))
  }

  override fun perform(context: ActionContext): ModCommand {
    val available = available(context)
    return when (available.size) {
      0 -> ModCommand.nop()
      1 -> available.single().perform(context)
      else -> ModCommand.chooseAction(message("target.class.chooser.title"), available)
    }
  }

  private fun available(context: ActionContext): List<JvmGroupModCommandAction> =
    actions.filter { it.getPresentation(context) != null }

  private fun getGroupText(actions: List<JvmGroupModCommandAction>): String {
    actions.mapTo(HashSet()) { it.getGroupDisplayText() }.singleOrNull()?.let {
      // All actions have the same group text, so use it.
      return it
    }

    actions.find { it.getTarget()?.sourceElement?.language == callSiteLanguage }?.let {
      // One action has the target language of the call site, so its group text is in our terms.
      return it.getGroupDisplayText()
    }

    // Every action comes from a foreign language, and the group texts differ.
    // We do not know how to name them, so we fall back to the default text.
    // We pass some data, so the group can use the element name.
    val renderData = actions.asSequence().mapNotNull { it.getRenderData() }.firstOrNull()
    return actionGroup.getDisplayText(renderData)
  }
}

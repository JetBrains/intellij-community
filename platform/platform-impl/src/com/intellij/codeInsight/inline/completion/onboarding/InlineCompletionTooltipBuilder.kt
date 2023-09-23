// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.onboarding

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.GotItComponentBuilder
import com.intellij.ui.GotItTooltip
import com.intellij.util.lateinitVal
import java.awt.Point

internal object InlineCompletionTooltipBuilder {
  fun atFirstShow(editor: EditorImpl, curCaret: Caret) {
    GotItTooltip("inline.completion.onboarding.show", GotItComponentBuilder {
      buildString {
        appendLine("It extends basic completion suggests.")
        appendLine("Accept it by pressing Tab ")

        KeymapUtil.getFirstKeyboardShortcutText("InsertInlineCompletionAction").also {
          append("${StringUtil.NON_BREAK_SPACE}<span class=\"shortcut\">${it}</span>${StringUtil.NON_BREAK_SPACE}")
        }
      }
    }.apply {
      showButton(false)
    })
      .withPosition(Balloon.Position.above)
      .withHeader("Grey text is ${name()} completion")
      .createAndShow(editor.component) { _, _ ->
        editor.logicalPositionToXY(
          LogicalPosition(curCaret.logicalPosition.line, curCaret.logicalPosition.column + 3, true)
        ).apply {
          translate(editor.gutterComponentEx.width, 0)
        }
      }
  }

  fun atFirstAccept(editor: EditorImpl, curCaret: Caret, dataContext: DataContext?) {
    if (System.getProperty("inline.completion.onboarding.show.ux1")?.toBoolean() == true) {
      atFirstAcceptOne(editor, curCaret, dataContext)
    }
    else {
      atFirstAcceptTwo(editor, curCaret, dataContext)
    }
  }

  private fun atFirstAcceptOne(editor: EditorImpl, curCaret: Caret, dataContext: DataContext?) {
    var tooltip by lateinitVal<Balloon>()

    tooltip = GotItTooltip("inline.completion.onboarding.accept1", GotItComponentBuilder { "" }.apply {
      withButtonLabel("Show Settings")
      onButtonClick {
        val action = ActionManager.getInstance().getAction("ChangeToCustomInlineCompletionAction")
        val actionEvent = AnActionEvent.createFromAnAction(action, null, ActionPlaces.TOOLBAR, dataContext ?: DataContext.EMPTY_CONTEXT)
        action.actionPerformed(actionEvent)
      }
      withSecondaryButton("Finish") {
        tooltip.hide(true)
      }
    })
      .withPosition(Balloon.Position.above)
      .withHeader("Customise ${name()} completion as you like")
      .createAndShow(editor.component) { _, _ -> editor.logicalPositionNearCaret(curCaret) }
  }

  private fun atFirstAcceptTwo(editor: EditorImpl, curCaret: Caret, dataContext: DataContext?) {
    GotItTooltip("inline.completion.onboarding.accept2", GotItComponentBuilder {
      buildString {
        appendLine("What you want to do next?")
        appendLine("Accept it by pressing Tab ")

        KeymapUtil.getFirstKeyboardShortcutText("InsertInlineCompletionAction").also {
          append("${StringUtil.NON_BREAK_SPACE}<span class=\"shortcut\">${it}</span>${StringUtil.NON_BREAK_SPACE}")
        }
      }
    }.apply {
      withButtonLabel("Finish")
      withSecondaryButton("Show Settings") {
        val action = ActionManager.getInstance().getAction("ChangeToCustomInlineCompletionAction")
        val actionEvent = AnActionEvent.createFromAnAction(action, null, ActionPlaces.TOOLBAR, dataContext ?: DataContext.EMPTY_CONTEXT)
        action.actionPerformed(actionEvent)
      }
    })
      .withPosition(Balloon.Position.atRight)
      .withHeader("This is your first\ninline completion \uD83C\uDF89")
      .createAndShow(editor.component) { _, _ -> editor.logicalPositionNearCaret(curCaret) }
  }

  fun showSettings(editor: EditorImpl): Boolean {
    val lookup = LookupManager.getActiveLookup(editor) ?: return false

    GotItTooltip("inline.completion.onboarding.settings", GotItComponentBuilder { "" }.apply {
      showButton(false)
    })
      .withPosition(Balloon.Position.atLeft)
      .withHeader("${name()} Completion Settings are here")
      .createAndShow(editor.component) { _, _ -> Point(lookup.component.width - 24, lookup.component.height - 24) }
    return true
  }

  private fun name(): String {
    return if (PluginManager.isPluginInstalled(PluginId.getId("org.jetbrains.completion.full.line"))) {
      "Full Line"
    }
    else {
      "Inline completion"
    }
  }

  private fun EditorImpl.logicalPositionNearCaret(caret: Caret, columnShift: Int = 0): Point {
    return logicalPositionToXY(
      LogicalPosition(caret.logicalPosition.line, caret.logicalPosition.column + columnShift, true)
    ).apply {
      translate(gutterComponentEx.width, 0)
    }
  }
}

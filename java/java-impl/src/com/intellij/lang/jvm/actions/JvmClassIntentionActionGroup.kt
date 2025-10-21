// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.jvm.actions

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.intention.impl.IntentionActionGroup
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.lang.Language
import com.intellij.lang.jvm.JvmClass
import com.intellij.modcommand.ActionContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiFile
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants

public open class JvmClassIntentionActionGroup(
  actions: List<JvmGroupIntentionAction>,
  private val actionGroup: JvmActionGroup,
  private val callSiteLanguage: Language
) : IntentionActionGroup<JvmGroupIntentionAction>(actions) {

  override fun getFamilyName(): String = message("create.member.from.usage.family")

  override fun getGroupText(actions: List<JvmGroupIntentionAction>): String {
    actions.mapTo(HashSet()) { it.groupDisplayText }.singleOrNull()?.let {
      // All actions have the same group text
      // => use it.
      return it
    }

    actions.find { it.target.sourceElement?.language == callSiteLanguage }?.let {
      // There is an action with the same target language as the call site language
      // => its group text is in our language terms
      // => use its group text.
      return it.groupDisplayText
    }

    // At this point, all actions came from foreign languages, and they have different group texts.
    // We don't know how to name them, so we fall back to default text.
    // We pass some data, so the group can, for example, make use of the element name.
    val renderData = actions.asSequence().mapNotNull { it.renderData }.firstOrNull()
    return actionGroup.getDisplayText(renderData)
  }

  override fun chooseAction(project: Project,
                            editor: Editor,
                            file: PsiFile,
                            actions: List<JvmGroupIntentionAction>,
                            invokeAction: (JvmGroupIntentionAction) -> Unit) {
    createPopup(project, editor, file, actions, invokeAction).showInBestPositionFor(editor)
  }

  private fun createPopup(project: Project, editor: Editor, psiFile: PsiFile, actions: List<JvmGroupIntentionAction>, invokeAction: (JvmGroupIntentionAction) -> Unit): ListPopup {
    if (actions.map { it.target }.toSet().size == actions.size) {
      // show popup grouped by targets, because there's one action for each target
      val targetActions = actions.groupByTo(LinkedHashMap()) { it.target }.mapValues { (_, actions) -> actions.single() }

      val step = object : BaseListPopupStep<JvmClass>(message("target.class.chooser.title"), targetActions.keys.toList()) {
        override fun onChosen(selectedValue: JvmClass, finalChoice: Boolean): PopupStep<*>? {
          invokeAction(targetActions[selectedValue]!!)
          return null
        }
      }

      return JBPopupFactory.getInstance().createListPopup(project, step) {
        // TODO JvmClass renderer
        PsiClassListCellRenderer()
      }
    }

    // show popup with all actions because some of them are for the same target
    val step = object : BaseListPopupStep<JvmGroupIntentionAction>(message("target.class.chooser.title"), actions) {
      override fun onChosen(selectedValue: JvmGroupIntentionAction, finalChoice: Boolean): PopupStep<*>? {
        invokeAction(selectedValue)
        return null
      }
    }

    return JBPopupFactory.getInstance().createListPopup(project, step) {
      ListCellRenderer<JvmGroupIntentionAction>(fun(_: JList<out JvmGroupIntentionAction>, value: JvmGroupIntentionAction, _: Int, _: Boolean, _: Boolean): Component {
        val icon = value.asModCommandAction()?.getPresentation(ActionContext.from(editor, psiFile))?.icon ?: AllIcons.Actions.QuickfixBulb
        return JLabel(value.text, icon, SwingConstants.LEFT)
      })
    }
  }
}

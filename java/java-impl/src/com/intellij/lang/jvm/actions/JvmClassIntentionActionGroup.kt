// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.intention.impl.IntentionActionGroup
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.lang.Language
import com.intellij.lang.jvm.JvmClass
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiFile
import com.intellij.ui.popup.list.ListPopupImpl

open class JvmClassIntentionActionGroup(
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
      // There is an action with same target language as call site language
      // => its group text is in our language terms
      // => use its group text.
      return it.groupDisplayText
    }

    // At this point all actions came from foreign languages and all have different group text.
    // We don't know how to name them, so we fall back to default text.
    // We pass some data, so the group can, for example, make use of element name.
    val renderData = actions.asSequence().mapNotNull { it.renderData }.firstOrNull()
    return actionGroup.getDisplayText(renderData)
  }

  override fun chooseAction(project: Project,
                            editor: Editor,
                            file: PsiFile,
                            actions: List<JvmGroupIntentionAction>,
                            invokeAction: (JvmGroupIntentionAction) -> Unit) {
    createPopup(actions, invokeAction).showInBestPositionFor(editor)
  }

  protected fun createPopup(actions: List<JvmGroupIntentionAction>, invokeAction: (JvmGroupIntentionAction) -> Unit): ListPopup {
    val targetActions = actions.groupByTo(LinkedHashMap()) { it.target }.mapValues { (_, actions) -> actions.single() }

    val step = object : BaseListPopupStep<JvmClass>(message("target.class.chooser.title"), targetActions.keys.toList()) {
      override fun onChosen(selectedValue: JvmClass, finalChoice: Boolean): PopupStep<*>? {
        invokeAction(targetActions[selectedValue]!!)
        return null
      }
    }

    return object : ListPopupImpl(step) {
      // TODO JvmClass renderer
      override fun getListElementRenderer() = PsiClassListCellRenderer()
    }
  }
}

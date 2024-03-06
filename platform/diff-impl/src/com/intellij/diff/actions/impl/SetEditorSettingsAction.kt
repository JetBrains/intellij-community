// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions.impl

import com.intellij.diff.tools.combined.CombinedDiffRegistry
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.tools.util.SyncScrollSupport
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder
import com.intellij.diff.util.CombinedDiffToggle
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.ui.BadgeIcon
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import com.intellij.util.ui.JBUI
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants

class SetEditorSettingsAction(
  settings: TextDiffSettingsHolder.TextDiffSettings,
  editors: List<Editor?>,
) : DumbAwareAction(DiffBundle.message("editor.settings")) {

  private val badgeIcon = BadgeIcon(AllIcons.General.GearPlain, JBUI.CurrentTheme.IconBadge.INFORMATION)
  private val editorSettingsActionGroup = SetEditorSettingsActionGroup(settings, editors)

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.icon = AllIcons.General.GearPlain
    val diffModeToggle = getDiffModeToggle(e) ?: return
    if (!diffModeToggle.isCombinedDiffEnabled && CombinedDiffRegistry.showBadge()) {
      e.presentation.icon = badgeIcon
    }
  }


  override fun actionPerformed(e: AnActionEvent) {
    val popup = createMainPopup(editorSettingsActionGroup, e.dataContext)

    PopupUtil.showForActionButtonEvent(popup, e)
    val diffModeToggle = getDiffModeToggle(e) ?: return

    popup.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        if (!diffModeToggle.isCombinedDiffEnabled) {
          CombinedDiffRegistry.resetBadge()
        }
      }
    })
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT


  fun applyDefaults() {
    editorSettingsActionGroup.applyDefaults()
  }

  fun setSyncScrollSupport(syncScrollSupport: SyncScrollSupport.Support) {
    editorSettingsActionGroup.setSyncScrollSupport(syncScrollSupport)
  }

  private val presentationFactory = PresentationFactory()

  private fun createMainPopup(actionGroup: ActionGroup, dataContext: DataContext): JBPopup {
    return MyPopup(actionGroup, dataContext)
  }

  private fun getDiffModeToggle(e: AnActionEvent): CombinedDiffToggle? {
    val context = e.getData(DiffDataKeys.DIFF_CONTEXT) ?: return null
    return context.getUserData(DiffUserDataKeysEx.COMBINED_DIFF_TOGGLE)
  }

  inner class MyPopup(
    group: ActionGroup,
    context: DataContext
  ) : PopupFactoryImpl.ActionGroupPopup(null, group, context, false, false, true, true, null, -1, null, null, presentationFactory, false) {

    override fun getListElementRenderer(): ListCellRenderer<*> {
      return MyRenderer(presentationFactory, this)
    }
  }

  private class MyRenderer(
    private val presentationFactory: PresentationFactory,
    myPopup: MyPopup
  ) : PopupListElementRenderer<Any>(myPopup) {
    override fun customizeComponent(list: JList<out Any>?, value: Any, isSelected: Boolean) {
      myTextLabel.icon = null
      myTextLabel.horizontalTextPosition = SwingConstants.RIGHT
      super.customizeComponent(list, value, isSelected)

      if (value !is PopupFactoryImpl.ActionItem) return
      val presentation = presentationFactory.getPresentation(value.action)

      val secondaryIcon = presentation.getClientProperty(ActionMenu.SECONDARY_ICON) ?: return
      myTextLabel.horizontalTextPosition = SwingConstants.LEFT
      myTextLabel.iconTextGap = JBUI.CurrentTheme.ActionsList.elementIconGap()
      myTextLabel.icon = secondaryIcon
    }
  }
}







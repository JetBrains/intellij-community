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
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.ui.BadgeIcon
import com.intellij.ui.popup.ActionPopupOptions
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.SwingConstants

class SetEditorSettingsAction @ApiStatus.Internal constructor(
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
    val popup = MyPopup(editorSettingsActionGroup, e.dataContext)
    PopupUtil.showForActionButtonEvent(popup, e)

    popup.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        CombinedDiffRegistry.resetBadge()
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

  private fun getDiffModeToggle(e: AnActionEvent): CombinedDiffToggle? {
    val context = e.getData(DiffDataKeys.DIFF_CONTEXT) ?: return null
    return context.getUserData(DiffUserDataKeysEx.COMBINED_DIFF_TOGGLE)
  }

  private inner class MyPopup(
    group: ActionGroup,
    context: DataContext
  ) : PopupFactoryImpl.ActionGroupPopup(
    null, null, group, context,
    ActionPlaces.getPopupPlace("SetEditorSettingsAction"), presentationFactory,
    ActionPopupOptions.mnemonicsAndDisabled(), null) {

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







// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions.impl

import com.intellij.diff.tools.util.SyncScrollSupport
import com.intellij.diff.tools.util.base.HighlightingLevel
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder
import com.intellij.diff.tools.util.breadcrumbs.BreadcrumbsPlacement
import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ActionUtil.copyFrom
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actions.AbstractToggleUseSoftWrapsAction
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.containers.toArray
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

open class SetEditorSettingsActionGroup @ApiStatus.Internal constructor(
  private val textSettings: TextDiffSettingsHolder.TextDiffSettings,
  private val editorsSupplier: () -> List<Editor>,
) : ActionGroup(DiffBundle.message("editor.settings"), null, AllIcons.General.GearPlain), DumbAware {
  @ApiStatus.Internal
  constructor(
    textSettings: TextDiffSettingsHolder.TextDiffSettings,
    editors: List<Editor>,
  ) : this(textSettings, { editors })

  private var syncScrollSupport: SyncScrollSupport.Support? = null
  private val editors get() = editorsSupplier()
  private val _appearanceGroup = AppearanceGroup()

  @ApiStatus.Internal
  val appearanceGroup: ActionGroup = _appearanceGroup

  private var viewerSettingsActions = emptyList<AnAction>()
  private var diffSettingsActions = emptyList<AnAction>()

  @ApiStatus.Internal
  fun setSettingsActions(viewerSettingsActions: List<AnAction>, diffSettingsActions: List<AnAction>) {
    this.viewerSettingsActions = viewerSettingsActions
    this.diffSettingsActions = diffSettingsActions
  }

  @ApiStatus.Internal
  fun setSyncScrollSupport(syncScrollSupport: SyncScrollSupport.Support?) {
    this.syncScrollSupport = syncScrollSupport
  }

  @ApiStatus.Internal
  fun applyDefaults() {
    if (!Registry.`is`("diff.highlighting.level.visible")) {
      textSettings.highlightingLevel = HighlightingLevel.INSPECTIONS
    }
    _appearanceGroup.applyDefaults(editors)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isPopupGroup = e.isFromActionToolbar
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> = buildList {
    addAll(viewerSettingsActions)
    add(ActionManager.getInstance().getAction(IdeActions.GROUP_DIFF_EDITOR_MODES))
    add(ActionManager.getInstance().getAction(IdeActions.GROUP_DIFF_VIEWER_SETTINGS))
    addAll(diffSettingsActions)
    add(Separator.getInstance())
    add(appearanceGroup)
    add(ActionManager.getInstance().getAction(IdeActions.ACTION_CONTEXT_HELP))
  }.toArray(EMPTY_ARRAY)

  private inner class AppearanceGroup : ActionGroup(), DumbAware, EditorSettingAction {
    private val actions: Array<AnAction> = arrayOf(
      object : EditorSettingToggleAction("EditorToggleShowWhitespaces") {
        override var isSelected: Boolean
          get() = textSettings.isShowWhitespaces
          set(value) {
            textSettings.isShowWhitespaces = value
          }

        override fun apply(editor: Editor, value: Boolean) {
          if (editor.getSettings().isWhitespacesShown() != value) {
            editor.getSettings().setWhitespacesShown(value)
            editor.getComponent().repaint()
          }
        }
      },
      object : EditorSettingToggleAction("EditorToggleShowLineNumbers") {
        override var isSelected: Boolean
          get() = textSettings.isShowLineNumbers
          set(value) {
            textSettings.isShowLineNumbers = value
          }

        override fun apply(editor: Editor, value: Boolean) {
          if (editor.getSettings().isLineNumbersShown() != value) {
            editor.getSettings().setLineNumbersShown(value)
            editor.getComponent().repaint()
          }
        }
      },
      object : EditorSettingToggleAction("EditorToggleShowIndentLines") {
        override var isSelected: Boolean
          get() = textSettings.isShowIndentLines
          set(value) {
            textSettings.isShowIndentLines = value
          }

        override fun apply(editor: Editor, value: Boolean) {
          if (editor.getSettings().isIndentGuidesShown() != value) {
            editor.getSettings().setIndentGuidesShown(value)
            editor.getComponent().repaint()
          }
        }
      },
      object : EditorSettingToggleAction("EditorToggleUseSoftWraps") {
        private var isSoftWrapForced = false

        override var isSelected: Boolean
          get() {
            val hasForcedSoftWraps = editors.any { it.getUserData(EditorImpl.FORCED_SOFT_WRAPS) == true }
            return isSoftWrapForced || textSettings.isUseSoftWraps || hasForcedSoftWraps
          }
          set(value) {
            isSoftWrapForced = false
            textSettings.isUseSoftWraps = value
          }

        override fun apply(editor: Editor, value: Boolean) {
          if (editor.getSettings().isUseSoftWraps() == value) return

          syncScrollSupport?.enterDisableScrollSection()
          try {
            AbstractToggleUseSoftWrapsAction.toggleSoftWraps(editor, null, value)
          }
          finally {
            syncScrollSupport?.exitDisableScrollSection()
          }
        }

        override fun applyDefaults(editors: List<Editor>) {
          if (!textSettings.isUseSoftWraps) {
            editors.forEach {
              isSoftWrapForced = isSoftWrapForced || (it as? EditorImpl)?.softWrapModel?.shouldSoftWrapsBeForced() ?: false
            }
          }
          super.applyDefaults(editors)
        }
      },
      EditorHighlightingLayerGroup(),
      EditorBreadcrumbsPlacementGroup())

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      e.presentation.text = DiffBundle.message("settings.appearance")
      e.presentation.isPopupGroup = e.isFromToolbar()
    }

    override fun getChildren(e: AnActionEvent?) =
      if (e.isFromToolbar()) {
        actions
      }
      else {
        arrayOf<AnAction>(Separator.create(DiffBundle.message("settings.appearance"))) + actions
      }

    override fun applyDefaults(editors: List<Editor>) {
      actions.filterIsInstance<EditorSettingAction>().forEach { it.applyDefaults(editors) }
    }
  }

  private abstract inner class EditorSettingToggleAction(@NonNls actionId: @NonNls String) : ToggleAction(),
                                                                                             DumbAware,
                                                                                             EditorSettingAction {
    init {
      copyFrom(this, actionId)
      getTemplatePresentation().setIcon(null)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      return this.isSelected
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      this.isSelected = state
      editors.forEach {
        apply(it, state)
      }
    }

    abstract var isSelected: Boolean

    abstract fun apply(editor: Editor, value: Boolean)

    override fun applyDefaults(editors: List<Editor>) {
      editors.forEach { apply(it, this.isSelected) }
    }
  }

  private inner class EditorHighlightingLayerGroup : ActionGroup(DiffBundle.message("highlighting.level"), false),
                                                     EditorSettingAction,
                                                     DumbAware {

    private val options = HighlightingLevel.entries.map { OptionAction(it) }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      if (!Registry.`is`("diff.highlighting.level.visible")) {
        e.presentation.isEnabledAndVisible = false
        return
      }
      e.presentation.isPopupGroup = !e.isFromToolbar()
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> = buildList {
      if (e.isFromToolbar()) add(Separator.create(getTemplatePresentation().text))
      addAll(options)
    }.toArray(EMPTY_ARRAY)

    override fun applyDefaults(editors: List<Editor>) {
      apply(textSettings.highlightingLevel)
    }

    fun apply(layer: HighlightingLevel) {
      editors.filterIsInstance<EditorImpl>().forEach {
        it.setHighlightingPredicate(layer.condition)
      }
    }

    private inner class OptionAction(private val layer: HighlightingLevel) : ToggleAction(layer.text, null, layer.icon),
                                                                             DumbAware {
      override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
      }

      override fun isSelected(e: AnActionEvent): Boolean {
        return textSettings.highlightingLevel == layer
      }

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        textSettings.highlightingLevel = layer
        apply(layer)
      }
    }
  }

  private inner class EditorBreadcrumbsPlacementGroup : ActionGroup(ActionsBundle.message("group.EditorBreadcrumbsSettings.text"), false),
                                                        EditorSettingAction,
                                                        DumbAware {
    private val options = BreadcrumbsPlacement.entries.map { OptionAction(it) }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.isPopupGroup = !e.isFromToolbar()
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> = buildList {
      if (e.isFromToolbar()) add(Separator.create(getTemplatePresentation().text))
      addAll(options)
    }.toArray<AnAction>(EMPTY_ARRAY)

    override fun applyDefaults(editors: List<Editor>) {}

    private inner class OptionAction(private val option: BreadcrumbsPlacement) : ToggleAction(), DumbAware {
      init {
        copyFrom(this, option.actionId)
      }

      override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
      }

      override fun isSelected(e: AnActionEvent): Boolean {
        return textSettings.breadcrumbsPlacement == option
      }

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        textSettings.breadcrumbsPlacement = option
      }
    }
  }

  private interface EditorSettingAction {
    fun applyDefaults(editors: List<Editor>)
  }

  private fun AnActionEvent?.isFromToolbar() =
    this?.place != null && (this.place.contains(ActionPlaces.DIFF_TOOLBAR) || this.place.contains(ActionPlaces.DIFF_RIGHT_TOOLBAR))
}

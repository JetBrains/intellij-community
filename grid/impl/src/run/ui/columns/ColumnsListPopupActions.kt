package com.intellij.database.run.ui.columns

import com.intellij.database.DataGridBundle
import com.intellij.database.run.actions.disableWithReason
import com.intellij.ide.actions.CopyAction
import com.intellij.ide.ui.customization.CustomizableActionGroupProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction

internal const val COLUMNS_LIST_POPUP_GROUP: String = "Console.TableResult.ColumnsList.Popup"

internal class ColumnsListCopyNamesAction : CopyAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    super.update(e)
    val actions = e.getData(ColumnsListActions.DATA_KEY)
    e.presentation.isEnabledAndVisible = actions != null && e.getData(PlatformDataKeys.COPY_PROVIDER) === actions.copyProvider &&
                                         actions.copyTargetCount > 0
    if (actions != null) {
      e.presentation.text = DataGridBundle.message("action.Console.TableResult.ColumnsList.CopyNames.multiple.text", actions.copyTargetCount)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val actions = e.getData(ColumnsListActions.DATA_KEY) ?: return
    if (e.getData(PlatformDataKeys.COPY_PROVIDER) === actions.copyProvider) super.actionPerformed(e)
  }
}

internal class ColumnsListCopyNamesAndTypesAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val actions = e.getData(ColumnsListActions.DATA_KEY)
    e.presentation.isEnabledAndVisible = actions != null && actions.copyTargetCount > 0
    if (actions != null) {
      e.presentation.text = DataGridBundle.message("action.Console.TableResult.ColumnsList.CopyNamesAndTypes.multiple.text",
                                                 actions.copyTargetCount)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getData(ColumnsListActions.DATA_KEY)?.copyNamesAndTypes()
  }
}

internal class ColumnsListRestoreOriginalOrderAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val actions = e.getData(ColumnsListActions.DATA_KEY)
    e.presentation.isEnabledAndVisible = actions != null && !actions.originalOrder
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getData(ColumnsListActions.DATA_KEY)?.restoreOriginalOrder()
  }
}

internal class ColumnsListPinSelectedAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val actions = e.getData(ColumnsListActions.DATA_KEY)
    e.presentation.isEnabledAndVisible = actions != null
    if (actions == null) return
    val columns = actions.selectedColumns()
    e.presentation.text = DataGridBundle.message(
      if (columns.size() == 1) "action.Console.TableResult.PinColumn.text" else "action.Console.TableResult.PinColumns.text"
    )
    if (!actions.commands.offersPin(columns)) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    disableWithReason(e, actions.commands.reasonPinRefuses(columns))
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getData(ColumnsListActions.DATA_KEY)?.pinSelected()
  }
}

internal class ColumnsListUnpinSelectedAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val actions = e.getData(ColumnsListActions.DATA_KEY)
    e.presentation.isEnabledAndVisible = actions != null
    if (actions == null) return
    val columns = actions.selectedColumns()
    e.presentation.text = DataGridBundle.message(
      if (columns.size() == 1) "action.Console.TableResult.UnpinColumn.text" else "action.Console.TableResult.UnpinColumns.text"
    )
    if (!actions.commands.offersUnpin(columns)) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    disableWithReason(e, actions.commands.reasonUnpinRefuses())
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getData(ColumnsListActions.DATA_KEY)?.unpinSelected()
  }
}

internal class ColumnsListPinUpToHereAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val actions = e.getData(ColumnsListActions.DATA_KEY)
    e.presentation.isEnabledAndVisible = actions != null
    if (actions == null) return
    val column = actions.selectedColumns().asIterable().singleOrNull()
    if (column == null || !actions.commands.offersPinUpToHere(column)) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    disableWithReason(e, actions.commands.reasonPinUpToHereRefuses(column))
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getData(ColumnsListActions.DATA_KEY)?.pinUpToHere()
  }
}

internal class ColumnsListUnpinAllAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val actions = e.getData(ColumnsListActions.DATA_KEY)
    e.presentation.isEnabledAndVisible = actions != null
    if (actions == null) return
    if (!actions.commands.offersUnpinAll()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    disableWithReason(e, actions.commands.reasonUnpinRefuses())
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getData(ColumnsListActions.DATA_KEY)?.unpinAll()
  }
}

internal class ColumnsListCustomizableGroupProvider : CustomizableActionGroupProvider() {
  override fun registerGroups(registrar: CustomizableActionGroupRegistrar) {
    registrar.addCustomizableActionGroup(COLUMNS_LIST_POPUP_GROUP, DataGridBundle.message("group.Console.TableResult.ColumnsList.Popup.text"))
  }
}

package com.intellij.database.run.actions

import com.intellij.database.DataGridBundle
import com.intellij.database.DatabaseDataKeys
import com.intellij.database.datagrid.GridUtil
import com.intellij.database.datagrid.GridUtilCore
import com.intellij.database.run.ui.CustomPageSizeForm
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class SetCustomPageSizeAction : DumbAwareAction(ApplicationBundle.messagePointer("custom.option"), ApplicationBundle.messagePointer("custom.option.description"), null as Icon?) {
  override fun update(e: AnActionEvent) {
    val grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY)
    e.presentation.setEnabledAndVisible(grid != null)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY)
    if (grid == null) return
    val pageModel = grid.getDataHookup().getPageModel()

    object : SetPageSizeDialogWrapper(getEventProject(e)) {
      override val pageSize: Int get() {
        val unlimited = GridUtilCore.isPageSizeUnlimited(pageModel.getPageSize())
        return if (unlimited) GridUtilCore.getPageSize(GridUtil.getSettings(grid)) else pageModel.getPageSize()
      }

      override val isLimitPageSize: Boolean get() {
        return !GridUtilCore.isPageSizeUnlimited(pageModel.getPageSize())
      }

      override fun doOKAction() {
        super.doOKAction()
        setPageSizeAndReload(myForm.getPageSize(), grid)
      }
    }.show()
  }

  abstract class SetPageSizeDialogWrapper(project: Project?) : DialogWrapper(project) {
    protected val myForm: CustomPageSizeForm = CustomPageSizeForm()

    init {
      title = DataGridBundle.message("dialog.title.change.page.size")
      initListeners()

      init()
    }

    override fun getPreferredFocusedComponent(): JComponent? {
      return myForm.resultPageSizeTextField
    }

    private fun initListeners() {
      myForm.resultPageSizeTextField.document.addDocumentListener(object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent?) {
          updateOk()
        }

        override fun removeUpdate(e: DocumentEvent?) {
          updateOk()
        }

        override fun changedUpdate(e: DocumentEvent?) {
          updateOk()
        }
      })
    }

    private fun updateOk() {
      okAction.isEnabled = isOKActionEnabled
    }

    override fun isOKActionEnabled(): Boolean {
      try {
        myForm.resultPageSizeTextField.validateContent()
        return true
      }
      catch (_: ConfigurationException) {
        return false
      }
    }

    protected abstract val pageSize: Int

    protected abstract val isLimitPageSize: Boolean

    override fun createCenterPanel(): JComponent {
      myForm.reset(this.isLimitPageSize, this.pageSize)
      return myForm.panel
    }
  }
}

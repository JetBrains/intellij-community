// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.util

import com.intellij.execution.ExecutionBundle
import com.intellij.ide.CopyProvider
import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.ListTableModel
import java.awt.GridLayout
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import javax.swing.DefaultCellEditor
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.table.TableCellEditor

open class EnvVariablesTable : ListTableWithButtons<EnvironmentVariable>() {
  private var myPanel: CopyPasteProviderPanel? = null
  private var myPasteEnabled = false

  init {
    getTableView().getEmptyText().setText(ExecutionBundle.message("empty.text.no.variables"))
    val copyAction = ActionManager.getInstance().getAction(IdeActions.ACTION_COPY)
    if (copyAction != null) {
      copyAction.registerCustomShortcutSet(copyAction.getShortcutSet(), getTableView()) // no need to add in popup menu
    }
    val pasteAction = ActionManager.getInstance().getAction(IdeActions.ACTION_PASTE)
    if (pasteAction != null) {
      pasteAction.registerCustomShortcutSet(pasteAction.getShortcutSet(), getTableView()) // no need to add in popup menu
    }
  }

  fun setPasteActionEnabled(enabled: Boolean) {
    myPasteEnabled = enabled
  }

  override fun createListModel(): ListTableModel<EnvironmentVariable>? {
    return ListTableModel<EnvironmentVariable>(this.NameColumnInfo(), this.ValueColumnInfo())
  }

  fun editVariableName(environmentVariable: EnvironmentVariable) {
    ApplicationManager.getApplication().invokeLater(Runnable {
      val actualEnvVar = getElements().find { it.name == environmentVariable.name }
      if (actualEnvVar == null) {
        return@Runnable
      }

      setSelection(actualEnvVar)
      if (actualEnvVar.getNameIsWriteable()) {
        editSelection(0)
      }
    })
  }

  override fun setValues(list: List<EnvironmentVariable>) {
    super.setValues(
      ContainerUtil.sorted(list, Comparator.comparing({ obj: EnvironmentVariable? -> obj!!.getName() }, NaturalComparator.INSTANCE)))
  }

  val environmentVariables: MutableList<EnvironmentVariable>
    get() = getElements()

  override fun getComponent(): JComponent {
    if (myPanel == null) {
      myPanel = CopyPasteProviderPanel(super.getComponent())
    }
    return myPanel!!
  }

  override fun createElement(): EnvironmentVariable {
    return EnvironmentVariable("", "", false)
  }

  override fun isEmpty(element: EnvironmentVariable): Boolean {
    return element.getName().isEmpty() && element.getValue().isEmpty()
  }


  override fun cloneElement(envVariable: EnvironmentVariable): EnvironmentVariable? {
    return envVariable.clone()
  }

  override fun canDeleteElement(selection: EnvironmentVariable): Boolean {
    return !selection.getIsPredefined()
  }

  protected open inner class NameColumnInfo : ElementsColumnInfoBase<EnvironmentVariable>(
    ExecutionBundle.message("env.variable.column.name.title")) {
    override fun valueOf(environmentVariable: EnvironmentVariable): String? {
      return environmentVariable.getName()
    }

    override fun isCellEditable(environmentVariable: EnvironmentVariable): Boolean {
      return environmentVariable.getNameIsWriteable()
    }

    override fun setValue(environmentVariable: EnvironmentVariable, s: String) {
      if (s == valueOf(environmentVariable)) {
        return
      }
      environmentVariable.setName(s)
      setModified()
    }

    override fun getDescription(environmentVariable: EnvironmentVariable): String? {
      return environmentVariable.getDescription()
    }

    override fun getEditor(variable: EnvironmentVariable): TableCellEditor {
      return DefaultCellEditor(JTextField())
    }
  }

  protected open inner class ValueColumnInfo : ElementsColumnInfoBase<EnvironmentVariable>(
    ExecutionBundle.message("env.variable.column.value.title")) {
    override fun valueOf(environmentVariable: EnvironmentVariable): String? {
      return environmentVariable.getValue()
    }

    override fun isCellEditable(environmentVariable: EnvironmentVariable): Boolean {
      return !environmentVariable.getIsPredefined()
    }

    override fun setValue(environmentVariable: EnvironmentVariable, s: String) {
      if (s == valueOf(environmentVariable)) {
        return
      }
      environmentVariable.setValue(s)
      setModified()
    }

    override fun getDescription(environmentVariable: EnvironmentVariable): String? {
      return environmentVariable.getDescription()
    }

    override fun getEditor(variable: EnvironmentVariable?): TableCellEditor {
      return StringWithNewLinesCellEditor()
    }
  }

  private inner class CopyPasteProviderPanel(component: JComponent?) : JPanel(
    GridLayout(1, 1)), UiDataProvider, CopyProvider, PasteProvider {
    init {
      add(component)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun uiDataSnapshot(sink: DataSink) {
      sink.set<CopyProvider>(PlatformDataKeys.COPY_PROVIDER, this)
      sink.set<PasteProvider>(PlatformDataKeys.PASTE_PROVIDER, this)
    }

    override fun performCopy(dataContext: DataContext) {
      val view = getTableView()
      if (view.isEditing()) {
        var row = view.getEditingRow()
        var column = view.getEditingColumn()
        if (row < 0 || column < 0) {
          row = view.getSelectedRow()
          column = view.getSelectedColumn()
        }
        if (row >= 0 && column >= 0) {
          val component = (view.getCellEditor() as DefaultCellEditor).getComponent()
          var text: String? = ""
          when (component) {
            is JTextField -> {
              text = component.getSelectedText()
            }
            is JComboBox<*> -> {
              text = (component.getEditor().getEditorComponent() as JTextField).getSelectedText()
            }
            else -> {
              Logger.getInstance(EnvVariablesTable::class.java).error("Unknown editor type: " + component)
            }
          }
          CopyPasteManager.getInstance().setContents(StringSelection(text))
        }
        return
      }
      stopEditing()
      val sb = StringBuilder()
      val variables = getSelection()
      for (environmentVariable in variables) {
        if (isEmpty(environmentVariable)) continue
        if (!sb.isEmpty()) sb.append(';')
        sb.append(StringUtil.escapeChars(environmentVariable!!.getName(), '=', ';', '\n')).append('=')
          .append(StringUtil.escapeChars(environmentVariable.getValue(), '=', ';', '\n'))
      }
      CopyPasteManager.getInstance().setContents(StringSelection(sb.toString()))
    }


    override fun isCopyEnabled(dataContext: DataContext): Boolean {
      return !getSelection().isEmpty()
    }

    override fun isCopyVisible(dataContext: DataContext): Boolean {
      return isCopyEnabled(dataContext)
    }

    override fun performPaste(dataContext: DataContext) {
      val content = CopyPasteManager.getInstance().getContents<String?>(DataFlavor.stringFlavor)
      if (StringUtil.isEmpty(content)) {
        return
      }
      val map: MutableMap<String, String> = parseEnvsFromText(content)
      val view = getTableView()
      if ((view.isEditing() || map.isEmpty())) {
        var row = view.getEditingRow()
        var column = view.getEditingColumn()
        if (row < 0 || column < 0) {
          row = view.getSelectedRow()
          column = view.getSelectedColumn()
        }
        if (row >= 0 && column >= 0) {
          val editor = view.getCellEditor()
          if (editor != null) {
            val component = (editor as DefaultCellEditor).getComponent()
            if (component is JTextField) {
              component.paste()
            }
          }
        }
        return
      }
      stopEditing()
      val parsed = mutableListOf<EnvironmentVariable>()
      for (entry in map.entries) {
        parsed.add(EnvironmentVariable(entry.key, entry.value, false))
      }
      val variables = (environmentVariables + parsed).filter {
        !StringUtil.isEmpty(it.getName()) ||
        !StringUtil.isEmpty(it.getValue())
      }
      setValues(variables)
    }

    override fun isPastePossible(dataContext: DataContext): Boolean {
      return myPasteEnabled
    }

    override fun isPasteEnabled(dataContext: DataContext): Boolean {
      return myPasteEnabled
    }
  }

  override fun createExtraToolbarActions(): Array<AnAction?> {
    val copyButton = ActionManager.getInstance().getAction(IdeActions.ACTION_COPY)
    val pasteButton = ActionManager.getInstance().getAction(IdeActions.ACTION_PASTE)
    return arrayOf<AnAction?>(copyButton, pasteButton)
  }

  companion object {

    /**
     * This will parse the environment variable pairs from a text string. The expected text corresponds to the text generated by
     * `com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton.stringifyEnvironment`.
     *
     * The specification for that string follows.
     *
     * The string consists of pairs `key=value`, separated by one or more `;` characters, and the whole string terminated by zero or more
     * `;` characters.
     *
     * Each of `key` and `value` might be either
     * - a sequence of characters excluding `"`, `;` and `=`,
     * - or a sequence of any characters (including `;` and `=`) inside of double quotes `""`.
     *
     * For the latter case, escape sequences are allowed: character `\` might precede other characters, so that they lose their meta meaning
     * and just become characters included into the `key` or `value`. Escape sequences supported:
     * - `\\` that gets converted into `\` character,
     * - `\"` that gets converted into `"` character.
     *
     * Note that this method has "quirks mode" behavior for incorrect character sequences, such as unpaired quotes, invalid escape sequences
     * etc. This behavior is not a part of the method contract.
     */
    @JvmStatic
    fun parseEnvsFromText(content: String?): MutableMap<String, String> {
      return EnvVariables.parseFromText(content).envs.toMutableMap()
    }
  }
}

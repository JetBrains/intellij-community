// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jsonpath.ui

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.find.FindBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.json.JsonBundle
import com.intellij.json.JsonFileType
import com.intellij.json.psi.JsonFile
import com.intellij.jsonpath.JsonPathFileType
import com.intellij.jsonpath.ui.JsonPathEvaluateManager.Companion.JSON_PATH_EVALUATE_EXPRESSION_KEY
import com.intellij.jsonpath.ui.JsonPathEvaluateManager.Companion.JSON_PATH_EVALUATE_HISTORY
import com.intellij.jsonpath.ui.JsonPathEvaluateManager.Companion.JSON_PATH_EVALUATE_RESULT_KEY
import com.intellij.jsonpath.ui.JsonPathEvaluateManager.Companion.JSON_PATH_EVALUATE_SOURCE_KEY
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.components.*
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.popup.PopupState
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.KeyEvent
import java.util.*
import java.util.function.Supplier
import javax.swing.*
import kotlin.collections.ArrayDeque

internal abstract class JsonPathEvaluateView(protected val project: Project) : SimpleToolWindowPanel(true, true), Disposable {
  companion object {
    init {
      Configuration.setDefaults(object : Configuration.Defaults {
        private val jsonProvider = JacksonJsonProvider()
        private val mappingProvider = JacksonMappingProvider()

        override fun jsonProvider() = jsonProvider

        override fun mappingProvider() = mappingProvider

        override fun options() = EnumSet.noneOf(Option::class.java)
      })
    }
  }

  protected val searchTextField: EditorTextField = object : EditorTextField(project, JsonPathFileType.INSTANCE) {
    override fun processKeyBinding(ks: KeyStroke?, e: KeyEvent?, condition: Int, pressed: Boolean): Boolean {
      if (e?.keyCode == KeyEvent.VK_ENTER && pressed) {
        evaluate()
        return true
      }
      return super.processKeyBinding(ks, e, condition, pressed)
    }

    override fun createEditor(): EditorEx {
      val editor = super.createEditor()

      editor.setBorder(JBUI.Borders.empty())
      editor.component.border = JBUI.Borders.empty(4, 0, 3, 6)
      editor.component.isOpaque = false
      editor.backgroundColor = UIUtil.getTextFieldBackground()

      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
      if (psiFile != null) {
        psiFile.putUserData(JSON_PATH_EVALUATE_EXPRESSION_KEY, true)
        psiFile.putUserData(JSON_PATH_EVALUATE_SOURCE_KEY, Supplier(::getJsonFile))
      }

      return editor
    }
  }

  protected val searchWrapper: JPanel = object : NonOpaquePanel(BorderLayout()) {
    override fun updateUI() {
      super.updateUI()
      this.background = UIUtil.getTextFieldBackground()
    }
  }

  val searchComponent: JComponent
    get() = searchTextField

  protected val resultWrapper: JBPanelWithEmptyText = JBPanelWithEmptyText(BorderLayout())
  private val resultLabel = JBLabel(JsonBundle.message("jsonpath.evaluate.result"))
  private val resultEditor: Editor = initJsonEditor("result.json", true, EditorKind.PREVIEW)

  private val errorOutputArea: JBTextArea = JBTextArea()
  private val errorOutputContainer: JScrollPane = JBScrollPane(errorOutputArea)
  private val evalOptions: MutableSet<Option> = mutableSetOf()

  init {
    resultEditor.putUserData(JSON_PATH_EVALUATE_RESULT_KEY, true)
    resultEditor.setBorder(JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0))
    resultLabel.border = JBUI.Borders.empty(3, 6)
    resultWrapper.emptyText.text = JsonBundle.message("jsonpath.evaluate.no.result")
    errorOutputContainer.border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)

    val historyButton = SearchHistoryButton(ShowHistoryAction(), false)
    val historyButtonWrapper = NonOpaquePanel(BorderLayout())
    historyButtonWrapper.border = JBUI.Borders.empty(3, 6, 3, 6)
    historyButtonWrapper.add(historyButton, BorderLayout.NORTH)

    searchTextField.setFontInheritedFromLAF(false) // use font as in regular editor

    searchWrapper.add(historyButtonWrapper, BorderLayout.WEST)
    searchWrapper.add(searchTextField, BorderLayout.CENTER)
    searchWrapper.border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
    searchWrapper.isOpaque = true

    errorOutputArea.isEditable = false
    errorOutputArea.wrapStyleWord = true
    errorOutputArea.lineWrap = true
    errorOutputArea.border = JBUI.Borders.empty(10)

    setExpression("$..*")
  }

  protected fun initToolbar() {
    val actionGroup = DefaultActionGroup()
    fillToolbarOptions(actionGroup)

    val toolbar = ActionManager.getInstance().createActionToolbar("JsonPathEvaluateToolbar", actionGroup, true)
    toolbar.setTargetComponent(this)

    setToolbar(toolbar.component)
  }

  protected abstract fun getJsonFile(): JsonFile?

  protected fun resetExpressionHighlighting() {
    val jsonPathFile = PsiDocumentManager.getInstance(project).getPsiFile(searchTextField.document)
    if (jsonPathFile != null) {
      // reset inspections in expression
      DaemonCodeAnalyzer.getInstance(project).restart(jsonPathFile)
    }
  }

  private fun fillToolbarOptions(group: DefaultActionGroup) {
    val outputComboBox = object : ComboBoxAction() {
      override fun createPopupActionGroup(button: JComponent?): DefaultActionGroup {
        val outputItems = DefaultActionGroup()
        outputItems.add(OutputOptionAction(false, JsonBundle.message("jsonpath.evaluate.output.values")))
        outputItems.add(OutputOptionAction(true, JsonBundle.message("jsonpath.evaluate.output.paths")))
        return outputItems
      }

      override fun update(e: AnActionEvent) {
        val presentation = e.presentation
        if (e.project == null) return

        presentation.text = if (evalOptions.contains(Option.AS_PATH_LIST)) {
          JsonBundle.message("jsonpath.evaluate.output.paths")
        }
        else {
          JsonBundle.message("jsonpath.evaluate.output.values")
        }
      }

      override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.add(JLabel(JsonBundle.message("jsonpath.evaluate.output.option")),
                  GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.BOTH, JBUI.insetsLeft(5), 0, 0))
        panel.add(super.createCustomComponent(presentation, place),
                  GridBagConstraints(1, 0, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.BOTH, JBInsets.emptyInsets(), 0, 0))
        return panel
      }
    }

    group.add(outputComboBox)

    group.add(DefaultActionGroup(JsonBundle.message("jsonpath.evaluate.options"), true).apply {
      templatePresentation.icon = AllIcons.General.Settings

      add(OptionToggleAction(Option.SUPPRESS_EXCEPTIONS, JsonBundle.message("jsonpath.evaluate.suppress.exceptions")))
      add(OptionToggleAction(Option.ALWAYS_RETURN_LIST, JsonBundle.message("jsonpath.evaluate.return.list")))
      add(OptionToggleAction(Option.DEFAULT_PATH_LEAF_TO_NULL, JsonBundle.message("jsonpath.evaluate.nullize.missing.leaf")))
      add(OptionToggleAction(Option.REQUIRE_PROPERTIES, JsonBundle.message("jsonpath.evaluate.require.all.properties")))
    })
  }

  protected fun initJsonEditor(fileName: String, isViewer: Boolean, kind: EditorKind): Editor {
    val sourceVirtualFile = LightVirtualFile(fileName, JsonFileType.INSTANCE, "") // require strict JSON with quotes
    val sourceFile = PsiManager.getInstance(project).findFile(sourceVirtualFile)!!
    val document = PsiDocumentManager.getInstance(project).getDocument(sourceFile)!!

    val editor = EditorFactory.getInstance().createEditor(document, project, sourceVirtualFile, isViewer, kind)
    editor.settings.isLineNumbersShown = false
    return editor
  }

  fun setExpression(jsonPathExpr: String) {
    searchTextField.text = jsonPathExpr
  }

  private fun setResult(result: String) {
    WriteAction.run<Throwable> {
      resultEditor.document.setText(result)
      PsiDocumentManager.getInstance(project).commitDocument(resultEditor.document)

      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(resultEditor.document)!!

      ReformatCodeProcessor(psiFile, false).run()
    }

    if (!resultWrapper.components.contains(resultEditor.component)) {
      resultWrapper.removeAll()
      resultWrapper.add(resultLabel, BorderLayout.NORTH)

      resultWrapper.add(resultEditor.component, BorderLayout.CENTER)
      resultWrapper.revalidate()
      resultWrapper.repaint()
    }

    resultEditor.caretModel.moveToOffset(0)
  }

  private fun setError(error: String) {
    errorOutputArea.text = error

    if (!resultWrapper.components.contains(errorOutputArea)) {
      resultWrapper.removeAll()
      resultWrapper.add(resultLabel, BorderLayout.NORTH)

      resultWrapper.add(errorOutputContainer, BorderLayout.CENTER)
      resultWrapper.revalidate()
      resultWrapper.repaint()
    }
  }

  private fun evaluate() {
    val evaluator = JsonPathEvaluator(getJsonFile(), searchTextField.text, evalOptions)
    val result = evaluator.evaluate()

    when (result) {
      is IncorrectExpression -> setError(result.message)
      is IncorrectDocument -> setError(result.message)
      is ResultNotFound -> setError(result.message)
      is ResultString -> setResult(result.value)
    }

    if (result != null && result !is IncorrectExpression) {
      addJSONPathToHistory(searchTextField.text.trim())
    }
  }

  override fun dispose() {
    EditorFactory.getInstance().releaseEditor(resultEditor)
  }

  private inner class OutputOptionAction(private val enablePaths: Boolean, @NlsActions.ActionText message: String)
    : DumbAwareAction(message) {
    override fun actionPerformed(e: AnActionEvent) {
      if (enablePaths) {
        evalOptions.add(Option.AS_PATH_LIST)
      }
      else {
        evalOptions.remove(Option.AS_PATH_LIST)
      }
      evaluate()
    }
  }

  private inner class OptionToggleAction(private val option: Option, @NlsActions.ActionText message: String) : ToggleAction(message) {
    override fun isSelected(e: AnActionEvent): Boolean {
      return evalOptions.contains(option)
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (state) {
        evalOptions.add(option)
      }
      else {
        evalOptions.remove(option)
      }
      evaluate()
    }
  }

  private class SearchHistoryButton constructor(action: AnAction, focusable: Boolean) :
    ActionButton(action, action.templatePresentation.clone(), ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {

    override fun getDataContext(): DataContext {
      return DataManager.getInstance().getDataContext(this)
    }

    override fun getPopState(): Int {
      return if (isSelected) SELECTED else super.getPopState()
    }

    override fun getIcon(): Icon {
      if (isEnabled && isSelected) {
        val selectedIcon = myPresentation.selectedIcon
        if (selectedIcon != null) return selectedIcon
      }
      return super.getIcon()
    }

    init {
      setLook(ActionButtonLook.INPLACE_LOOK)
      isFocusable = focusable
      updateIcon()
    }
  }

  private fun getExpressionHistory(): List<String> {
    return PropertiesComponent.getInstance().getValue(JSON_PATH_EVALUATE_HISTORY)?.split('\n') ?: emptyList()
  }

  private fun setExpressionHistory(history: Collection<String>) {
    PropertiesComponent.getInstance().setValue(JSON_PATH_EVALUATE_HISTORY, history.joinToString("\n"))
  }

  private fun addJSONPathToHistory(path: String) {
    if (path.isBlank()) return

    val history = ArrayDeque(getExpressionHistory())
    if (!history.contains(path)) {
      history.addFirst(path)
      if (history.size > 10) {
        history.removeLast()
      }
      setExpressionHistory(history)
    }
    else {
      if (history.firstOrNull() == path) {
        return
      }
      history.remove(path)
      history.addFirst(path)
      setExpressionHistory(history)
    }
  }

  private inner class ShowHistoryAction : DumbAwareAction(FindBundle.message("find.search.history"), null,
                                                          AllIcons.Actions.SearchWithHistory) {
    private val popupState: PopupState<JBPopup?> = PopupState.forPopup()

    override fun actionPerformed(e: AnActionEvent) {
      if (popupState.isRecentlyHidden) return

      val historyList = JBList(getExpressionHistory())
      showCompletionPopup(searchWrapper, historyList, searchTextField, popupState)
    }

    init {
      registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts("ShowSearchHistory"), searchTextField)
    }

    private fun showCompletionPopup(toolbarComponent: JComponent?,
                                    list: JList<String>,
                                    textField: EditorTextField,
                                    popupState: PopupState<JBPopup?>) {
      val builder: PopupChooserBuilder<*> = JBPopupFactory.getInstance().createListPopupBuilder(list)
      val popup = builder
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChoosenCallback(Runnable {
          val selectedValue = list.selectedValue
          if (selectedValue != null) {
            textField.text = selectedValue
            IdeFocusManager.getGlobalInstance().requestFocus(textField, false)
          }
        })
        .createPopup()

      popupState.prepareToShow(popup)
      if (toolbarComponent != null) {
        popup.showUnderneathOf(toolbarComponent)
      }
      else {
        popup.showUnderneathOf(textField)
      }
    }
  }
}
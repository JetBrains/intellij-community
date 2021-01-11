// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath.ui

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.icons.AllIcons
import com.intellij.json.JsonBundle
import com.intellij.json.json5.Json5FileType
import com.intellij.jsonpath.JsonPathFileType
import com.intellij.jsonpath.ui.JsonPathEvaluateManager.Companion.EVALUATE_TOOLWINDOW_ID
import com.intellij.jsonpath.ui.JsonPathEvaluateManager.Companion.JSON_PATH_EVALUATE_EXPRESSION_KEY
import com.intellij.jsonpath.ui.JsonPathEvaluateManager.Companion.JSON_PATH_EVALUATE_RESULT_KEY
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.serialization.ClassUtil.isPrimitive
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.tabs.impl.SingleHeightTabs
import com.intellij.util.castSafelyTo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jayway.jsonpath.*
import com.jayway.jsonpath.Configuration.ConfigurationBuilder
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import javax.swing.FocusManager
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

internal class JsonPathEvaluateView(private val project: Project) : SimpleToolWindowPanel(false, true), Disposable, DataProvider {

  private val searchTextField: EditorTextField = object : EditorTextField(project, JsonPathFileType.INSTANCE) {
    init {
      border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 0, 1)
    }

    override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
      super.setBounds(x - 1, y, width + 2, height)
    }

    override fun processKeyBinding(ks: KeyStroke?, e: KeyEvent?, condition: Int, pressed: Boolean): Boolean {
      if (e?.keyCode == KeyEvent.VK_ENTER && pressed) {
        evaluate()
        return true
      }
      return super.processKeyBinding(ks, e, condition, pressed)
    }

    override fun createEditor(): EditorEx {
      val editor = super.createEditor()
      editor.scrollPane.border = JBUI.Borders.empty()
      editor.component.border = JBUI.Borders.empty(3)
      editor.component.background = UIUtil.getInactiveTextFieldBackgroundColor()
      editor.component.isOpaque = true
      editor.putUserData(JSON_PATH_EVALUATE_EXPRESSION_KEY, true)

      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
      psiFile?.putUserData(JSON_PATH_EVALUATE_EXPRESSION_KEY, true)

      return editor
    }
  }

  private val sourceEditor: Editor
  private val resultEditor: Editor

  private val resultWrapper: JBPanelWithEmptyText = JBPanelWithEmptyText(BorderLayout())
  private val errorOutputArea: JBTextArea = JBTextArea()

  private val evalOptions: MutableSet<Option> = mutableSetOf()

  init {
    sourceEditor = initJsonEditor("source.json", false, EditorKind.UNTYPED)
    resultEditor = initJsonEditor("result.json", true, EditorKind.PREVIEW)

    resultEditor.putUserData(JSON_PATH_EVALUATE_RESULT_KEY, true)
    resultEditor.castSafelyTo<EditorEx>()?.apply {
      scrollPane.border = JBUI.Borders.empty()
    }

    val sourcePanel = BorderLayoutPanel()

    val buttonsGroup = createOptionsGroup()

    val filterToolbar = ActionManager.getInstance().createActionToolbar("JsonPathEvaluateToolbar", buttonsGroup, true)
    filterToolbar.setTargetComponent(this)
    filterToolbar.setReservePlaceAutoPopupIcon(false)

    val filtersWrapper = BorderLayoutPanel().apply {
      border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
      withPreferredHeight(SingleHeightTabs.UNSCALED_PREF_HEIGHT)

      addToCenter(searchTextField)
      addToRight(filterToolbar.component)
    }

    sourcePanel.addToTop(filtersWrapper)
    sourcePanel.addToCenter(sourceEditor.component)

    resultWrapper.emptyText.text = JsonBundle.message("jsonpath.evaluate.no.result")
    errorOutputArea.isEditable = false
    errorOutputArea.border = JBUI.Borders.empty(10)

    val splitter = OnePixelSplitter(0.5f)
    splitter.firstComponent = sourcePanel
    splitter.secondComponent = resultWrapper

    setContent(splitter)

    setExpression("$..*")
    setSource("{\n\n}")

    val messageBusConnection = project.messageBus.connect(this)
    messageBusConnection.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun stateChanged(toolWindowManager: ToolWindowManager) {
        val toolWindow = toolWindowManager.getToolWindow(EVALUATE_TOOLWINDOW_ID)
        if (toolWindow != null) {
          splitter.orientation = !toolWindow.anchor.isHorizontal
        }
      }
    })
  }

  private fun createOptionsGroup(): ActionGroup {
    val buttonsGroup = DefaultActionGroup()
    buttonsGroup.add(DefaultActionGroup(JsonBundle.message("jsonpath.evaluate.options"), true).apply {
      templatePresentation.icon = AllIcons.General.GearPlain

      add(OptionToggleAction(Option.AS_PATH_LIST, JsonBundle.message("jsonpath.evaluate.output.paths")))
      add(OptionToggleAction(Option.SUPPRESS_EXCEPTIONS, JsonBundle.message("jsonpath.evaluate.suppress.exceptions")))
      add(OptionToggleAction(Option.ALWAYS_RETURN_LIST, JsonBundle.message("jsonpath.evaluate.return.list")))
      add(OptionToggleAction(Option.DEFAULT_PATH_LEAF_TO_NULL, JsonBundle.message("jsonpath.evaluate.nullize.missing.leaf")))
      add(OptionToggleAction(Option.REQUIRE_PROPERTIES, JsonBundle.message("jsonpath.evaluate.require.all.properties")))
    })

    return buttonsGroup
  }

  override fun processKeyBinding(ks: KeyStroke?, e: KeyEvent?, condition: Int, pressed: Boolean): Boolean {
    if (pressed && e?.keyCode == KeyEvent.VK_ESCAPE) {
      val focusOwner = FocusManager.getCurrentManager().focusOwner

      if (SwingUtilities.isDescendingFrom(focusOwner, sourceEditor.component)) {
        searchTextField.requestFocus()
        return true
      }
    }
    return super.processKeyBinding(ks, e, condition, pressed)
  }

  private fun initJsonEditor(fileName: String, isViewer: Boolean, kind: EditorKind): Editor {
    val sourceVirtualFile = LightVirtualFile(fileName, Json5FileType.INSTANCE, "")
    val sourceFile = PsiManager.getInstance(project).findFile(sourceVirtualFile)!!
    val document = PsiDocumentManager.getInstance(project).getDocument(sourceFile)!!

    return EditorFactory.getInstance().createEditor(document, project, sourceVirtualFile, isViewer, kind)
  }

  fun setExpression(jsonPathExpr: String) {
    searchTextField.text = jsonPathExpr
  }

  fun setSource(json: String) {
    WriteAction.run<Throwable> {
      sourceEditor.document.setText(json)
    }
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
      resultWrapper.add(resultEditor.component, BorderLayout.CENTER)
      resultWrapper.invalidate()
      resultEditor.component.invalidate()
      resultWrapper.repaint()
      resultEditor.component.repaint()
    }

    resultEditor.caretModel.moveToOffset(0)
  }

  private fun setError(error: String) {
    errorOutputArea.text = error

    if (!resultWrapper.components.contains(errorOutputArea)) {
      resultWrapper.removeAll()
      resultWrapper.add(errorOutputArea, BorderLayout.CENTER)
      resultWrapper.invalidate()
      errorOutputArea.invalidate()
      resultWrapper.repaint()
      errorOutputArea.repaint()
    }
  }

  private fun evaluate() {
    val jsonPath: JsonPath = try {
      val expression = searchTextField.text
      if (expression.isBlank()) return
      JsonPath.compile(expression)
    }
    catch (ip: InvalidPathException) {
      setError(ip.localizedMessage)
      return
    }

    val config = ConfigurationBuilder()
      .options(evalOptions)
      .build()

    val jsonDocument: DocumentContext = try {
      val json = sourceEditor.document.text
      JsonPath.parse(json, config)
    }
    catch (e: IllegalArgumentException) {
      setError(e.localizedMessage)
      return
    }
    catch (ej: InvalidJsonException) {
      setError(ej.localizedMessage)
      return
    }

    val result = try {
      jsonDocument.read<Any>(jsonPath)
    }
    catch (pe: PathNotFoundException) {
      setError(pe.localizedMessage)
      return
    }
    catch (jpe: JsonPathException) {
      setError(jpe.localizedMessage)
      return
    }
    catch (ise: IllegalStateException) {
      setError(ise.localizedMessage)
      return
    }

    setResult(toResultString(config, result))
  }

  private fun toResultString(config: Configuration, result: Any?): String {
    if (result == null) return "null"
    if (result is String) return "\"" + StringUtil.escapeStringCharacters(result) + "\""

    if (isPrimitive(result.javaClass)) {
      return result.toString()
    }

    if (result is Collection<*>) {
      // .keys() result is Set<String>
      return "[" + result.joinToString(", ") {
        toResultString(config, it)
      } + "]"
    }

    return config.jsonProvider().toJson(result) ?: ""
  }

  override fun getData(dataId: String): Any? {
    if (JsonPathEvaluateManager.JSON_PATH_EVALUATE_SOURCE_KEY.`is`(dataId)) {
      return PsiDocumentManager.getInstance(project).getPsiFile(sourceEditor.document)
    }
    return super.getData(dataId)
  }

  override fun dispose() {
    val editorFactory = EditorFactory.getInstance()
    editorFactory.releaseEditor(sourceEditor)
    editorFactory.releaseEditor(resultEditor)
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
}
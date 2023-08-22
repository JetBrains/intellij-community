// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.project

import com.intellij.CommonBundle
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionProfileWrapper
import com.intellij.codeInspection.ex.InspectionToolsSupplier
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.configurationStore.runInAllowSaveMode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LineExtensionInfo
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModulePointerManager
import com.intellij.openapi.module.impl.ModulePointerManagerImpl
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.*
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlStringUtil
import java.awt.Color
import java.awt.Font
import javax.swing.Action
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

internal class ConvertModuleGroupsToQualifiedNamesDialog(val project: Project) : DialogWrapper(project) {
  private val editorArea: EditorTextField
  private val document: Document
    get() = editorArea.document
  private lateinit var modules: List<Module>
  private val recordPreviousNamesCheckBox: JCheckBox
  private var modified = false

  init {
    title = ProjectBundle.message("convert.module.groups.dialog.title")
    isModal = false
    setOKButtonText(ProjectBundle.message("convert.module.groups.button.text"))
    editorArea = EditorTextFieldProvider.getInstance().getEditorField(PlainTextLanguage.INSTANCE, project, listOf(EditorCustomization {
      it.settings.apply {
        isLineNumbersShown = false
        isLineMarkerAreaShown = false
        isFoldingOutlineShown = false
        isRightMarginShown = false
        additionalLinesCount = 0
        additionalColumnsCount = 0
        isAdditionalPageAtBottom = false
        isShowIntentionBulb = false
      }
      (it as? EditorImpl)?.registerLineExtensionPainter(this::generateLineExtension)
      setupHighlighting(it)
    }, MonospaceEditorCustomization.getInstance()))
    document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        modified = true
      }
    }, disposable)
    recordPreviousNamesCheckBox = JCheckBox(ProjectBundle.message("convert.module.groups.record.previous.names.text"), true)
    importRenamingScheme(emptyMap())
    init()
  }

  override fun show() {
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
    InspectionProfileWrapper.runWithCustomInspectionWrapper(psiFile!!, { customize() }) {
      super.show()
    }
  }

  private fun setupHighlighting(editor: Editor) {
    editor.putUserData(IntentionManager.SHOW_INTENTION_OPTIONS_KEY, false)
  }

  private fun customize(): InspectionProfileWrapper {
    val inspections = InspectionToolsSupplier.Simple(listOf(LocalInspectionToolWrapper(ModuleNamesListInspection())))
    val profile = InspectionProfileImpl("Module names", inspections, null)
    for (spellCheckingToolName in SpellCheckingEditorCustomizationProvider.getInstance().spellCheckingToolNames) {
      profile.getToolsOrNull(spellCheckingToolName, project)?.isEnabled = false
    }
    return InspectionProfileWrapper(profile)
  }

  override fun createCenterPanel(): JPanel {
    val text = XmlStringUtil.wrapInHtml(ProjectBundle.message("convert.module.groups.description.text"))
    val recordPreviousNames = com.intellij.util.ui.UI.PanelFactory.panel(recordPreviousNamesCheckBox)
      .withTooltip(ProjectBundle.message("convert.module.groups.record.previous.names.tooltip",
                                         ApplicationNamesInfo.getInstance().fullProductName)).createPanel()
    return JBUI.Panels.simplePanel(0, UIUtil.DEFAULT_VGAP)
      .addToCenter(editorArea)
      .addToTop(JBLabel(text))
      .addToBottom(recordPreviousNames)
  }

  override fun getPreferredFocusedComponent(): JComponent = editorArea.focusTarget

  private fun generateLineExtension(line: Int): Collection<LineExtensionInfo> {
    val lineText = document.charsSequence.subSequence(document.getLineStartOffset(line), document.getLineEndOffset(line)).toString()
    if (line !in modules.indices || modules[line].name == lineText) return emptyList()

    val name = LineExtensionInfo(" <- ${modules[line].name}", JBColor.GRAY, null, null, Font.PLAIN)
    val groupPath = ModuleManager.getInstance(project).getModuleGroupPath(modules[line])
    if (groupPath == null) {
      return listOf(name)
    }
    @NlsSafe val pathString = groupPath.joinToString(separator = "/", prefix = " (", postfix = ")")
    val group = LineExtensionInfo(pathString, Color.GRAY, null, null, Font.PLAIN)
    return listOf(name, group)
  }

  fun importRenamingScheme(renamingScheme: Map<String, String>) {
    val moduleManager = ModuleManager.getInstance(project)
    fun getDefaultName(module: Module) = (moduleManager.getModuleGroupPath(module)?.let { it.joinToString(".") + "." } ?: "") + module.name
    val names = moduleManager.modules.associateBy({ it }, { renamingScheme.getOrElse(it.name, { getDefaultName(it) }) })
    modules = moduleManager.modules.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, { names[it]!! }))
    runWriteAction {
      document.setText(modules.joinToString("\n") { names[it]!! })
    }
    modified = false
  }

  fun getRenamingScheme(): Map<String, String> {
    val lines = document.charsSequence.split('\n')

    return modules.withIndex().filter { lines[it.index] != it.value.name }.associateByTo(LinkedHashMap(), { it.value.name }, {
      if (it.index in lines.indices) lines[it.index] else it.value.name
    })
  }

  override fun doCancelAction() {
    if (modified) {
      val answer = Messages.showYesNoCancelDialog(project,
                                                  ProjectBundle.message("convert.module.groups.do.you.want.to.save.scheme"),
                                                  ProjectBundle.message("convert.module.groups.dialog.title"), null)
      when (answer) {
        Messages.CANCEL -> return
        Messages.YES -> {
          if (!saveModuleRenamingScheme(this)) {
            return
          }
        }
      }
    }

    super.doCancelAction()
  }

  override fun doOKAction() {
    ModuleNamesListInspection.checkModuleNames(document.charsSequence.lines(), project) { line, message ->
      Messages.showErrorDialog(project,
                               ProjectBundle.message("convert.module.groups.error.at.text", line + 1, StringUtil.decapitalize(message)),
                               CommonBundle.getErrorTitle())
      return
    }

    val renamingScheme = getRenamingScheme()
    if (renamingScheme.isNotEmpty()) {
      val model = ModuleManager.getInstance(project).getModifiableModel()
      val byName = modules.associateBy { it.name }
      for (entry in renamingScheme) {
        model.renameModule(byName[entry.key]!!, entry.value)
      }
      modules.forEach {
        model.setModuleGroupPath(it, null)
      }

      runInAllowSaveMode(isSaveAllowed = false) {
        runWriteAction {
          model.commit()
        }
        if (recordPreviousNamesCheckBox.isSelected) {
          (ModulePointerManager.getInstance(project) as ModulePointerManagerImpl).setRenamingScheme(renamingScheme)
        }
      }
      project.save()
    }

    super.doOKAction()
  }

  override fun createActions(): Array<Action> {
    return arrayOf(okAction, SaveModuleRenamingSchemeAction(this) { modified = false },
                   LoadModuleRenamingSchemeAction(this), cancelAction)
  }
}

internal class ConvertModuleGroupsToQualifiedNamesAction : DumbAwareAction(ProjectBundle.message("convert.module.groups.action.text"),
                                                                  ProjectBundle.message("convert.module.groups.action.description"), null) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ConvertModuleGroupsToQualifiedNamesDialog(project).show()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null && ModuleManager.getInstance(e.project!!).hasModuleGroups()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}


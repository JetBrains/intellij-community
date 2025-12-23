// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileTextField
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.*
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.text.nullize
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingUndoUtil
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.nio.file.Path
import javax.swing.*

/**
 * @author gregsh
 */
internal class ShowUpdateInfoDialogAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val dialog = MyDialog(project)
    if (dialog.showAndGet()) {
      try {
        UpdateChecker.testPlatformUpdate(
          project,
          dialog.updateXmlText(),
          dialog.patchFilePath()?.let { Path.of(FileUtil.toSystemDependentName(it)) },
          dialog.forceUpdate(),
        )
      }
      catch (ex: Exception) {
        Messages.showErrorDialog(project, "${ex.javaClass.name}: ${ex.message}", "Something Went Wrong")
      }
    }
  }

  private class MyDialog(private val project: Project?) : DialogWrapper(project, true) {
    private lateinit var textArea: JTextArea
    private lateinit var fileField: FileTextField
    private var forceUpdate = false

    init {
      @Suppress("DialogTitleCapitalization")
      title = "Test update dialog"
      init()
    }

    override fun createCenterPanel(): JComponent {
      textArea = JTextArea(30, 80)
      SwingUndoUtil.addUndoRedoActions(textArea)
      textArea.wrapStyleWord = true
      textArea.lineWrap = true

      fileField = FileChooserFactory.getInstance().createFileTextField(FileChooserDescriptorFactory.singleFile(), disposable)
      val fileCombo = TextFieldWithBrowseButton(fileField.field)
      fileCombo.addBrowseFolderListener(project, FileChooserDescriptorFactory.singleFile().withTitle("Patch File").withDescription("Patch file"))

      val panel = JPanel(BorderLayout(0, JBUI.scale(10)))

      val labelsPanel = JPanel(BorderLayout(0, JBUI.scale(5)))
      labelsPanel.add(
        JLabel("Add updates.xml content or choose a patch file").apply { font = JBUI.Fonts.label().asBold() },
        BorderLayout.NORTH,
      )
      labelsPanel.add(JLabel("Updates.xml <channel> text:"), BorderLayout.SOUTH)
      panel.add(labelsPanel, BorderLayout.NORTH)
      panel.add(ScrollPaneFactory.createScrollPane(textArea), BorderLayout.CENTER)
      panel.add(LabeledComponent.create(fileCombo, "Patch file:"), BorderLayout.SOUTH)
      panel.minimumSize = JBUI.size(200, 200)
      return panel
    }

    override fun createActions() = arrayOf(
      object : AbstractAction("&Check Updates") {
        override fun actionPerformed(e: ActionEvent?) {
          forceUpdate = false
          validateAndDoOkAction()
        }
      },
      object : AbstractAction("&Show Dialog") {
        override fun actionPerformed(e: ActionEvent?) {
          forceUpdate = true
          validateAndDoOkAction()
        }
      },
      cancelAction)

    private fun validateAndDoOkAction() {
      val info = doValidate()
      if (info != null) {
        IdeFocusManager.getInstance(null).requestFocus(textArea, true)
        updateErrorInfo(listOf(info))
        startTrackingValidation()
      }
      else {
        doOKAction()
      }
    }

    override fun doValidate(): ValidationInfo? {
      val text = getXmlText()
      if (text.isEmpty()) {
        return ValidationInfo("Please paste something here or choose a patch file", textArea)
      }

      try {
        JDOMUtil.load(completeUpdateInfoXml(text))
      }
      catch (e: Exception) {
        return ValidationInfo(e.message ?: "Error: ${e.javaClass.name}", textArea)
      }

      return super.doValidate()
    }

    override fun getPreferredFocusedComponent() = textArea
    override fun getDimensionServiceKey() = "TEST_UPDATE_INFO_DIALOG"

    fun updateXmlText() = completeUpdateInfoXml(getXmlText())
    fun forceUpdate() = forceUpdate
    fun patchFilePath() = fileField.field.text.nullize(nullizeSpaces = true)

    private fun completeUpdateInfoXml(text: String) =
      when (JDOMUtil.load(text).name) {
        "products" -> text
        "channel" -> {
          val productName = ApplicationNamesInfo.getInstance().fullProductName
          val productCode = ApplicationInfo.getInstance().build.productCode
          """<products><product name="${productName}"><code>${productCode}</code>${text}</product></products>"""
        }
        else -> throw IllegalArgumentException("Unknown root element")
      }

    private fun getXmlText(): String {
      val text = textArea.text?.trim()
      if (text?.isNotBlank() == true) return text

      val patchFile = patchFilePath()
      if (patchFile?.isNotBlank() == true) return xmlTextForPatchUpdate(patchFile)

      return ""
    }

    private fun xmlTextForPatchUpdate(path: String) = """
      <channel id="">
        <build number="1" version="fake version">
          <message><![CDATA[Test text for the update dialog<br><br>Selected patch path:<br>$path]]></message>
        </build>
      </channel>""".trimIndent()
  }
}

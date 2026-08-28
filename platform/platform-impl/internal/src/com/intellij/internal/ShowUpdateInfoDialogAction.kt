// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.BundleBase
import com.intellij.CommonBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileTextField
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.updateSettings.impl.PlatformUpdateDialog
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.builder.text
import com.intellij.ui.layout.selected
import com.intellij.util.containers.nullize
import com.intellij.util.text.nullize
import com.intellij.util.ui.SwingUndoUtil
import java.awt.event.ActionEvent
import java.nio.file.Path
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JTextArea

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
        when (dialog.mode) {
          MyDialog.Mode.GENERAL ->
            UpdateChecker.testPlatformUpdate(
              project,
              dialog.updateXmlText(),
              dialog.patchFilePath()?.let { Path.of(FileUtil.toSystemDependentName(it)) },
              dialog.forceUpdate,
            )

          MyDialog.Mode.QUICK_MOCK -> dialog.quickMockExecute(project)
        }
      }
      catch (ex: Exception) {
        Messages.showErrorDialog(project, "${ex.javaClass.name}: ${ex.message}", "Something Went Wrong")
      }
    }
  }

  private class MyDialog(private val project: Project?) : DialogWrapper(project, true) {

    enum class Mode {
      GENERAL,
      QUICK_MOCK
    }

    val mode: Mode
      get() = if (::tabbedPane.isInitialized) Mode.entries[tabbedPane.selectedIndex] else Mode.GENERAL

    var forceUpdate = false
      private set

    private lateinit var tabbedPane: JBTabbedPane
    private lateinit var textArea: JTextArea
    private lateinit var fileField: FileTextField
    private lateinit var rbShowDialog: JBRadioButton
    private lateinit var quickMock: QuickMock
    private val okAction = object : AbstractAction(CommonBundle.getOkButtonText()) {

      init {
        putValue(DEFAULT_ACTION, true)
      }

      override fun actionPerformed(e: ActionEvent?) {
        when (mode) {
          Mode.GENERAL -> {
            forceUpdate = rbShowDialog.isSelected
            validateAndDoOkAction()
          }
          Mode.QUICK_MOCK -> doOKAction()
        }
      }
    }

    init {
      @Suppress("DialogTitleCapitalization")
      title = "Test update dialog"
      init()
    }

    override fun createCenterPanel(): JComponent {
      quickMock = QuickMock()
      tabbedPane = JBTabbedPane()

      tabbedPane.addTab("General", createGeneral())
      tabbedPane.addTab("Quick Mock", quickMock.panel)

      return tabbedPane
    }

    private fun createGeneral(): DialogPanel {
      fileField = FileChooserFactory.getInstance().createFileTextField(FileChooserDescriptorFactory.singleFile(), disposable)
      val fileCombo = TextFieldWithBrowseButton(fileField.field)
      fileCombo.addBrowseFolderListener(project,
                                        FileChooserDescriptorFactory.singleFile().withTitle("Patch File").withDescription("Patch file"))

      return panel {
        row {
          label("Add updates.xml content or choose a patch file").bold()
        }

        row {
          textArea = textArea().applyToComponent {
            SwingUndoUtil.addUndoRedoActions(this)
            wrapStyleWord = true
            lineWrap = true
            rows = 30
            columns = 80
          }.label("Updates.xml <channel> text:", LabelPosition.TOP)
            .align(Align.FILL)
            .component
        }.resizableRow()

        row {
          cell(fileCombo)
            .label("Patch file:")
            .align(AlignX.FILL)
        }

        buttonsGroup("Action:") {
          row {
            radioButton(BundleBase.replaceMnemonicAmpersand("&Check updates")!!)
              .selected(true)
          }
          row {
            rbShowDialog = radioButton(BundleBase.replaceMnemonicAmpersand("&Show dialog")!!)
              .component
          }
        }
      }
    }

    override fun getOKAction(): Action = okAction

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
      if (mode != Mode.GENERAL) {
        return null
      }

      val text = getXmlText()
      if (text.isEmpty()) {
        return ValidationInfo("Please paste something here or choose a patch file", textArea).withOKEnabled()
      }

      try {
        JDOMUtil.load(completeUpdateInfoXml(text))
      }
      catch (e: Exception) {
        return ValidationInfo(e.message ?: "Error: ${e.javaClass.name}", textArea).withOKEnabled()
      }

      return super.doValidate()
    }

    override fun getPreferredFocusedComponent() = textArea
    override fun getDimensionServiceKey() = "TEST_UPDATE_INFO_DIALOG"

    fun updateXmlText() = completeUpdateInfoXml(getXmlText())
    fun patchFilePath() = fileField.field.text.nullize(nullizeSpaces = true)
    fun quickMockExecute(project: Project) {
      quickMock.execute(project)
    }

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

private class QuickMock {

  private lateinit var textArea: JBTextArea
  private lateinit var chAddConfigLink: JBCheckBox
  private lateinit var chWriteProtected: JBCheckBox
  private lateinit var tfIncompatiblePlugins: JBTextField
  private lateinit var chLicenseNoteWarning: JBCheckBox
  private lateinit var tfLicenseNote: JBTextField

  val panel = panel {
    row {
      textArea = textArea()
        .align(Align.FILL)
        .applyToComponent {
          text = QUICK_MODE_XML
        }.component
    }
    row {
      chWriteProtected = checkBox("Write protected")
        .selected(true)
        .component
    }
    row {
      chAddConfigLink = checkBox("Add config link")
        .selected(true)
        .component
    }
    row {
      val checkBox = checkBox("Incompatible plugins:")
        .gap(RightGap.SMALL)
        .selected(true)
        .component

      tfIncompatiblePlugins = textField()
        .align(AlignX.FILL)
        .text("Classic UI, A very incompatible plugin")
        .comment("Not shown when write protected")
        .enabledIf(checkBox.selected)
        .component
    }
    row("License note:") {
      tfLicenseNote = textField()
        .text("The new version has an expiration date and does not require a license")
        .resizableColumn()
        .align(AlignX.FILL)
        .component
      chLicenseNoteWarning = checkBox("Warning")
        .selected(true)
        .component
    }
  }

  fun execute(project: Project) {
    val loaded = UpdateChecker.testLoadFromXml(textArea.text)
    val incompatiblePluginsText = (if (tfIncompatiblePlugins.isEnabled) tfIncompatiblePlugins.text else null) ?: ""
    val incompatiblePlugins = incompatiblePluginsText
      .split(",")
      .map { it.trim() }
      .filter { it.isNotBlank() }
      .nullize()

    PlatformUpdateDialog.createTestDialog(project, loaded,
                                          chWriteProtected.isSelected,
                                          tfLicenseNote.text.trim().nullize(), chLicenseNoteWarning.isSelected,
                                          chAddConfigLink.isSelected,
                                          null, incompatiblePlugins)
      .show()
  }
}

private const val QUICK_MODE_XML = """
<products>
  <product name="IntelliJ IDEA">
    <code>IU</code>
    <channel id="IU-RELEASE-licensing-RELEASE" name="IntelliJ IDEA RELEASE" status="release" url="https://www.jetbrains.com/idea/download" feedback="https://youtrack.jetbrains.com/issues/IDEA" majorVersion="2027" licensing="release">
      <build number="371.9999" version="2037.1.1" releaseDate="20360101" fullNumber="371.9999.123">
        <blogPost url="https://blog.jetbrains.com/idea/2026/08/intellij-idea-2026-2-1/"/>
        <message><![CDATA[<p>IntelliJ IDEA 2037.1.1 is out with the following improvements:</p>
<ul>
 <li>Markdown shell scripts now execute in the correct order. [<a href="https://youtrack.jetbrains.com/issue/IJPL-92206/">IJPL-92206</a>]</li>
 <li>Undo now works correctly after applying <em>Optimize imports on the fly</em>. [<a href="https://youtrack.jetbrains.com/issue/IDEA-285011/">IDEA-285011</a>]</li>
 <li>Dragging a terminal tab after using the <em>Move to Editor</em> action no longer restarts the terminal session. [<a href="https://youtrack.jetbrains.com/issue/IJPL-165734/Terminal-restarts-when-dragged-after-Move-to-Editor">IJPL-165734</a>]</li>
</ul>
<p>Get more details in our <a href="https://blog.jetbrains.com/idea/2026/08/intellij-idea-2026-2-1/">blog post</a>.</p>]]></message>
        <button name="Download" url="https://www.jetbrains.com/idea/download" download="true"/>
        <button name="Release Notes" url="https://youtrack.jetbrains.com/articles/IDEA-A-2100662729"/>
        <button name="More Information" url="https://blog.jetbrains.com/idea/2026/08/intellij-idea-2026-2-1/"/>
        <patch from="262.9437" size="from 15 to 17" fullFrom="262.9437.65"/>
        <patch from="261.26222" size="from 618 to 666" fullFrom="261.26222.65"/>
        <patch from="262.8665" size="from 130 to 163" fullFrom="262.8665.337"/>
      </build>
    </channel>
  </product>
</products>
"""

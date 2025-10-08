package com.intellij.platform.ide.nonModalWelcomeScreen.newFileDialog

import com.intellij.ide.util.DirectoryUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.impl.FileChooserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.IncorrectOperationException
import com.intellij.util.PathUtilRt
import com.intellij.util.asSafely
import com.intellij.util.ui.FormBuilder
import org.jetbrains.annotations.ApiStatus
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.event.DocumentEvent

@ApiStatus.Internal
class WelcomeScreenNewFileDialog private constructor(
  internal val project: Project,
  private val builder: Builder,
) : DialogWrapper(project, true) {

  private companion object {
    private const val MAX_PATH_LENGTH = 70
  }

  private val targetDirectoryField: ComponentWithBrowseButton<JBTextField> = ComponentWithBrowseButton(ExtendableTextField(), null)
  private val nameField = JBTextField()
  private val templateComboBox: ComboBox<WelcomeScreenNewFileTemplateOption>? = builder.templateOptions.takeIf { it.isNotEmpty() }?.let { ComboBox() }
  private var targetDirectory: PsiDirectory? = null
  private val fileExtension: String? = builder.fixedExtension

  init {
    title = builder.title
    init()

    with(targetDirectoryField) {
      setTextFieldPreferredWidth(MAX_PATH_LENGTH)

      val descriptor = FileChooserDescriptorFactory.singleDir()
        .withTitle(NonModalWelcomeScreenBundle.message("welcome.screen.create.file.dialog.select.target.directory"))
        .withDescription(NonModalWelcomeScreenBundle.message("welcome.screen.create.file.dialog.directory.chooser.description"))

      addBrowseFolderListener(project, descriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT)
    }

    targetDirectoryField.childComponent.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) = validateButtons()
    })

    if (builder.showNameField) {
      nameField.document.addDocumentListener(object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) = validateButtons()
      })

      nameField.addFocusListener(object : FocusAdapter() {
        override fun focusGained(e: FocusEvent) = nameField.selectAll()
      })
    }

    targetDirectoryField.childComponent.text = builder.defaultDirectory ?: ""
    nameField.text = builder.defaultFileName

    templateComboBox?.apply {
      builder.templateOptions.forEach { addItem(it) }

      renderer = object : SimpleListCellRenderer<WelcomeScreenNewFileTemplateOption>() {
        override fun customize(list: JList<out WelcomeScreenNewFileTemplateOption>, value: WelcomeScreenNewFileTemplateOption, index: Int, selected: Boolean, hasFocus: Boolean) {
          setText(value.displayName)
          value.icon?.let { setIcon(it) }
        }
      }
    }

    validateButtons()
    setOKButtonText(NonModalWelcomeScreenBundle.message("welcome.screen.create.file.dialog.create.button.label"))
  }

  override fun getPreferredFocusedComponent(): JComponent = if (builder.showNameField) nameField else targetDirectoryField

  override fun createCenterPanel(): JComponent = FormBuilder.createFormBuilder().apply {
    if (builder.showNameField) {
      addLabeledComponent(NonModalWelcomeScreenBundle.message("welcome.screen.create.file.dialog.create.file.name.label"), nameField)
    }
    templateComboBox?.let {
      addLabeledComponent(NonModalWelcomeScreenBundle.message("welcome.screen.create.file.dialog.create.file.template.label"), it)
    }
    addLabeledComponent(
      NonModalWelcomeScreenBundle.message("welcome.screen.create.file.dialog.create.file.directory.label"),
      targetDirectoryField
    )
  }.panel

  fun getFileName(): String {
    if (!builder.showNameField) {
      return builder.defaultFileName
    }

    val fileName = nameField.text.trim()

    return fileExtension?.let {
      if (fileName.endsWith(".$it")) fileName else "$fileName.$it"
    } ?: fileName
  }

  fun getTargetDirectory(): PsiDirectory? = targetDirectory

  fun getSelectedTemplateName(): String? = templateComboBox?.selectedItem?.asSafely<WelcomeScreenNewFileTemplateOption>()?.templateName

  private fun isValid(): Boolean {
    if (!builder.showNameField) {
      return targetDirectoryField.childComponent.text.isNotEmpty()
    }

    val newName = getFileName()
    return (targetDirectoryField.childComponent.text.isNotEmpty() && nameField.text.isNotEmpty() && PathUtilRt.isValidFileName(newName, false))
  }

  private fun validateButtons() {
    okAction.isEnabled = isValid()
  }

  override fun doOKAction() {
    val newName = getFileName()

    if (builder.showNameField && newName.isEmpty()) {
      Messages.showErrorDialog(
        project,
        NonModalWelcomeScreenBundle.message("welcome.screen.create.file.dialog.no.name.specified"),
        NonModalWelcomeScreenBundle.message("welcome.screen.create.file.dialog.error.title")
      )
      return
    }

    if (builder.showNameField && !PathUtilRt.isValidFileName(newName, false)) {
      Messages.showErrorDialog(
        nameField,
        NonModalWelcomeScreenBundle.message("welcome.screen.create.file.dialog.invalid.file.name")
      )
      return
    }

    val targetDirectoryName = targetDirectoryField.childComponent.text

    if (targetDirectoryName.isEmpty()) {
      Messages.showErrorDialog(
        project,
        NonModalWelcomeScreenBundle.message("welcome.screen.create.file.dialog.no.target.directory.specified"),
        NonModalWelcomeScreenBundle.message("welcome.screen.create.file.dialog.error.title")
      )
      return
    }

    CommandProcessor.getInstance().executeCommand(project, {
      ApplicationManager.getApplication().runWriteAction {
        try {
          targetDirectory = DirectoryUtil.mkdirs(
            PsiManager.getInstance(project), 
            Path.of(targetDirectoryName).normalize().toString()
          )
        }
        catch (_: IncorrectOperationException) {
        }
      }
    }, NonModalWelcomeScreenBundle.message("welcome.screen.create.file.dialog.create.directory"), null)

    val targetDirectory = targetDirectory

    if (targetDirectory == null) {
      Messages.showErrorDialog(
        project,
        NonModalWelcomeScreenBundle.message("welcome.screen.create.file.dialog.cannot.create.directory"),
        NonModalWelcomeScreenBundle.message("welcome.screen.create.file.dialog.error.title")
      )
      return
    }

    FileChooserUtil.setLastOpenedFile(project, targetDirectory.virtualFile.toNioPath())

    super.doOKAction()
  }

  class Builder(val project: Project, @NlsContexts.DialogTitle val title: String) {
    var showNameField: Boolean = true
    var fixedExtension: String? = null
    var defaultFileName: String = ""
    var defaultDirectory: String? = null
    var templateOptions: List<WelcomeScreenNewFileTemplateOption> = emptyList()

    fun build(): WelcomeScreenNewFileDialog = WelcomeScreenNewFileDialog(project, this)
  }
}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.settings.mappings

import com.intellij.icons.AllIcons
import com.intellij.json.JsonBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.FixedSizeButton
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.impl.DslComponentPropertyInternal
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import com.jetbrains.jsonSchema.widget.JsonSchemaInfoPopupStep
import java.awt.event.ActionListener
import java.util.function.BiConsumer
import javax.swing.JEditorPane
import javax.swing.event.DocumentEvent

internal class JsonSchemaMappingsViewUi(
  private val project: Project,
  private var schemaPathChangedCallback: BiConsumer<String, Boolean>,
  private val tableDecorator: ToolbarDecorator,
  disposable: Disposable,
) {

  @JvmField
  val schemaField: TextFieldWithBrowseButton = TextFieldWithBrowseButton(JBTextField())
  lateinit var schemaVersionComboBox: ComboBox<JsonSchemaVersion>
  private lateinit var errorRow: Row
  private lateinit var errorEditorPane: JEditorPane
  private var errorText: @NlsContexts.PopupContent String? = null

  @JvmField
  val panel = panel {
    row(JsonBundle.message("json.schema.file.selector.title")) {
      cell(schemaField)
        .align(AlignX.FILL)
        .resizableColumn()
        .gap(RightGap.SMALL)
      cell(createUrlButton())
    }
    row(JsonBundle.message("json.schema.version.selector.title")) {
      schemaVersionComboBox = comboBox(
        JsonSchemaVersion.entries,
        textListCellRenderer(JsonBundle.message("schema.unknown")) {
          JsonBundle.message("schema.of.version", it.presentableVersionSuffix)
        }).component
    }
    errorRow = row {
      icon(AllIcons.General.BalloonWarning)
        .gap(RightGap.SMALL)
      errorEditorPane = text(JsonBundle.message("json.schema.conflicting.mappings")) {
        val errorText = errorText ?: return@text

        val builder = JBPopupFactory.getInstance()
          .createHtmlTextBalloonBuilder(errorText, UIUtil.getBalloonWarningIcon(), MessageType.WARNING.getPopupBackground(), null)
        builder.setDisposable(disposable)
        builder.setHideOnClickOutside(true)
        builder.setCloseButtonEnabled(true)
        builder.createBalloon().showInCenterOf(errorEditorPane)
      }.component
    }
    row {
      cell(tableDecorator.createPanel())
        .align(Align.FILL)
        .comment(JsonBundle.message("path.to.file.or.directory.relative.to.project.root.or.file.name"),
                 maxLineLength = MAX_LINE_LENGTH_WORD_WRAP).apply {
          comment!!.putClientProperty(DslComponentPropertyInternal.PREFERRED_COLUMNS_LABEL_WORD_WRAP, 20)
        }
    }.resizableRow()
  }

  init {
    schemaField.setButtonIcon(AllIcons.General.OpenDiskHover)
    SwingHelper.installFileCompletionAndBrowseDialog(
      project, schemaField, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
        .withTitle(JsonBundle.message("json.schema.add.schema.chooser.title")))
    schemaField.textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        schemaPathChangedCallback.accept(schemaField.getText(), false)
      }
    })

    // Magic constant for alignment with paren panel
    panel.border = JBUI.Borders.empty(0, 10, 0, 13)
  }

  fun setError(text: @NlsContexts.PopupContent String?, showWarning: Boolean) {
    errorText = text
    errorRow.visible(showWarning && text != null)
  }

  private fun createUrlButton(): FixedSizeButton {
    val result = FixedSizeButton()
    result.setIcon(AllIcons.General.Web)
    result.addActionListener(ActionListener {
      val service = JsonSchemaService.Impl.get(project)
      val schemas = service.getAllUserVisibleSchemas()
      JBPopupFactory.getInstance().createListPopup(
        object : JsonSchemaInfoPopupStep(schemas, project, null, service,
                                         JsonBundle.message("schema.configuration.mapping.remote")) {
          override fun setMapping(selectedValue: JsonSchemaInfo?, virtualFile: VirtualFile?, project: Project) {
            if (selectedValue != null) {
              schemaField.text = selectedValue.getUrl(project)
              schemaPathChangedCallback.accept(selectedValue.getDescription(), true) // force updating name
            }
          }
        }).showInCenterOf(result)
    })

    return result
  }
}

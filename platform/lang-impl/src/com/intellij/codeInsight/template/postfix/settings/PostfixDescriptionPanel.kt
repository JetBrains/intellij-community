// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.settings

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.intention.impl.config.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.profile.codeInspection.ui.DescriptionEditorPane
import com.intellij.profile.codeInspection.ui.readHTML
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.IOException
import javax.swing.JPanel

internal class PostfixDescriptionPanel : Disposable {
  private lateinit var descriptionPane: DescriptionEditorPane
  private val afterPanel: JPanel = JPanel()
  private val beforePanel: JPanel = JPanel()

  val component: OnePixelSplitter = OnePixelSplitter(
    true,
    "PostfixDescriptionPanel.VERTICAL_DIVIDER_PROPORTION",
    0.25f
  )

  init {
    initializeExamplePanel(beforePanel)
    initializeExamplePanel(afterPanel)

    component.apply {
      minimumSize = Dimension(60, 60)

      firstComponent = panel {
        row {
          descriptionPane = cell(DescriptionEditorPane())
            .resizableColumn()
            .align(Align.FILL)
            .component
        }
      }

      val examplePanel = JPanel(GridBagLayout())
      val constraint = GridBag()
        .setDefaultInsets(UIUtil.LARGE_VGAP, 0, 0, 0)
        .setDefaultFill(GridBagConstraints.BOTH)
        .setDefaultWeightY(0.5)
        .setDefaultWeightX(1.0)
      examplePanel.add(panel {
        row {
          cell(beforePanel)
            .label(CodeInsightBundle.message("border.title.before"), LabelPosition.TOP)
            .resizableColumn()
            .align(Align.FILL)
        }
          .resizableRow()
      }.apply { minimumSize = Dimension(-1, 60) }, constraint.nextLine())
      examplePanel.add(panel {
        row {
          cell(afterPanel)
            .label(CodeInsightBundle.message("border.title.after"), LabelPosition.TOP)
            .resizableColumn()
            .align(Align.FILL)
        }
          .resizableRow()
      }.apply { minimumSize = Dimension(-1, 60) }, constraint.nextLine())

      secondComponent = examplePanel
    }
  }

  fun reset(actionMetaData: BeforeAfterMetaData) {
    val isEmpty = actionMetaData === PostfixTemplateMetaData.EMPTY_METADATA
    readHtml(actionMetaData, isEmpty)
    showUsages(beforePanel, when {
      isEmpty -> PlainTextDescriptor(CodeInsightBundle.message("templates.postfix.settings.category.before"), "before.txt.template")
      else -> actionMetaData.getExampleUsagesBefore().firstOrNull()
    })
    showUsages(afterPanel, when {
      isEmpty -> PlainTextDescriptor(CodeInsightBundle.message("templates.postfix.settings.category.after"), "after.txt.template")
      else -> actionMetaData.getExampleUsagesAfter().firstOrNull()
    })
  }

  private fun readHtml(actionMetaData: BeforeAfterMetaData, isEmpty: Boolean) {
    descriptionPane.readHTML(when {
      isEmpty -> CodeInsightBundle.message("templates.postfix.settings.category.text")
      else -> getDescription(actionMetaData.getDescription())
    })
  }

  private fun initializeExamplePanel(panel: JPanel) {
    panel.setLayout(BorderLayout())
    val actionUsagePanel = ActionUsagePanel()
    panel.add(actionUsagePanel)
    Disposer.register(this, actionUsagePanel)
  }

  override fun dispose() {}

  companion object {
    private val LOG = Logger.getInstance(IntentionDescriptionPanel::class.java)

    private fun getDescription(url: TextDescriptor): @Nls String {
      try {
        return url.getText()
      }
      catch (e: IOException) {
        LOG.error(e)
      }
      return ""
    }

    private fun showUsages(panel: JPanel, exampleUsage: TextDescriptor?) {
      var text = ""
      var fileType: FileType? = PlainTextFileType.INSTANCE
      if (exampleUsage != null) {
        try {
          text = exampleUsage.getText()
          val name = exampleUsage.getFileName()
          val fileTypeManager = FileTypeManagerEx.getInstanceEx()
          val extension = fileTypeManager.getExtension(name)
          fileType = fileTypeManager.getFileTypeByExtension(extension)
        }
        catch (e: IOException) {
          LOG.error(e)
        }
      }
      (panel.getComponent(0) as ActionUsagePanel).reset(text, fileType)
      panel.repaint()
    }
  }
}

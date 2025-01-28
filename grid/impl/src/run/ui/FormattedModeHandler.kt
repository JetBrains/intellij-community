package com.intellij.database.run.ui

import com.intellij.application.options.CodeStyle
import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.extractors.ObjectFormatterMode
import com.intellij.database.extractors.TextInfo
import com.intellij.database.extractors.toJson
import com.intellij.formatting.FormatTextRanges
import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.Language
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import com.intellij.psi.xml.XmlTokenType.XML_TAG_END
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.DocumentUtil
import com.intellij.util.containers.TreeTraversal

internal class FormattedModeHandler(
  private val grid: DataGrid,
  private val document: Document,
  private val project: Project,
  private val disableUpdateListener: (() -> Unit) -> Unit
) {
  private var enabled = false
  private var formatCache: FormatCache? = null

  init {
    if (!PropertiesComponent.getInstance().getBoolean(FORMATTED_MODE_DISABLED)) {
      enabled = true
    }
  }

  fun isEnabled() = enabled
  fun setEnabled(v: Boolean) {
    PropertiesComponent.getInstance().setValue(FORMATTED_MODE_DISABLED, !v)
    enabled = v
  }

  fun supportsCurrentValue(): Boolean {
    val file = FileDocumentManager.getInstance().getFile(document) as? LightVirtualFile ?: return false
    val value = grid.getSelectedValue()
    val stringValue = value as? String ?: (value as? TextInfo)?.text

    return (value is Map<*, *> ||
            value is List<*> ||
            stringValue is String && !StringUtil.containsLineBreak(stringValue.trim())) &&
           (isJsonDialect(file.language) ||
            file.language is XMLLanguage ||
            file.language.id == "MongoJS")
  }

  fun reformat() {
    if (!enabled || !supportsCurrentValue()) return
    DocumentUtil.writeInRunUndoTransparentAction {
      PsiDocumentManager.getInstance(project).commitDocument(document)
      val value = grid.getSelectedValue()
      val format = detectFormat(grid, value, project, document) ?: return@writeInRunUndoTransparentAction
      formatCache = FormatCache(format)
      format.reformat(disableUpdateListener)
    }
  }

  fun minimize(file: PsiFile): String {
    val format = formatCache?.get()
    return if (format == null || !enabled || !supportsCurrentValue()) file.text
    else format.restore(file)
  }

  companion object {
    const val FORMATTED_MODE_DISABLED = "EditMaximizedView.FORMATTED_MODE_DISABLED"
  }

  private fun isJsonDialect(language: Language): Boolean {
    return language.id == "JSON" || language.id == "JSON5"
  }

  private inner class FormatCache(val format: MinimizedFormat) {
    val row: ModelIndex<GridRow> = grid.selectionModel.leadSelectionRow
    val column: ModelIndex<GridColumn> = grid.selectionModel.leadSelectionColumn

    fun get(): MinimizedFormat? {
      return if (row == grid.selectionModel.leadSelectionRow && column == grid.selectionModel.leadSelectionColumn) format
      else null
    }
  }
}

interface MinimizedFormat {
  fun restore(file: PsiFile): String
  fun reformat(disableUpdateListener: (() -> Unit) -> Unit)
}

private class XmlMinimizedFormat(private val project: Project, private val document: Document) : MinimizedFormat {
  override fun restore(file: PsiFile): String {
    return SyntaxTraverser.psiTraverser(file)
      .traverse(TreeTraversal.LEAVES_DFS)
      .filter { !(isSpaceBetweenTags(it) || isSpaceInAttribute(it) || isSpaceBeforeTagEnd(it)) }
      .joinToString(separator = "") { if (it is PsiWhiteSpace && it.parent is XmlTag) " " else it.text }
  }

  private fun isSpaceBeforeTagEnd(it: PsiElement?) = it is PsiWhiteSpace && it.nextSibling?.elementType == XML_TAG_END
  private fun isSpaceInAttribute(it: PsiElement?) = it is PsiWhiteSpace && it.parent is XmlAttribute
  private fun isSpaceBetweenTags(it: PsiElement?) = it is PsiWhiteSpace && it.parent is XmlText && it.parent.children.size == 1

  override fun reformat(disableUpdateListener: (() -> Unit) -> Unit) = runFormatter(project, document, disableUpdateListener)

  companion object {
    fun detect(project: Project, document: Document): XmlMinimizedFormat? {
      val file = PsiDocumentManager.getInstance(project).getPsiFile(document)
      return if (file?.language is XMLLanguage) XmlMinimizedFormat(project, document) else null
    }
  }
}

class MongoJSFormat(private val grid: DataGrid, private val value: Any?, private val document: Document) : MinimizedFormat {
  override fun restore(file: PsiFile): String {
    return file.text
  }

  override fun reformat(disableUpdateListener: (() -> Unit) -> Unit) {
    val formatted = toJson(value, grid.objectFormatter, ObjectFormatterMode.JS_SCRIPT, newLines = true)
    disableUpdateListener {
      document.setText(formatted)
    }
  }

  companion object {
    fun detect(grid: DataGrid, value: Any?, project: Project, document: Document): MongoJSFormat? {
      val file = PsiDocumentManager.getInstance(project).getPsiFile(document)
      return if (file?.language?.id == "MongoJS" && (value is List<*> || value is Map<*, *>)) MongoJSFormat(grid, value, document)
      else null
    }
  }
}

private fun detectFormat(grid: DataGrid, value: Any?, project: Project, document: Document): MinimizedFormat? {
  return MinimizedFormatDetector.EP_NAME.extensionList.firstNotNullOfOrNull { detector -> detector.detectFormat(project, document) }
         ?: MongoJSFormat.detect(grid, value, project, document)
         ?: XmlMinimizedFormat.detect(project, document)
}

fun DataGrid.getSelectedValue(): Any? {
  val row = selectionModel.leadSelectionRow
  val column = selectionModel.leadSelectionColumn
  return getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getValueAt(row, column)
}

fun runFormatter(project: Project, document: Document, disableUpdateListener: (() -> Unit) -> Unit) {
  val file = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return
  val codeFormatter = CodeFormatterFacade(CodeStyle.getSettings(file), null)
  val ranges = FormatTextRanges(TextRange.from(0, document.textLength), true)
  disableUpdateListener {
    codeFormatter.processText(file, ranges, false)
  }
}
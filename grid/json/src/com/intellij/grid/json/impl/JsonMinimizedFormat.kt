package com.intellij.grid.json.impl

import com.intellij.database.run.ui.MinimizedFormat
import com.intellij.database.run.ui.MinimizedFormatDetector
import com.intellij.database.run.ui.runFormatter
import com.intellij.json.JsonElementTypes
import com.intellij.json.JsonLanguage
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.elementType
import com.intellij.util.containers.TreeTraversal

internal class JsonMinimizedFormatDetector : MinimizedFormatDetector {
  override fun detectFormat(project: Project, document: Document): MinimizedFormat? {
    return JsonMinimizedFormat.detect(project, document)
  }
}

internal class JsonMinimizedFormat(
  private val project: Project,
  private val document: Document,
  private val rules: List<SpaceRule>,
) : MinimizedFormat {

  override fun reformat(disableUpdateListener: (() -> Unit) -> Unit) = runFormatter(project, document, disableUpdateListener)

  override fun restore(file: PsiFile): String {
    return SyntaxTraverser.psiTraverser(file)
      .traverse(TreeTraversal.LEAVES_DFS)
      .filter { it !is PsiWhiteSpace }
      .joinToString(separator = "") {
        rules.fold(it.text) { text, rule ->
          rule.apply(text, it.elementType)
        }
      }
  }

  companion object {
    private const val ELEMENTS_TO_DETECT_FORMAT = 128

    fun detect(project: Project, document: Document): JsonMinimizedFormat? {
      val file = PsiDocumentManager.getInstance(project).getPsiFile(document)
      if (file?.language !is JsonLanguage) return null
      val rules = listOf(
        SpaceRule(true, JsonElementTypes.COLON, true),
        SpaceRule(false, JsonElementTypes.COLON, false),
        SpaceRule(true, JsonElementTypes.COMMA, true),
        SpaceRule(false, JsonElementTypes.COMMA, false),
        SpaceRule(true, JsonElementTypes.L_CURLY, false),
        SpaceRule(false, JsonElementTypes.R_CURLY, false),
        SpaceRule(true, JsonElementTypes.L_BRACKET, false),
        SpaceRule(false, JsonElementTypes.R_BRACKET, false)
      )
      SyntaxTraverser.psiTraverser(file)
        .traverse(TreeTraversal.LEAVES_DFS)
        .take(ELEMENTS_TO_DETECT_FORMAT)
        .forEach { e ->
          rules.forEach { it.observeElement(e) }
        }
      return JsonMinimizedFormat(project, document, rules)
    }

    class SpaceRule(private val isAfter: Boolean, private val elementType: IElementType, private val default: Boolean) {
      private var spaceCount = 0
      private var noSpaceCount = 0

      fun observeElement(e: PsiElement) {
        if (e.elementType != elementType) return
        val hasSpace = if (isAfter) e.nextSibling is PsiWhiteSpace else e.prevSibling is PsiWhiteSpace
        if (hasSpace) spaceCount++
        else noSpaceCount++
      }

      fun apply(text: String, type: IElementType?): String {
        if (type != elementType || !default && noSpaceCount == spaceCount || noSpaceCount > spaceCount) return text
        return if (isAfter) "$text " else " $text"
      }
    }
  }
}

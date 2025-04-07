// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.documentation.actions.ShowQuickDocInfoAction
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewDiffResult
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewDiffResult.HighlightingType
import com.intellij.codeInsight.intention.impl.preview.LookupPreviewHandler
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo.Html
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.lang.Language
import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.lang.documentation.ide.impl.DocumentationToolWindowManager
import com.intellij.model.Pointer
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.editor.richcopy.SyntaxInfoBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.backend.documentation.DocumentationContent
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LookupElementDocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.createSmartPointer
import com.intellij.ui.ColorUtil
import com.intellij.ui.DeferredIcon
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RowIcon
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.VisibleForTesting
import java.util.function.Function


class CommandCompletionDocumentationProvider : LookupElementDocumentationTargetProvider {
  override fun documentationTarget(psiFile: PsiFile, element: LookupElement, offset: Int): DocumentationTarget? {
    val completionLookupElement = element.`as`(CommandCompletionLookupElement::class.java) ?: return null
    val command: CompletionCommand = completionLookupElement.command
    if (command !is CompletionCommandWithPreview) return null
    val anchorElement = if (offset - 1 >= 0) psiFile.findElementAt(offset - 1) else psiFile.findElementAt(offset)
    if (anchorElement == null) return null
    return CommandCompletionDocumentationTarget(anchorElement, completionLookupElement)
  }
}

private class CommandCompletionDocumentationTarget(
  psiElement: PsiElement,
  val completionLookupElement: CommandCompletionLookupElement,
) : DocumentationTarget {
  private val myProject: Project = psiElement.project
  private val myLanguage: Language = psiElement.language
  private val elementPtr = psiElement.createSmartPointer()

  override fun createPointer(): Pointer<out DocumentationTarget> {
    return Pointer {
      val element = elementPtr.dereference() ?: return@Pointer null
      CommandCompletionDocumentationTarget(element, completionLookupElement)
    }
  }

  override fun computePresentation(): TargetPresentation {
    val presentation = LookupElementPresentation()
    completionLookupElement.renderElement(presentation)

    return TargetPresentation.builder(presentation.itemText
                                      ?: completionLookupElement.command.name).icon(presentation.icon).containerText(presentation.tailText).presentation()
  }

  override fun computeDocumentation(): DocumentationResult? {
    if (!completionLookupElement.hasPreview) return null
    return DocumentationResult.asyncDocumentation {
      readAction {
        val previewResult = completionLookupElement.preview ?: return@readAction null
        render(postprocess(previewResult))
      }
    }
  }

  private fun render(previewResult: IntentionPreviewInfo): DocumentationResult.Documentation? {
    return when (previewResult) {
      is IntentionPreviewDiffResult -> {
        val diffs = previewResult.diffs
        DocumentationResult.documentation(renderHtml(diffs))
      }
      is Html -> DocumentationResult.documentation(createContext(previewResult.content()))
      else -> null
    }
  }

  private fun createContext(content: HtmlChunk): DocumentationContent {
    val html = content.toString()
    val regexIcons = """<icon src="(.*?)"/>""".toRegex()
    var updatedContent = html

    regexIcons.findAll(html).forEach {
      val iconTag = it.value.substring(11, it.value.length - 3)
      val iconPath = findIconPath(iconTag, content)
      val newValue = if (iconPath != null) """<icon src="$iconPath"/>""" else ""
      updatedContent = updatedContent.replace(it.value, newValue)
    }

    var updatedClearedContent = updatedContent
    val regexSettings = """<a href="settings:(.*?)</a>""".toRegex()
    regexSettings.findAll(updatedContent).forEach {
      val path = it.value.substring(9, it.value.length - 4)
      val openClosed = path.indexOf(">")
      if (openClosed != -1 && openClosed + 1 < path.length) {
        val newValue = path.substring(openClosed + 1)
        @Suppress("HardCodedStringLiteral")
        updatedClearedContent = updatedClearedContent.replace(it.value, "<b>$newValue</b>")
      }
    }

    return DocumentationContent.content(updatedClearedContent)
  }

  private fun findIconPath(iconId: String, content: HtmlChunk): String? {
    var icon = content.findIcon(iconId) ?: return null
    var changed = true
    var i = 1
    while (i < 10 && changed) {
      i++
      changed = false
      if (icon is RowIcon) {
        changed = true
        icon = icon.allIcons.firstOrNull() ?: return null
      }
      if (icon is DeferredIcon) {
        changed = true
        icon = icon.baseIcon
      }
      if (icon is LayeredIcon) {
        return null
      }
    }
    if (icon is CachedImageIcon) {
      val coords = icon.getCoords() ?: return null
      return coords.second.getResource(coords.first.trimStart('/'))?.toString()
    }
    return null
  }


  @NlsSafe
  private fun renderHtml(diffs: List<IntentionPreviewDiffResult.DiffInfo>): String {
    val builder = StringBuilder()
    val maxLine = (diffs.maxOfOrNull { it.startLine + it.length } ?: 1000).toString().length
    for (i in 0..diffs.size - 1) {
      ProgressManager.checkCanceled()
      var codeSnippet = diffs[i].fileText
      val length = codeSnippet.split("\n").lastOrNull()?.length ?: -1
      if (length > 0 && length < 35) {
        codeSnippet += " ".repeat(35 - length)
      }
      val lineNumberIndexes = codeSnippet.indexesOf("\n").map { it + 1 }.toMutableList()
      lineNumberIndexes.add(0, 0)
      val additionalHighlighting = additionalHighlighting(diffs[i].fragments, lineNumberIndexes)
      val textHandler = if (diffs[i].startLine != -1) createLineNumberTextHandler(lineNumberIndexes, diffs[i].startLine, maxLine) else null
      var properties = HtmlSyntaxInfoUtil.HtmlGeneratorProperties.createDefault()
        .generateWrappedTags()
        .generateBackground()
        .generateFontSize()
        .generateFontFamily()
      if (textHandler != null) {
        properties = properties.textHandler(textHandler)
      }
      HtmlSyntaxInfoUtil.HtmlCodeSnippetBuilder(builder, myProject, myLanguage)
        .additionalIterator(additionalHighlighting)
        .codeSnippet(codeSnippet)
        .properties(properties)
        .build()
      if (diffs.size > 1 && i < diffs.size - 1) {
        builder.append("<hr>")
      }
    }
    val scheme: EditorColorsScheme = EditorColorsManager.getInstance().getGlobalScheme()
    val defaultBackground = scheme.defaultBackground
    val lineSpacing = scheme.lineSpacing
    val backgroundColor = ColorUtil.toHtmlColor(defaultBackground)
    return """
      <div style="min-width: 150px; max-width: 250px; padding: 0; margin: 0;"> 
      <div style="width: 95%; background-color:$backgroundColor; line-height: ${lineSpacing * 1.1}">$builder<br/>
      </div></div>""".trimIndent()
  }

  private fun createLineNumberTextHandler(
    lineNumberIndexes: List<Int>,
    startLine: Int,
    maxLine: Int,
  ): HtmlSyntaxInfoUtil.HtmlGeneratorProperties.TextHandler {
    val color = EditorColorsManager.getInstance().globalScheme.getColor(EditorColors.LINE_NUMBERS_COLOR)
    val colorLine = StringBuilder()
    if (color != null) {
      colorLine.append("#")
      UIUtil.appendColor(color, colorLine)
    }
    else {
      colorLine.append("grey")
    }

    val padding = maxLine + 2

    return object : HtmlSyntaxInfoUtil.HtmlGeneratorProperties.TextHandler {
      val processedIndexesStart = mutableSetOf<Int>()
      var currentLine = startLine
      val myColor = colorLine.toString()
      val myPadding = padding

      override fun handleText(startOffset: Int, endOffset: Int, resultBuffer: java.lang.StringBuilder, superHandler: Runnable?) {
        if (startOffset in lineNumberIndexes && !processedIndexesStart.contains(startOffset)) {
          processedIndexesStart.add(startOffset)
          resultBuffer.append("<span style=\"font-size: 90%; color:${myColor};\">${currentLine.toString().padStart(myPadding) + "  "}</span>")
          currentLine++
        }
        superHandler?.run()
      }
    }
  }

  private fun additionalHighlighting(
    fragments: List<IntentionPreviewDiffResult.Fragment>,
    additionalIndexes: List<Int>,
  ): SyntaxInfoBuilder.RangeIterator {
    val combinedFragments = combineFragments(fragments, additionalIndexes)
    return object : SyntaxInfoBuilder.RangeIterator {
      private val iterator: Iterator<IntentionPreviewDiffResult.Fragment> = combinedFragments.iterator()
      private var current: IntentionPreviewDiffResult.Fragment? = null
      val scheme: EditorColorsScheme = EditorColorsManager.getInstance().getGlobalScheme()

      override fun atEnd(): Boolean {
        return !iterator.hasNext()
      }

      override fun advance() {
        current = iterator.next()
      }

      override fun getRangeStart(): Int {
        return current?.start ?: 0
      }

      override fun getRangeEnd(): Int {
        return current?.end ?: 0
      }

      override fun getTextAttributes(): TextAttributes? {
        val attributesKey = when (current?.type) {
          HighlightingType.UPDATED -> DiffColors.DIFF_MODIFIED
          HighlightingType.ADDED -> DiffColors.DIFF_INSERTED
          HighlightingType.DELETED -> DiffColors.DIFF_DELETED
          else -> DiffColors.DIFF_MODIFIED
        }
        return scheme.getAttributes(attributesKey, true)
      }

      override fun dispose() {
      }
    }
  }

  private fun postprocess(info: IntentionPreviewInfo) = when (info) {
    is IntentionPreviewInfo.CustomDiff -> IntentionPreviewDiffResult.fromCustomDiff(info)
    is IntentionPreviewInfo.MultiFileDiff -> IntentionPreviewDiffResult.fromMultiDiff(info)
    else -> info
  }
}

private fun String.indexesOf(fragment: String): MutableList<Int> {
  val result = mutableListOf<Int>()
  var index = indexOf(fragment)
  while (index >= 0) {
    result.add(index)
    index = indexOf(fragment, index + fragment.length)
  }
  return result
}

@VisibleForTesting
fun combineFragments(
  fragments: List<IntentionPreviewDiffResult.Fragment>,
  additionalIndexes: List<Int>,
): List<IntentionPreviewDiffResult.Fragment> {
  val combinedFragments = mutableListOf<IntentionPreviewDiffResult.Fragment>()
  val sortedFragments = fragments.sortedBy { it.start }
  val sortedAdditionalIndexes = additionalIndexes.sorted()
  var indexAdditionalIndexes = 0
  for (fragment in sortedFragments) {
    while (indexAdditionalIndexes < sortedAdditionalIndexes.size && fragment.start >= sortedAdditionalIndexes[indexAdditionalIndexes]) {
      val index = sortedAdditionalIndexes[indexAdditionalIndexes]
      combinedFragments.add(IntentionPreviewDiffResult.Fragment(HighlightingType.UPDATED, index, index))
      indexAdditionalIndexes++
    }

    var firstIndex = fragment.start
    if (indexAdditionalIndexes < sortedAdditionalIndexes.size && fragment.start < sortedAdditionalIndexes[indexAdditionalIndexes] && fragment.end >= sortedAdditionalIndexes[indexAdditionalIndexes]) {
      while (indexAdditionalIndexes < sortedAdditionalIndexes.size && fragment.start < sortedAdditionalIndexes[indexAdditionalIndexes] && fragment.end >= sortedAdditionalIndexes[indexAdditionalIndexes]) {
        val end = sortedAdditionalIndexes[indexAdditionalIndexes]
        combinedFragments.add(IntentionPreviewDiffResult.Fragment(fragment.type, firstIndex, end))
        firstIndex = end
        indexAdditionalIndexes++
      }
    }


    if (indexAdditionalIndexes >= sortedAdditionalIndexes.size || fragment.end < sortedAdditionalIndexes[indexAdditionalIndexes]) {
      combinedFragments.add(IntentionPreviewDiffResult.Fragment(fragment.type, firstIndex, fragment.end))
      continue
    }
  }

  if (indexAdditionalIndexes < sortedAdditionalIndexes.size) {
    val index = sortedAdditionalIndexes[indexAdditionalIndexes]
    while (indexAdditionalIndexes < sortedAdditionalIndexes.size) {
      combinedFragments.add(IntentionPreviewDiffResult.Fragment(HighlightingType.UPDATED, index, index))
      indexAdditionalIndexes++
    }
  }
  return combinedFragments
}

/**
 * Installs a listener on the provided lookup that enables preview functionality for intentions
 * if certain user settings allow it. The preview displays additional context or suggestions
 * for relevant completion items when the lookup selection changes.
 *
 * @param lookup the instance of `LookupImpl` for which the preview listener is to be installed
 */
internal fun installLookupIntentionPreviewListener(lookup: LookupImpl) {
  if (!EditorSettingsExternalizable.getInstance().isShowIntentionPreview) return
  val project = lookup.project
  if (showJavaDocPreview(project)) return
  val previewHandler =
    LookupPreviewHandler(
      project, lookup,
      Function { element ->
        return@Function element as? LookupElement
      },
      Function { element ->
        val commandElement = element.`as`(CommandCompletionLookupElement::class.java)
        if (commandElement != null && commandElement.hasPreview) {
          commandElement.preview
        }
        else {
          IntentionPreviewInfo.EMPTY
        }
      }
    )

  val listener = object : LookupListener {
    override fun currentItemChanged(event: LookupEvent) {
      val currentItem = event.lookup.currentItem
      val element = currentItem
        ?.`as`(CommandCompletionLookupElement::class.java)
      if (element != null) {
        previewHandler.showInitially()
      }
    }

    override fun itemSelected(event: LookupEvent): Unit = stopPreview()
    override fun lookupCanceled(event: LookupEvent): Unit = stopPreview()
    fun stopPreview() {
      previewHandler.close()
      lookup.removeLookupListener(this)
    }
  }

  lookup.addLookupListener(listener)

  val connection: MessageBusConnection = ApplicationManager.getApplication().getMessageBus().connect(lookup)
  connection.subscribe(AnActionListener.TOPIC, object : AnActionListener {
    override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
      if (action !is ShowQuickDocInfoAction) {
        return
      }
      listener.stopPreview()
    }
  })
}

internal fun showJavaDocPreview(project: Project): Boolean =
  CodeInsightSettings.getInstance().AUTO_POPUP_JAVADOC_INFO ||
  DocumentationSettings.autoShowQuickDocInModalDialogs() ||
  ToolWindowManager.getInstance(project)
    .getToolWindow(DocumentationToolWindowManager.TOOL_WINDOW_ID)?.isVisible == true
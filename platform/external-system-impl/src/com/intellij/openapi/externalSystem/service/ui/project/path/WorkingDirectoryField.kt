// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.project.path

import com.intellij.ide.wizard.getCanonicalPath
import com.intellij.ide.wizard.getPresentablePath
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.externalSystem.service.ui.completion.JTextCompletionContributor
import com.intellij.openapi.externalSystem.service.ui.completion.JTextCompletionContributor.CompletionType
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionInfo
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionPopup
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionPopup.UpdatePopupType.SHOW_IF_HAS_VARIANCES
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.properties.map
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.addKeyboardAction
import com.intellij.openapi.ui.getKeyStrokes
import com.intellij.openapi.ui.isTextUnderMouse
import com.intellij.openapi.ui.BrowseFolderRunnable
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.layout.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.plaf.basic.BasicTextUI
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter
import javax.swing.text.Highlighter


class WorkingDirectoryField(
  project: Project,
  private val workingDirectoryInfo: WorkingDirectoryInfo
) : ExtendableTextField() {

  private val propertyGraph = PropertyGraph(isBlockPropagation = false)
  private val modeProperty = propertyGraph.graphProperty { Mode.NAME }
  private val textProperty = propertyGraph.graphProperty { "" }
  private val workingDirectoryProperty = propertyGraph.graphProperty { "" }
  private val projectNameProperty = propertyGraph.graphProperty { "" }

  var mode by modeProperty
  var workingDirectory by workingDirectoryProperty
  var projectName by projectNameProperty

  private val externalProjects = workingDirectoryInfo.externalProjects

  private var highlightTag: Any? = null
  private val highlightRecursionGuard =
    RecursionManager.createGuard<WorkingDirectoryField>(WorkingDirectoryField::class.java.name)

  init {
    val text by textProperty.map { it.trim() }
    workingDirectoryProperty.dependsOn(textProperty) {
      when (mode) {
        Mode.PATH -> getCanonicalPath(text)
        Mode.NAME -> resolveProjectPathByName(text) ?: text
      }
    }
    projectNameProperty.dependsOn(textProperty) {
      when (mode) {
        Mode.PATH -> resolveProjectNameByPath(getCanonicalPath(text)) ?: text
        Mode.NAME -> text
      }
    }
    textProperty.dependsOn(modeProperty) {
      when (mode) {
        Mode.PATH -> getPresentablePath(workingDirectory)
        Mode.NAME -> projectName
      }
    }
    textProperty.dependsOn(workingDirectoryProperty) {
      when (mode) {
        Mode.PATH -> getPresentablePath(workingDirectory)
        Mode.NAME -> resolveProjectNameByPath(workingDirectory) ?: getPresentablePath(workingDirectory)
      }
    }
    textProperty.dependsOn(projectNameProperty) {
      when (mode) {
        Mode.PATH -> resolveProjectPathByName(projectName) ?: projectName
        Mode.NAME -> projectName
      }
    }
    modeProperty.dependsOn(workingDirectoryProperty) {
      when {
        workingDirectory.isEmpty() -> Mode.NAME
        resolveProjectNameByPath(workingDirectory) != null -> mode
        else -> Mode.PATH
      }
    }
    modeProperty.dependsOn(projectNameProperty) {
      when {
        projectName.isEmpty() -> Mode.NAME
        resolveProjectPathByName(projectName) != null -> mode
        else -> Mode.PATH
      }
    }
    bind(textProperty)
  }

  private fun resolveProjectPathByName(projectName: String) =
    resolveValueByKey(projectName, externalProjects, { name }, { path })

  private fun resolveProjectNameByPath(workingDirectory: String) =
    resolveValueByKey(workingDirectory, externalProjects, { path }, { name })

  private fun <E> resolveValueByKey(
    key: String,
    entries: List<E>,
    getKey: E.() -> String,
    getValue: E.() -> String
  ): String? {
    if (key.isNotEmpty()) {
      val entry = entries.asSequence()
        .filter { it.getKey().startsWith(key) }
        .sortedBy { it.getKey().length }
        .firstOrNull()
      if (entry != null) {
        val suffix = entry.getKey().removePrefix(key)
        if (entry.getValue().endsWith(suffix)) {
          return entry.getValue().removeSuffix(suffix)
        }
      }
      val parentEntry = entries.asSequence()
        .filter { key.startsWith(it.getKey()) }
        .sortedByDescending { it.getKey().length }
        .firstOrNull()
      if (parentEntry != null) {
        val suffix = key.removePrefix(parentEntry.getKey())
        return parentEntry.getValue() + suffix
      }
    }
    return null
  }

  init {
    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (isTextUnderMouse(e)) {
          mode = Mode.PATH
        }
      }
    })
    addKeyboardAction(getKeyStrokes("CollapseRegion", "CollapseRegionRecursively", "CollapseAllRegions")) {
      mode = Mode.NAME
    }
    addKeyboardAction(getKeyStrokes("ExpandRegion", "ExpandRegionRecursively", "ExpandAllRegions")) {
      mode = Mode.PATH
    }
  }

  init {
    addHighlighterListener {
      updateHighlight()
    }
    textProperty.afterChange {
      updateHighlight()
    }
    modeProperty.afterChange {
      updateHighlight()
    }
    updateHighlight()
  }

  private fun updateHighlight() {
    highlightRecursionGuard.doPreventingRecursion(this, false) {
      if (highlightTag != null) {
        highlighter.removeHighlight(highlightTag)
        foreground = null
      }
      if (mode == Mode.NAME) {
        val textAttributes = EditorColors.FOLDED_TEXT_ATTRIBUTES.defaultAttributes
        val painter = DefaultHighlightPainter(textAttributes.backgroundColor)
        highlightTag = highlighter.addHighlight(0, text.length, painter)
        foreground = textAttributes.foregroundColor
      }
    }
  }

  private fun addHighlighterListener(listener: () -> Unit) {
    highlighter = object : BasicTextUI.BasicHighlighter() {
      override fun changeHighlight(tag: Any, p0: Int, p1: Int) =
        super.changeHighlight(tag, p0, p1)
          .also { listener() }

      override fun removeHighlight(tag: Any) =
        super.removeHighlight(tag)
          .also { listener() }

      override fun removeAllHighlights() =
        super.removeAllHighlights()
          .also { listener() }

      override fun addHighlight(p0: Int, p1: Int, p: Highlighter.HighlightPainter) =
        super.addHighlight(p0, p1, p)
          .also { listener() }
    }
  }

  init {
    val fileBrowseAccessor = object : TextComponentAccessor<WorkingDirectoryField> {
      override fun getText(component: WorkingDirectoryField) = workingDirectory
      override fun setText(component: WorkingDirectoryField, text: String) {
        workingDirectory = text
      }
    }
    val browseFolderRunnable = object : BrowseFolderRunnable<WorkingDirectoryField>(
      workingDirectoryInfo.fileChooserTitle,
      workingDirectoryInfo.fileChooserDescription,
      project,
      workingDirectoryInfo.fileChooserDescriptor,
      this,
      fileBrowseAccessor
    ) {
      override fun chosenFileToResultingText(chosenFile: VirtualFile): String {
        return ExternalSystemApiUtil.getLocalFileSystemPath(chosenFile)
      }
    }
    addBrowseExtension(browseFolderRunnable, null)
  }

  init {
    val textCompletionContributor = JTextCompletionContributor.create<WorkingDirectoryField>(CompletionType.REPLACE) { textToComplete ->
      when (mode) {
        Mode.NAME -> {
          externalProjects
            .map { it.name }
            .map { TextCompletionInfo(it) }
        }
        Mode.PATH -> {
          val pathToComplete = getCanonicalPath(textToComplete, removeLastSlash = false)
          externalProjects
            .filter { it.path.startsWith(pathToComplete) }
            .map { it.path.substring(pathToComplete.length) }
            .map { textToComplete + FileUtil.toSystemDependentName(it) }
            .map { TextCompletionInfo(it) }
        }
      }
    }
    val textCompletionPopup = TextCompletionPopup(project, this, textCompletionContributor)
    modeProperty.afterChange {
      textCompletionPopup.updatePopup(SHOW_IF_HAS_VARIANCES)
    }
  }

  enum class Mode { PATH, NAME }
}
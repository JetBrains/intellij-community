// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui

import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.ui.completetion.JTextCompletionContributor
import com.intellij.openapi.externalSystem.service.ui.completetion.JTextCompletionContributor.CompletionType
import com.intellij.openapi.externalSystem.service.ui.completetion.TextCompletionContributor.TextCompletionInfo
import com.intellij.openapi.externalSystem.service.ui.completetion.TextCompletionPopup
import com.intellij.openapi.externalSystem.service.ui.completetion.TextCompletionPopup.UpdatePopupType.SHOW_IF_HAS_VARIANCES
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.properties.map
import com.intellij.openapi.observable.properties.transform
import com.intellij.openapi.project.Project
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


class ExternalSystemProjectPathField(
  project: Project,
  externalSystemId: ProjectSystemId
) : ExtendableTextField() {

  private val propertyGraph = PropertyGraph(isBlockPropagation = false)
  private val modeProperty = propertyGraph.graphProperty { Mode.NAME }
  private val textProperty = propertyGraph.graphProperty { "" }
  private val projectPathProperty = propertyGraph.graphProperty { "" }
  private val projectUiPathProperty = projectPathProperty.transform(::getUiPath, ::getModelPath)
  private val projectNameProperty = propertyGraph.graphProperty { "" }

  var mode by modeProperty
  var projectPath by projectPathProperty
  var projectUiPath by projectUiPathProperty
  var projectName by projectNameProperty

  private val externalProjects = ArrayList<ExternalProject>()
  private val projectNameDelimiter = ExternalSystemUiUtil.getUiAware(externalSystemId).projectNameDelimiter

  private var highlightTag: Any? = null
  private val highlightRecursionGuard =
    RecursionManager.createGuard<ExternalSystemProjectPathField>(ExternalSystemProjectPathField::class.java.name)

  init {
    val text by textProperty.map { it.trim() }
    projectUiPathProperty.dependsOn(textProperty) {
      when (mode) {
        Mode.PATH -> text
        Mode.NAME -> resolveProjectPathByName(text) ?: text
      }
    }
    projectNameProperty.dependsOn(textProperty) {
      when (mode) {
        Mode.PATH -> resolveProjectNameByPath(text) ?: text
        Mode.NAME -> text
      }
    }
    textProperty.dependsOn(modeProperty) {
      when (mode) {
        Mode.PATH -> projectUiPath
        Mode.NAME -> projectName
      }
    }
    textProperty.dependsOn(projectUiPathProperty) {
      when (mode) {
        Mode.PATH -> projectUiPath
        Mode.NAME -> resolveProjectNameByPath(projectUiPath) ?: projectUiPath
      }
    }
    textProperty.dependsOn(projectNameProperty) {
      when (mode) {
        Mode.PATH -> resolveProjectPathByName(projectName) ?: projectName
        Mode.NAME -> projectName
      }
    }
    modeProperty.dependsOn(projectUiPathProperty) {
      when {
        projectUiPath.isEmpty() -> Mode.NAME
        resolveProjectNameByPath(projectUiPath) != null -> mode
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
    resolveValueByKey(projectName, externalProjects, { name }, { path }, projectNameDelimiter, "/")

  private fun resolveProjectNameByPath(projectPath: String) =
    resolveValueByKey(getModelPath(projectPath), externalProjects, { path }, { name }, "/", projectNameDelimiter)

  private fun <E> resolveValueByKey(
    key: String,
    entries: List<E>,
    getKey: E.() -> String,
    getValue: E.() -> String,
    keyDelimiter: String,
    valueDelimiter: String
  ): String? {
    val entry = entries.asSequence()
      .filter { it.getKey().startsWith(key) && key.isNotEmpty() }
      .sortedBy { it.getKey().length }
      .firstOrNull()
    if (entry != null) {
      val suffix = entry.getKey().removePrefix(key)
        .replace(keyDelimiter, valueDelimiter)
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
        .replace(keyDelimiter, valueDelimiter)
      return parentEntry.getValue() + suffix
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
    val localSettings = ExternalSystemApiUtil.getLocalSettings<AbstractExternalSystemLocalSettings<*>>(project, externalSystemId)
    val uiAware = ExternalSystemUiUtil.getUiAware(externalSystemId)
    for ((parent, children) in localSettings.availableProjects) {
      val parentPath = getModelPath(parent.path)
      val parentName = uiAware.getProjectRepresentationName(project, parentPath, null)
      externalProjects.add(ExternalProject(parentName, parentPath))
      for (child in children) {
        val childPath = getModelPath(child.path)
        if (parentPath == childPath) continue
        val childName = uiAware.getProjectRepresentationName(project, childPath, parentPath)
        externalProjects.add(ExternalProject(childName, childPath))
      }
    }
    val rootProject = externalProjects.firstOrNull()
    if (rootProject != null) {
      projectName = rootProject.name
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
    val title = ExternalSystemBundle.message("settings.label.select.project", externalSystemId.readableName)
    val fileChooserDescriptor = ExternalSystemApiUtil.getExternalProjectConfigDescriptor(externalSystemId)
    val fileBrowseAccessor = object : TextComponentAccessor<ExternalSystemProjectPathField> {
      override fun getText(component: ExternalSystemProjectPathField) = projectPath
      override fun setText(component: ExternalSystemProjectPathField, text: String) {
        projectPath = text
      }
    }
    val browseFolderRunnable = object : BrowseFolderRunnable<ExternalSystemProjectPathField>(
      title, null, project, fileChooserDescriptor, this, fileBrowseAccessor
    ) {
      override fun chosenFileToResultingText(chosenFile: VirtualFile): String {
        return ExternalSystemApiUtil.getLocalFileSystemPath(chosenFile)
      }
    }
    addBrowseExtension(browseFolderRunnable, null)
  }

  init {
    val textCompletionContributor = JTextCompletionContributor.create(CompletionType.REPLACE) { textToComplete ->
      when (mode) {
        Mode.NAME -> {
          externalProjects
            .map { it.name }
            .map { TextCompletionInfo(it) }
        }
        Mode.PATH -> {
          val pathToComplete = getModelPath(textToComplete, removeLastSlash = false)
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

  private data class ExternalProject(val name: String, val path: String)
}
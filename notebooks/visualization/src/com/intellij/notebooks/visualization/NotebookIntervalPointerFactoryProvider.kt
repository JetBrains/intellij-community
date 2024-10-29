package com.intellij.notebooks.visualization

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project

private const val ID: String = "org.jetbrains.plugins.notebooks.notebookIntervalPointerFactoryProvider"

interface NotebookIntervalPointerFactoryProvider {
  fun create(project: Project, document: Document): NotebookIntervalPointerFactory

  companion object : LanguageExtension<NotebookIntervalPointerFactoryProvider>(ID)
}
package com.intellij.notebooks.visualization

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.editor.Document
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.KeyedLazyInstance

internal val INTERVAL_POINTER_FACTORY_EP_ID = ExtensionPointName.create<KeyedLazyInstance<NotebookIntervalPointerFactoryProvider>>(
  "org.jetbrains.plugins.notebooks.notebookIntervalPointerFactoryProvider"
)

interface NotebookIntervalPointerFactoryProvider {
  fun create(project: Project, document: Document): NotebookIntervalPointerFactory

  companion object : LanguageExtension<NotebookIntervalPointerFactoryProvider>(INTERVAL_POINTER_FACTORY_EP_ID)
}
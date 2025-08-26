// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.codeInsight.codeVision.*
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.icons.AllIcons
import com.intellij.java.JavaBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.ExternalJavaConfigurationService.JavaConfigurationStatus
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import java.awt.event.MouseEvent

/**
 * Code Vision for external Java configuration files (e.g., .sdkmanrc, .tool-versions).
 *
 * It shows the [JavaConfigurationStatus] and makes it possible to set the project JDK
 * or download missing JDKs through an external configuration tool.
 */
public class ExternalJavaConfigurationCodeVision : CodeVisionProvider<Unit> {
  public companion object {
    internal const val ID: String = "java.external.configuration"
  }

  override fun isAvailableFor(project: Project): Boolean = true

  override fun precomputeOnUiThread(editor: Editor) {}

  override fun preparePreview(editor: Editor, file: PsiFile) {

  }

  override val name: String
    get() = JavaBundle.message("external.java.configuration.inlay.provider.name")
  override val relativeOrderings: List<CodeVisionRelativeOrdering>
    get() = emptyList()
  override val defaultAnchor: CodeVisionAnchorKind
    get() = CodeVisionAnchorKind.Default
  override val id: String
    get() = ID

  override fun computeCodeVision(editor: Editor, uiData: Unit): CodeVisionState {
    val project = editor.project ?: return CodeVisionState.READY_EMPTY

    return InlayHintsUtils.computeCodeVisionUnderReadAction {
      val document = editor.document
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return@computeCodeVisionUnderReadAction CodeVisionState.READY_EMPTY

      val provider = ExternalJavaConfigurationProvider.EP_NAME.extensionList.find { it.isConfigurationFile(psiFile.name) }
                      ?: return@computeCodeVisionUnderReadAction CodeVisionState.READY_EMPTY

      val text = FileDocumentManager.getInstance().getFile(document)?.let { FileDocumentManager.getInstance().getDocument(it)?.text }
                 ?: psiFile.text

      val range = provider.getReleaseDataOffset(text) ?: return@computeCodeVisionUnderReadAction CodeVisionState.READY_EMPTY

      val service = project.service<ExternalJavaConfigurationService>()
      @Suppress("UNCHECKED_CAST")
      service.updateFromConfig(provider as ExternalJavaConfigurationProvider<Any?>)
      val status = service.statuses[psiFile.virtualFile.path] ?: JavaConfigurationStatus.Unknown

      val entry = buildEntry(project, provider, status)
      if (entry == null) return@computeCodeVisionUnderReadAction CodeVisionState.READY_EMPTY

      CodeVisionState.Ready(listOf(TextRange(range.startOffset, range.endOffset) to entry))
    }
  }

  private fun <T> buildEntry(project: Project,
                             provider: ExternalJavaConfigurationProvider<T>,
                             status: JavaConfigurationStatus): CodeVisionEntry? {
    return when (status) {
      is JavaConfigurationStatus.Unknown -> {
        val text = JavaBundle.message("external.java.configuration.inlay.unknown")
        TextCodeVisionEntry(text, ID, icon = AllIcons.Actions.Refresh).apply { showInMorePopup = false }
      }

      is JavaConfigurationStatus.AlreadyConfigured -> {
        val jdkName = ProjectRootManager.getInstance(project).projectSdk?.name ?: return null
        val text = JavaBundle.message("external.java.configuration.inlay.already.configured", jdkName)
        val onClick: (MouseEvent?, Editor) -> Unit = { _, _ ->
          ProjectSettingsService.getInstance(project).openProjectSettings()
        }
        ClickableTextCodeVisionEntry(text, ID, onClick = onClick, icon = AllIcons.General.GreenCheckmark)
      }

      is JavaConfigurationStatus.Found -> {
        val text = JavaBundle.message("external.java.configuration.inlay.found")
        val onClick: (MouseEvent?, Editor) -> Unit = { _, _ ->
          val service = project.service<ExternalJavaConfigurationService>()
          @Suppress("UNCHECKED_CAST")
          service.updateFromConfig(provider as ExternalJavaConfigurationProvider<Any?>, true)
        }
        ClickableTextCodeVisionEntry(text, ID, onClick = onClick, icon = AllIcons.General.Gear)
      }

      is JavaConfigurationStatus.Missing<*> -> {
        @Suppress("UNCHECKED_CAST")
        val missing = status as JavaConfigurationStatus.Missing<T>
        val command = provider.getDownloadCommandFor(missing.releaseData)
        if (command != null) {
          val text = JavaBundle.message("external.java.configuration.inlay.download", command)
          val onClick: (MouseEvent?, Editor) -> Unit = { _, _ ->
              // TODO: Open terminal session with the [command]
          }
          ClickableTextCodeVisionEntry(text, ID, onClick = onClick, icon = AllIcons.Actions.Download)
        }
        else {
          val text = JavaBundle.message("external.java.configuration.inlay.missing")
          TextCodeVisionEntry(text, ID, icon = AllIcons.General.Warning).apply {
            showInMorePopup = false
          }
        }
      }
    }
  }
}
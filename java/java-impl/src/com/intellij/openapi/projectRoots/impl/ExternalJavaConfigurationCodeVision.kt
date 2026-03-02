// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.CodeVisionState
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.java.JavaBundle
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.ExternalJavaConfigurationService.JavaConfigurationStatus
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent

private val FAKE_DATA_KEY: Key<Boolean> = Key.create("external.java.configuration.fake.key")

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
    editor.putUserData(FAKE_DATA_KEY, true)
  }

  override val name: String
    get() = JavaBundle.message("external.java.configuration.inlay.provider.name")
  override val relativeOrderings: List<CodeVisionRelativeOrdering>
    get() = emptyList()
  override val defaultAnchor: CodeVisionAnchorKind
    get() = CodeVisionAnchorKind.Default
  override val id: String
    get() = ID
  override val groupId: String
    get() = ExternalJavaConfigurationGroupSettingProvider.GROUP_ID

  override fun computeCodeVision(editor: Editor, uiData: Unit): CodeVisionState {
    val project = editor.project ?: return CodeVisionState.READY_EMPTY

    if (editor.getUserData(FAKE_DATA_KEY) == true) return fakeUserData()

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
      val virtualFile = psiFile.virtualFile
      val status = service.statuses[virtualFile.fileSystem.getNioPath(virtualFile)] ?: JavaConfigurationStatus.Unknown

      val entry = buildEntry(project, provider, status)
      if (entry == null) return@computeCodeVisionUnderReadAction CodeVisionState.READY_EMPTY

      CodeVisionState.Ready(listOf(TextRange(range.startOffset, range.endOffset) to entry))
    }
  }

  private fun fakeUserData(): CodeVisionState {
    val text = JavaBundle.message("external.java.configuration.inlay.already.configured", "temurin-11")
    return CodeVisionState.Ready(listOf(
      TextRange(13, 28) to ClickableTextCodeVisionEntry(text, ID, onClick = { _, _ -> }, icon = AllIcons.General.GreenCheckmark)
    ))
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

        val actions = ExternalJavaConfigurationMissingAction.EP_NAME.extensionList.mapNotNull { it.createAction(project, provider, missing.releaseData) }

        val onClick: (MouseEvent?, Editor) -> Unit = { event, editor ->
          if (actions.isNotEmpty()) {
            val group = DefaultActionGroup(actions)
            val dataContext = DataManager.getInstance().getDataContext(event?.component ?: editor.component)
            val popup = JBPopupFactory.getInstance().createActionGroupPopup(
              null,
              group,
              dataContext,
              JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
              true
            )
            if (event != null) {
              popup.show(RelativePoint(event))
            } else {
              popup.showInBestPositionFor(editor)
            }
          }
        }

        val text = when {
          actions.isNotEmpty() -> JavaBundle.message("external.java.configuration.inlay.missing.actions")
          else -> JavaBundle.message("external.java.configuration.inlay.missing")
        }

        ClickableTextCodeVisionEntry(text, ID, onClick = onClick).apply {
          showInMorePopup = actions.isNotEmpty()
        }
      }
    }
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.client.generator

import com.intellij.icons.AllIcons
import com.intellij.ide.scratch.RootType
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.microservices.MicroservicesBundle
import com.intellij.microservices.endpoints.EndpointsChangeTracker
import com.intellij.microservices.oas.OasSpecificationProvider.Companion.getOasSpecification
import com.intellij.microservices.oas.OpenApiSpecification
import com.intellij.microservices.url.HTTP_SCHEMES
import com.intellij.microservices.url.inlay.UrlPathInlayAction
import com.intellij.microservices.url.inlay.UrlPathInlayHint
import com.intellij.microservices.url.references.UrlPathContext
import com.intellij.microservices.url.references.resolveTargets
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.openapi.vfs.writeText
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.createSmartPointer
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.awt.RelativePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.ListSelectionModel

internal class OpenInScratchClientGeneratorInlayAction : UrlPathInlayAction {
  override val icon: Icon = AllIcons.Webreferences.Openapi

  override val name: String
    get() = MicroservicesBundle.message("client.generator.inlay.action.name")

  override fun isAvailable(file: PsiFile, urlPathInlayHint: UrlPathInlayHint): Boolean {
    val available = urlPathInlayHint.context.schemes.run { isNotEmpty() && all { it in HTTP_SCHEMES } }
    return Registry.`is`("client.generator.inlay.action") && available
  }

  override fun actionPerformed(file: PsiFile, editor: Editor, urlPathContext: UrlPathContext, mouseEvent: MouseEvent) {
    JBPopupFactory.getInstance().createPopupChooserBuilder(clients)
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setRenderer(SimpleListCellRenderer.create("", ClientGenerator::title))
      .setTitle(MicroservicesBundle.message("client.generator.inlay.action.popup.title"))
      .setItemChosenCallback { selectedClient ->
        file.project.service<ClientGeneratorOpenInScratchService>()
          .createScratchFile(selectedClient, editor, urlPathContext)
      }
      .createPopup()
      .show(RelativePoint(mouseEvent))
  }

  private val clients: List<ClientGenerator>
    get() = ClientGenerator.EP_NAME.extensionList
}

@ApiStatus.Experimental
const val CLIENT_EXAMPLE_SCRATCH_PREFIX: String = "client-example"

@ApiStatus.Experimental
@Service(Service.Level.PROJECT)
class ClientGeneratorOpenInScratchService(private val project: Project, private val scope: CoroutineScope) {

  fun createScratchFileWithoutEndpointsChangeTracking(clientGenerator: ClientGenerator, oas: OpenApiSpecification) {
    scope.launch {
      EndpointsChangeTracker.withExpectedChanges(project) {
        createScratchFile(clientGenerator, oas)
      }
    }
  }

  fun createScratchFile(clientGenerator: ClientGenerator, editor: Editor, urlPathContext: UrlPathContext) {
    scope.launch {
      val oas = withModalProgress(project, MicroservicesBundle.message("client.generator.progress.title.open.in.scratch")) {
        readAction {
          blockingContextToIndicator {
            getOpenApiSpecification(urlPathContext)
          }
        }
      }

      if (oas == null) {
        CommonRefactoringUtil.showErrorHint(
          project,
          editor,
          MicroservicesBundle.message("client.generator.open.in.scratch.error"),
          MicroservicesBundle.message("client.generator.open.in.scratch.error.title"),
          null
        )
        return@launch
      }

      createScratchFile(clientGenerator, oas)
    }
  }

  private fun getOpenApiSpecification(urlPathContext: UrlPathContext): OpenApiSpecification? {
    val resolvedTargets = urlPathContext.resolveTargets(project)
    return resolvedTargets.firstNotNullOfOrNull(::getOasSpecification)
  }

  suspend fun createScratchFile(clientGenerator: ClientGenerator, oas: OpenApiSpecification) {
    val (text, fileType) = readAction { clientGenerator.generateClientWithBoilerplate(project, oas) } ?: return

    val extension = fileType.defaultExtension.takeIf(String::isNotEmpty)
    val fileName = extension?.let { ext -> "$CLIENT_EXAMPLE_SCRATCH_PREFIX.$ext" } ?: CLIENT_EXAMPLE_SCRATCH_PREFIX
    val language = (fileType as? LanguageFileType)?.language

    val fileService = ScratchFileService.getInstance()
    val scratchFile = writeAction {
      runCatching {
        fileService.findFile(RootType.findById("scratches"), fileName, ScratchFileService.Option.create_new_always)
      }.getOrNull()
        ?.also { scratchFile ->
          fileService.scratchesMapping.setMapping(scratchFile, language)
          scratchFile.writeText(text)
        }
    } ?: return

    val pointer = readAction { scratchFile.findPsiFile(project)?.createSmartPointer() }

    WriteCommandAction.runWriteCommandAction(project, MicroservicesBundle.message("command.export.client.example"), null, Runnable {
      val psiFile = pointer?.element ?: return@Runnable
      CodeStyleManager.getInstance(project).reformat(psiFile)
    })

    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        pointer?.element?.navigate(true)
      }
    }
    return
  }

  private fun ClientGenerator.generateClientWithBoilerplate(project: Project, oas: OpenApiSpecification): ClientExample? {
    val clientSettings = availableClientSettings.actualClientSettings
    val previousSettings = clientSettings.boilerplate
    clientSettings.boilerplate = true

    val clientExample = generate(project, oas)

    clientSettings.boilerplate = previousSettings
    return clientExample
  }
}
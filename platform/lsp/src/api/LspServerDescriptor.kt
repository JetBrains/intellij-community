// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.DeprecatedLspCustomization
import com.intellij.platform.lsp.api.customization.LspCodeActionsSupport
import com.intellij.platform.lsp.api.customization.LspCommandsSupport
import com.intellij.platform.lsp.api.customization.LspCompletionSupport
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
import com.intellij.platform.lsp.api.customization.LspDocumentColorSupport
import com.intellij.platform.lsp.api.customization.LspDocumentLinkSupport
import com.intellij.platform.lsp.api.customization.LspFindReferencesSupport
import com.intellij.platform.lsp.api.customization.LspFormattingSupport
import com.intellij.platform.lsp.api.customization.LspGoToDefinitionSupport
import com.intellij.platform.lsp.api.customization.LspGoToTypeDefinitionSupport
import com.intellij.platform.lsp.api.customization.LspHoverSupport
import com.intellij.platform.lsp.api.customization.LspSemanticTokensSupport
import com.intellij.platform.lsp.api.customization.defaultLspCustomization
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus

@Deprecated(
  "Use LspClientDescriptor",
  ReplaceWith("LspClientDescriptor", "com.intellij.platform.lsp.api.LspClientDescriptor"),
)
abstract class LspServerDescriptor protected constructor(
  project: Project,
  @NlsSafe presentableName: String,
  vararg roots: VirtualFile,
) : LspClientDescriptor(project, presentableName, *roots) {

  @RequiresBackgroundThread
  @Throws(ExecutionException::class)
  override fun startServerProcess(): OSProcessHandler {
    return super.startServerProcess() as OSProcessHandler
  }

  override val lspCustomization: LspCustomization = DeprecatedLspCustomization(this)

  //<editor-fold desc="Deprecated stuff.">

  /**
   * @see LspCustomization.goToDefinitionCustomizer
   */
  @Deprecated("Use lspCustomization.goToDefinitionCustomizer instead",
              ReplaceWith("lspCustomization.goToDefinitionCustomizer is LspGoToDefinitionSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspGoToDefinitionSupport: Boolean = defaultLspCustomization.goToDefinitionCustomizer is LspGoToDefinitionSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.goToDefinitionCustomizer is LspGoToDefinitionSupport else field

  /**
   * @see LspCustomization.goToTypeDefinitionCustomizer
   */
  @Deprecated("Use lspCustomization.goToTypeDefinitionCustomizer instead",
              ReplaceWith("lspCustomization.goToTypeDefinitionCustomizer is LspGoToTypeDefinitionSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspGoToTypeDefinitionSupport: Boolean = defaultLspCustomization.goToTypeDefinitionCustomizer is LspGoToTypeDefinitionSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.goToTypeDefinitionCustomizer is LspGoToTypeDefinitionSupport else field

  /**
   * @see LspCustomization.hoverCustomizer
   */
  @Deprecated("Use lspCustomization.hoverCustomizer instead", ReplaceWith("lspCustomization.hoverCustomizer is LspHoverSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspHoverSupport: Boolean = defaultLspCustomization.hoverCustomizer is LspHoverSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.hoverCustomizer is LspHoverSupport else field

  /**
   * @see LspCustomization.completionCustomizer
   */
  @Deprecated("Use lspCustomization.completionCustomizer instead",
              ReplaceWith("lspCustomization.completionCustomizer as? LspCompletionSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspCompletionSupport: LspCompletionSupport? = defaultLspCustomization.completionCustomizer as? LspCompletionSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.completionCustomizer as? LspCompletionSupport else field

  /**
   * @see LspCustomization.semanticTokensCustomizer
   */
  @Deprecated("Use lspCustomization.semanticTokensCustomizer instead",
              ReplaceWith("lspCustomization.semanticTokensCustomizer as? LspSemanticTokensSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspSemanticTokensSupport: LspSemanticTokensSupport? =
    defaultLspCustomization.semanticTokensCustomizer as? LspSemanticTokensSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.semanticTokensCustomizer as? LspSemanticTokensSupport else field

  /**
   * @see LspCustomization.diagnosticsCustomizer
   */
  @Deprecated("Use lspCustomization.diagnosticsCustomizer instead",
              ReplaceWith("lspCustomization.diagnosticsCustomizer as? LspDiagnosticsSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspDiagnosticsSupport: LspDiagnosticsSupport? = defaultLspCustomization.diagnosticsCustomizer as? LspDiagnosticsSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.diagnosticsCustomizer as? LspDiagnosticsSupport else field

  /**
   * @see LspCustomization.codeActionsCustomizer
   */
  @Deprecated("Use lspCustomization.codeActionsCustomizer instead",
              ReplaceWith("lspCustomization.codeActionsCustomizer as? LspCodeActionsSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspCodeActionsSupport: LspCodeActionsSupport? = defaultLspCustomization.codeActionsCustomizer as? LspCodeActionsSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.codeActionsCustomizer as? LspCodeActionsSupport else field

  /**
   * @see LspCustomization.commandsCustomizer
   */
  @Deprecated("Use lspCustomization.commandsCustomizer instead", ReplaceWith("lspCustomization.commandsCustomizer as? LspCommandsSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspCommandsSupport: LspCommandsSupport? = defaultLspCustomization.commandsCustomizer as? LspCommandsSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.commandsCustomizer as? LspCommandsSupport else field

  /**
   * @see LspCustomization.formattingCustomizer
   */
  @Deprecated("Use lspCustomization.formattingCustomizer instead",
              ReplaceWith("lspCustomization.formattingCustomizer as? LspFormattingSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspFormattingSupport: LspFormattingSupport? = defaultLspCustomization.formattingCustomizer as? LspFormattingSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.formattingCustomizer as? LspFormattingSupport else field

  /**
   * @see LspCustomization.findReferencesCustomizer
   */
  @Deprecated("Use lspCustomization.findReferencesCustomizer instead",
              ReplaceWith("lspCustomization.findReferencesCustomizer as? LspFindReferencesSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspFindReferencesSupport: LspFindReferencesSupport? =
    defaultLspCustomization.findReferencesCustomizer as? LspFindReferencesSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.findReferencesCustomizer as? LspFindReferencesSupport else field

  /**
   * @see LspCustomization.documentColorCustomizer
   */
  @Deprecated("Use lspCustomization.documentColorCustomizer instead",
              ReplaceWith("lspCustomization.documentColorCustomizer as? LspDocumentColorSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspDocumentColorSupport: LspDocumentColorSupport? = defaultLspCustomization.documentColorCustomizer as? LspDocumentColorSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.documentColorCustomizer as? LspDocumentColorSupport else field

  /**
   * @see LspCustomization.documentLinkCustomizer
   */
  @Deprecated("Use lspCustomization.documentLinkCustomizer instead",
              ReplaceWith("lspCustomization.documentLinkCustomizer as? LspDocumentLinkSupport"))
  @ApiStatus.ScheduledForRemoval
  open val lspDocumentLinkSupport: LspDocumentLinkSupport? = defaultLspCustomization.documentLinkCustomizer as? LspDocumentLinkSupport
    get() = if (lspCustomization !is DeprecatedLspCustomization) lspCustomization.documentLinkCustomizer as? LspDocumentLinkSupport else field

  //</editor-fold>

  companion object {
    @Deprecated("Use LspClientDescriptor.LOG", ReplaceWith("LspClientDescriptor.LOG", "com.intellij.platform.lsp.api.LspClientDescriptor"))
    @JvmField
    val LOG: Logger = LspClientDescriptor.LOG

    @Deprecated(
      "Use LspClientDescriptor.getLanguageId",
      ReplaceWith("LspClientDescriptor.getLanguageId(file)", "com.intellij.platform.lsp.api.LspClientDescriptor"),
    )
    fun getLanguageId(file: VirtualFile): String = LspClientDescriptor.getLanguageId(file)
  }
}

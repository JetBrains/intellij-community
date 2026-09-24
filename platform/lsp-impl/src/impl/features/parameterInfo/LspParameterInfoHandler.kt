package com.intellij.platform.lsp.impl.features.parameterInfo

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.platform.lsp.api.customization.LspSignatureHelpDisabled
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.psi.PsiElement
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureInformation

internal class LspParameterInfoHandler : ParameterInfoHandler<PsiElement, LspParameterInfoContext>, DumbAware {
  override fun findElementForParameterInfo(context: CreateParameterInfoContext): PsiElement? {
    if (context.file.project.isDefault) return null

    val virtualFile = context.file.virtualFile?.let { (it as? VirtualFileWindow)?.delegate ?: it } ?: return null
    for (client in LspClientManagerImpl.getInstanceImpl(context.file.project).getClientsWithThisFileOpen(virtualFile)) {
      if (client.descriptor.lspCustomization.signatureHelpCustomizer is LspSignatureHelpDisabled) continue
      if (!client.supportsSignatureHelp(virtualFile)) continue
      val signatureHelp = client.requestExecutor.getSignatureHelp(virtualFile, context.offset) ?: continue
      if (signatureHelp.signatures.isNotEmpty()) {
        context.setItemsToShow(arrayOf<Any>(LspParameterInfoContext(signatureHelp, client)))
        return context.file
      }
    }
    return null
  }

  override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): PsiElement? {
    return if (context.objectsToView.isNotEmpty()) context.file else null
  }

  override fun showParameterInfo(element: PsiElement, context: CreateParameterInfoContext) {
    context.showHint(element, context.offset, this)
  }

  override fun updateParameterInfo(parameterOwner: PsiElement, context: UpdateParameterInfoContext) {
    if (context.parameterOwner != parameterOwner) {
      context.removeHint()
      return
    }
    val storedContext = context.objectsToView?.firstOrNull() as? LspParameterInfoContext ?: return
    val virtualFile = context.file.virtualFile?.let { (it as? VirtualFileWindow)?.delegate ?: it } ?: return
    val signatureHelp = storedContext.client.requestExecutor.getSignatureHelp(virtualFile, context.offset)
    if (signatureHelp == null || signatureHelp.signatures.isEmpty()) {
      context.removeHint()
      return
    }
    // `updateUI()` must render the same response that the current parameter index is calculated from
    storedContext.signatureHelp = signatureHelp
    context.setCurrentParameter(getActiveParameterIndex(signatureHelp))
  }

  override fun updateUI(infoContext: LspParameterInfoContext?, context: ParameterInfoUIContext) {
    if (infoContext == null) {
      context.isUIComponentEnabled = false
      return
    }

    val signature = getActiveSignature(infoContext.signatureHelp)
    val text = signature?.label
    if (text.isNullOrEmpty()) {
      context.isUIComponentEnabled = false
      return
    }

    // `currentParameterIndex` is -1 if no parameter is active
    val highlightRange = getParameterRanges(signature).getOrNull(context.currentParameterIndex)

    context.setupUIComponentPresentation(
      text,
      highlightRange?.startOffset ?: -1,
      highlightRange?.endOffset ?: -1,
      false,  // not disabled
      false,  // no strikeout
      false,  // not disabled before highlight
      context.defaultParameterColor
    )
  }

  private fun getParameterRanges(signature: SignatureInformation): List<TextRange?> {
    val signatureLabel = signature.label
    var searchFrom = 0
    return signature.parameters.orEmpty().map { parameter ->
      val range = parameter.label.map(
        { text ->
          // The spec only requires the string to be a substring of the signature label.
          // Searching after the previous parameter helps when the same text is found earlier in the label.
          val start = signatureLabel.indexOf(text, searchFrom).takeIf { it >= 0 } ?: signatureLabel.indexOf(text)
          if (text.isNotEmpty() && start >= 0) TextRange(start, start + text.length) else null
        },
        { offsets ->
          val start = offsets.first
          val end = offsets.second
          if (start in 0..end && end <= signatureLabel.length) TextRange(start, end) else null
        },
      )
      if (range != null) searchFrom = range.endOffset
      range
    }
  }

  private fun getActiveSignature(signatureHelp: SignatureHelp): SignatureInformation? {
    val signatures = signatureHelp.signatures
    return signatures.getOrNull(signatureHelp.activeSignature ?: 0) ?: signatures.firstOrNull()
  }

  /**
   * @return the index of the active parameter in the active signature, or -1 if no parameter is active
   */
  private fun getActiveParameterIndex(signatureHelp: SignatureHelp): Int {
    val signature = getActiveSignature(signatureHelp) ?: return -1
    val parameterCount = signature.parameters?.size ?: 0
    if (parameterCount == 0) return -1

    // Per spec v3.18, `null` means that no parameter is active, while an omitted `SignatureInformation.activeParameter` falls back
    // to `SignatureHelp.activeParameter`, and an omitted `SignatureHelp.activeParameter` defaults to 0.
    // lsp4j deserializes both an omitted value and `null` as `null`, so `null` in `SignatureInformation.activeParameter` falls back
    // to `SignatureHelp.activeParameter`, and `null` there means that no parameter is active.
    val signatureActiveParameter = signature.activeParameter
    if (signatureActiveParameter != null) {
      // The spec doesn't specify the out-of-range case for `SignatureInformation.activeParameter`
      return if (signatureActiveParameter in 0..<parameterCount) signatureActiveParameter else -1
    }

    val index = signatureHelp.activeParameter ?: return -1
    // Per spec, if `SignatureHelp.activeParameter` lies outside the range of the active signature parameters, it defaults to 0
    return if (index in 0..<parameterCount) index else 0
  }
}

internal class LspParameterInfoContext(
  // The latest response to the `textDocument/signatureHelp` request; updated in `LspParameterInfoHandler.updateParameterInfo`
  @Volatile var signatureHelp: SignatureHelp,
  val client: LspClientImpl,
)

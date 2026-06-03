package com.intellij.platform.lsp.impl.features.parameterInfo

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.lsp.api.customization.LspSignatureHelpDisabled
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
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
    val currentParameter = getCurrentParameterIndex(signatureHelp, context.offset, context.file)
    context.setCurrentParameter(currentParameter)
  }

  override fun updateUI(infoContext: LspParameterInfoContext?, context: ParameterInfoUIContext) {
    if (infoContext == null) {
      context.isUIComponentEnabled = false
      return
    }

    val signatureHelp = infoContext.signatureHelp
    val activeSignature = signatureHelp.activeSignature ?: 0

    if (activeSignature >= signatureHelp.signatures.size) {
      context.isUIComponentEnabled = false
      return
    }

    val signature = signatureHelp.signatures[activeSignature]
    val activeParameter = context.currentParameterIndex

    val text = signature.label
    if (text.isEmpty()) {
      context.isUIComponentEnabled = false
      return
    }

    val parameterRanges = getParameterRanges(signature)

    val hasHighlight = parameterRanges.size >= 2 &&
                       activeParameter >= 0 &&
                       activeParameter * 2 + 1 < parameterRanges.size

    val startIndex = if (hasHighlight) parameterRanges[activeParameter * 2] else -1
    val endIndex = if (hasHighlight) parameterRanges[activeParameter * 2 + 1] else -1

    context.setupUIComponentPresentation(
      text,
      startIndex,
      endIndex,
      false,  // not disabled
      false,  // no strikeout
      false,  // not disabled before highlight
      context.defaultParameterColor
    )
  }

  private fun getParameterRanges(signature: SignatureInformation): IntArray {
    val parameters = signature.parameters ?: return intArrayOf()
    val ranges = mutableListOf<Int>()

    for (param in parameters) {
      val label = param.label
      when {
        label.isLeft -> {
          val paramLabel = label.left!!
          val startIndex = signature.label.indexOf(paramLabel)
          if (startIndex >= 0) {
            ranges.add(startIndex)
            ranges.add(startIndex + paramLabel.length)
          }
        }
        label.isRight -> {
          val range = label.right!!
          ranges.add(range.first)
          ranges.add(range.second)
        }
      }
    }

    return ranges.toIntArray()
  }

  @Suppress("UNUSED_PARAMETER")
  private fun getCurrentParameterIndex(signatureHelp: SignatureHelp, offset: Int, file: PsiFile): Int {
    return signatureHelp.activeParameter
           ?: signatureHelp.activeSignature?.let { signatureHelp.signatures[it].activeParameter }
           ?: 0
  }
}

internal data class LspParameterInfoContext(
  val signatureHelp: SignatureHelp,
  val client: LspClientImpl,
)
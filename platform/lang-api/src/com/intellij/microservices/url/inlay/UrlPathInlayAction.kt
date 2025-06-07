package com.intellij.microservices.url.inlay

import com.intellij.codeInsight.hints.InlayPresentationFactory
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.microservices.url.references.UrlPathContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsContexts.ListItem
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import java.awt.event.MouseEvent
import javax.swing.Icon

interface UrlPathInlayAction {
  val icon: Icon

  @get:ListItem
  val name: String

  @RequiresEdt
  fun actionPerformed(file: PsiFile, editor: Editor, urlPathContext: UrlPathContext, mouseEvent: MouseEvent) {
  }

  @RequiresEdt
  fun actionPerformed(file: PsiFile, editor: Editor, urlPathInlayHint: UrlPathInlayHint, mouseEvent: MouseEvent) {
    actionPerformed(file, editor, urlPathInlayHint.context, mouseEvent)
  }

  @RequiresReadLock
  fun isAvailable(file: PsiFile, urlPathInlayHint: UrlPathInlayHint): Boolean
}

@ApiStatus.Internal
interface UrlPathInlayActionService {
  fun getAvailableActions(file: PsiFile, hint: UrlPathInlayHint): List<UrlPathInlayAction>

  fun buildPresentation(editor: Editor, factory: InlayPresentationFactory): InlayPresentation
}
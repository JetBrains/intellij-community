// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.url.inlay

import com.intellij.icons.AllIcons
import com.intellij.microservices.MicroservicesBundle
import com.intellij.microservices.utils.MicroservicesUsageCollector.URL_INLAY_ACTION_TRIGGERED_EVENT
import com.intellij.microservices.utils.MicroservicesUsageCollector.URL_INLAY_FIND_USAGES_ACTION_ID
import com.intellij.microservices.url.references.UrlPathContext
import com.intellij.microservices.url.references.UrlPathReference
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.event.MouseEvent
import java.util.concurrent.Callable
import javax.swing.Icon

@ApiStatus.Internal
class FindUsagesUrlPathInlayAction : UrlPathInlayAction {
  override val icon: Icon
    get() = AllIcons.Javaee.WebService

  override val name: String
    get() = MicroservicesBundle.message("microservices.inlay.find.usages.url.path")

  override fun actionPerformed(file: PsiFile, editor: Editor, urlPathContext: UrlPathContext, mouseEvent: MouseEvent) {
    val project = editor.project ?: return

    URL_INLAY_ACTION_TRIGGERED_EVENT.log(project, URL_INLAY_FIND_USAGES_ACTION_ID)

    ReadAction.nonBlocking(
      Callable {
        urlPathContext.takeIf { it.resolveRequests.any() }
          ?.let { UrlPathReference.Companion.createSearchableElement(project, urlPathContext) }
      })
      .inSmartMode(file.project)
      .coalesceBy(file, FindUsagesUrlPathInlayAction::class.java)
      .finishOnUiThread(ModalityState.defaultModalityState()) { it?.navigate(true) }
      .submit(AppExecutorUtil.getAppExecutorService())
  }

  override fun isAvailable(file: PsiFile, urlPathInlayHint: UrlPathInlayHint): Boolean = true
}
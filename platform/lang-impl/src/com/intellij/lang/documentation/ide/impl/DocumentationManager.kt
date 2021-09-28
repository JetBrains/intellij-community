// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.lookup.*
import com.intellij.ide.util.propComponentProperty
import com.intellij.lang.documentation.ide.actions.DOCUMENTATION_TARGETS_KEY
import com.intellij.lang.documentation.impl.DocumentationRequest
import com.intellij.lang.documentation.impl.documentationRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.lang.ref.WeakReference

@Service
internal class DocumentationManager(private val project: Project) : Disposable {

  companion object {

    fun instance(project: Project): DocumentationManager = project.service()

    var skipPopup: Boolean by propComponentProperty(name = "documentation.skip.popup", defaultValue = false)
  }

  private val cs: CoroutineScope = CoroutineScope(SupervisorJob())

  init {
    project.messageBus.connect().subscribe(LookupManagerListener.TOPIC, LookupManagerListener { _, current ->
      if (current is LookupEx) {
        showDocOnItemChange(current)
      }
    })
  }

  override fun dispose() {
    cs.cancel()
  }

  private val toolWindowManager: DocumentationToolWindowManager get() = DocumentationToolWindowManager.instance(project)

  fun actionPerformed(dataContext: DataContext) {
    EDT.assertIsEdt()

    val editor = dataContext.getData(CommonDataKeys.EDITOR)
    val lookup = LookupManager.getActiveLookup(editor)
    val currentPopup = getPopup()
    if (lookup != null && currentPopup != null) {
      // lookup can't handle actions itself, so we have to handle this case here
      currentPopup.focusPreferredComponent()
      return
    }

    // Explicit invocation moves focus to preview tab (if visible).
    if (toolWindowManager.focusVisiblePreview()) {
      return
    }

    val targets = dataContext.getData(DOCUMENTATION_TARGETS_KEY) ?: return
    val target = targets.firstOrNull() ?: return // TODO multiple targets

    // This happens in the UI thread because IntelliJ action system returns `DocumentationTarget` instance from the `DataContext`,
    // and it's not possible to guarantee that it will still be valid when sent to another thread,
    // so we create pointer and presentation right in the UI thread.
    val request = target.documentationRequest()

    val popupContext = if (lookup != null) {
      LookupPopupContext(lookup)
    }
    else {
      ProjectPopupContext(project, editor)
    }
    showDocumentation(request, popupContext)
  }

  private var popup: WeakReference<AbstractPopup>? = null

  private fun getPopup(): AbstractPopup? {
    EDT.assertIsEdt()
    val popup: AbstractPopup? = popup?.get()
    if (popup == null) {
      return null
    }
    if (!popup.isVisible) {
      // hint's window might've been hidden by AWT without notifying us
      // dispose to remove the popup from IDE hierarchy and avoid leaking components
      popup.cancel()
      check(this.popup == null)
      return null
    }
    return popup
  }

  private fun setPopup(popup: AbstractPopup) {
    EDT.assertIsEdt()
    this.popup = WeakReference(popup)
    Disposer.register(popup) {
      EDT.assertIsEdt()
      this.popup = null
    }
  }

  private fun showDocumentation(request: DocumentationRequest, popupContext: PopupContext) {
    if (skipPopup) {
      toolWindowManager.previewInToolWindow(request)
      return
    }
    if (getPopup() != null) {
      return
    }
    val popup = showDocumentationPopup(project, request, popupContext)
    setPopup(popup)
  }

  private fun showDocOnItemChange(lookup: LookupEx) {
    val autoShowRequests = autoShowRequestFlow(lookup) ?: return
    val showDocJob = cs.launch(Dispatchers.EDT) {
      autoShowRequests.collectLatest { request: DocumentationRequest ->
        if (!toolWindowManager.updateVisiblePreview(request)) {
          showDocumentation(request, LookupPopupContext(lookup))
        }
      }
    }
    lookup.addLookupListener(object : LookupListener {
      override fun lookupCanceled(event: LookupEvent) {
        showDocJob.cancel()
        lookup.removeLookupListener(this)
      }
    })
  }
}

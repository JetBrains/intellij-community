// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupEx
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.propComponentProperty
import com.intellij.lang.documentation.InlineDocumentation
import com.intellij.lang.documentation.ide.actions.DOCUMENTATION_TARGETS_KEY
import com.intellij.lang.documentation.impl.DocumentationRequest
import com.intellij.lang.documentation.impl.documentationRequest
import com.intellij.lang.documentation.impl.resolveLink
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.awt.Point
import java.lang.ref.WeakReference

@Service
internal class DocumentationManager(private val project: Project) : Disposable {

  companion object {

    @JvmStatic
    fun instance(project: Project): DocumentationManager = project.service()

    var skipPopup: Boolean by propComponentProperty(name = "documentation.skip.popup", defaultValue = false)
  }

  private val cs: CoroutineScope = CoroutineScope(SupervisorJob())

  override fun dispose() {
    cs.cancel()
  }

  private val toolWindowManager: DocumentationToolWindowManager get() = DocumentationToolWindowManager.instance(project)

  fun actionPerformed(dataContext: DataContext) {
    EDT.assertIsEdt()

    val editor = dataContext.getData(CommonDataKeys.EDITOR)
    val currentPopup = getPopup()
    if (currentPopup != null) {
      // focused popup would eat the shortcut itself
      // => at this point there is an unfocused documentation popup near lookup or search component
      currentPopup.focusPreferredComponent()
      return
    }

    val lookup = LookupManager.getActiveLookup(editor)
    val quickSearchComponent = quickSearchComponent(project)

    if (lookup == null && quickSearchComponent == null) {
      // no popups
      if (toolWindowManager.focusVisiblePreview()) {
        // Explicit invocation moves focus to a visible preview tab.
        return
      }
    }
    else {
      // some popup is already visible
      if (toolWindowManager.hasVisiblePreview()) {
        // don't show another popup is a preview tab is visible, it will be updated
        return
      }
    }

    val targets = dataContext.getData(DOCUMENTATION_TARGETS_KEY) ?: return
    val target = targets.firstOrNull() ?: return // TODO multiple targets

    // This happens in the UI thread because IntelliJ action system returns `DocumentationTarget` instance from the `DataContext`,
    // and it's not possible to guarantee that it will still be valid when sent to another thread,
    // so we create pointer and presentation right in the UI thread.
    val request = target.documentationRequest()

    val popupContext = when {
      lookup != null -> LookupPopupContext(lookup)
      quickSearchComponent != null -> QuickSearchPopupContext(project, quickSearchComponent)
      else -> DefaultPopupContext(project, editor)
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

  internal fun autoShowDocumentationOnItemChange(lookup: LookupEx) {
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

  fun navigateInlineLink(
    url: String,
    documentation: () -> InlineDocumentation?
  ) {
    EDT.assertIsEdt()
    cs.launch(ModalityState.current().asContextElement()) {
      val navigatable = readAction {
        val ownerTarget = documentation()?.ownerTarget
                          ?: return@readAction null
        val linkResult = resolveLink(ownerTarget, url)
        linkResult?.target?.navigatable
      }
      withContext(Dispatchers.EDT) { // will use context modality state
        if (navigatable != null && navigatable.canNavigate()) {
          navigatable.navigate(true)
        }
      }
    }
  }

  fun activateInlineLink(
    url: String,
    documentation: () -> InlineDocumentation?,
    editor: Editor,
    popupPosition: Point
  ) {
    EDT.assertIsEdt()
    cs.launch(ModalityState.current().asContextElement()) {
      val request = readAction {
        val ownerTarget = documentation()?.ownerTarget
                          ?: return@readAction null
        val linkResult = resolveLink(ownerTarget, url)
        linkResult?.target?.documentationRequest()
      }
      withContext(Dispatchers.EDT) {
        if (request == null) {
          BrowserUtil.browseAbsolute(url)
          return@withContext
        }
        showDocumentation(request, InlinePopupContext(project, editor, popupPosition))
      }
    }
  }
}

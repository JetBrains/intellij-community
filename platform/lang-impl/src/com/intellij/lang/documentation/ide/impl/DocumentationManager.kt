// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems") // KTIJ-19938

package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.*
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.propComponentProperty
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.documentation.ide.actions.documentationTargets
import com.intellij.lang.documentation.ide.ui.toolWindowUI
import com.intellij.lang.documentation.impl.DocumentationRequest
import com.intellij.lang.documentation.impl.InternalResolveLinkResult
import com.intellij.lang.documentation.impl.documentationRequest
import com.intellij.lang.documentation.impl.resolveLink
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
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
      if (toolWindowManager.focusVisibleReusableTab()) {
        // Explicit invocation moves focus to a visible preview tab.
        return
      }
    }
    else {
      // some popup is already visible
      if (toolWindowManager.hasVisibleAutoUpdatingTab()) {
        // don't show another popup is a preview tab is visible, it will be updated
        return
      }
    }

    val targets = documentationTargets(dataContext)
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
    cs.showDocumentation(request, popupContext)
  }

  private var popup: WeakReference<AbstractPopup>? = null

  val isPopupVisible: Boolean
    get() {
      EDT.assertIsEdt()
      return popup?.get()?.isVisible == true
    }

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

  private fun CoroutineScope.showDocumentation(request: DocumentationRequest, popupContext: PopupContext) {
    if (skipPopup) {
      toolWindowManager.showInToolWindow(request)
      return
    }
    else if (toolWindowManager.updateVisibleReusableTab(request)) {
      return
    }

    if (getPopup() != null) {
      return
    }
    val (browser, browseJob) = DocumentationBrowser.createBrowserAndGetJob(project, request)
    val popup = createDocumentationPopup(project, browser, popupContext)
    setPopup(popup)

    showPopupLater(popup, browseJob, popupContext)
  }

  internal fun autoShowDocumentationOnItemChange(lookup: LookupEx) {
    val settings = CodeInsightSettings.getInstance()
    if (!settings.AUTO_POPUP_JAVADOC_INFO) {
      return
    }
    val delay = settings.JAVADOC_INFO_DELAY.toLong()
    val showDocJob = autoShowDocumentationOnItemChange(lookup, delay)
    lookup.addLookupListener(object : LookupListener {
      override fun itemSelected(event: LookupEvent): Unit = lookupClosed()
      override fun lookupCanceled(event: LookupEvent): Unit = lookupClosed()
      private fun lookupClosed() {
        showDocJob.cancel()
        lookup.removeLookupListener(this)
      }
    })
  }

  private fun autoShowDocumentationOnItemChange(lookup: LookupEx, delay: Long): Job {
    val elements: Flow<LookupElement> = lookup.elementFlow()
    val mapper = lookupElementToRequestMapper(lookup)
    return cs.launch(Dispatchers.EDT + ModalityState.current().asContextElement()) {
      elements.collectLatest {
        handleElementChange(lookup, it, delay, mapper)
      }
    }
  }

  private suspend fun handleElementChange(
    lookup: LookupEx,
    lookupElement: LookupElement,
    delay: Long,
    mapper: suspend (LookupElement) -> DocumentationRequest?
  ) {
    if (getPopup() != null) {
      return // return here to avoid showing another popup if the current one gets cancelled during the delay
    }
    if (!LookupManagerImpl.isAutoPopupJavadocSupportedBy(lookupElement)) {
      return
    }
    delay(delay)
    if (getPopup() != null) {
      // the user might've explicitly invoked the action during the delay
      return // return here to not compute the request unnecessarily
    }
    if (toolWindowManager.hasVisibleAutoUpdatingTab()) {
      return // don't show a documentation popup if an auto-updating tab is visible, it will be updated
    }
    val request = withContext(Dispatchers.Default) {
      mapper(lookupElement)
    }
    if (request == null) {
      return
    }
    coroutineScope {
      showDocumentation(request, LookupPopupContext(lookup))
    }
  }

  fun navigateInlineLink(
    url: String,
    targetSupplier: () -> DocumentationTarget?
  ) {
    EDT.assertIsEdt()
    cs.launch(Dispatchers.EDT + ModalityState.current().asContextElement(), start = CoroutineStart.UNDISPATCHED) {
      val result = withContext(Dispatchers.IO) {
        resolveLink(targetSupplier, url, DocumentationTarget::navigatable)
      }
      if (result is InternalResolveLinkResult.Value) {
        val navigatable = result.value
        if (navigatable != null && navigatable.canNavigate()) {
          navigatable.navigate(true)
        }
      }
    }
  }

  fun activateInlineLink(
    url: String,
    targetSupplier: () -> DocumentationTarget?,
    editor: Editor,
    popupPosition: Point
  ) {
    EDT.assertIsEdt()
    cs.launch(Dispatchers.EDT + ModalityState.current().asContextElement(), start = CoroutineStart.UNDISPATCHED) {
      activateInlineLinkS(targetSupplier, url, editor, popupPosition)
    }
  }

  /**
   * @return `true` if the request was handled,
   * or `false` if nothing happened (e.g. [url] was not resolved, or [targetSupplier] returned `null`)
   */
  suspend fun activateInlineLinkS(
    targetSupplier: () -> DocumentationTarget?,
    url: String,
    editor: Editor,
    popupPosition: Point,
  ): Boolean = coroutineScope {
    val pauseAutoUpdateHandle = toolWindowManager.getVisibleAutoUpdatingContent()?.toolWindowUI?.pauseAutoUpdate()
    try {
      val result = withContext(Dispatchers.Default) {
        resolveLink(targetSupplier, url)
      }
      if (result !is InternalResolveLinkResult.Value) {
        BrowserUtil.browseAbsolute(url)
      }
      else {
        showDocumentation(result.value, InlinePopupContext(project, editor, popupPosition))
        true
      }
    }
    finally {
      pauseAutoUpdateHandle?.let(Disposer::dispose)
    }
  }
}

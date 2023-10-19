// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.documentation.actions.ShowQuickDocInfoAction.Companion.CODEASSISTS_QUICKJAVADOC_CTRLN_FEATURE
import com.intellij.codeInsight.documentation.actions.ShowQuickDocInfoAction.Companion.CODEASSISTS_QUICKJAVADOC_LOOKUP_FEATURE
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupEx
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl
import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.ide.util.propComponentProperty
import com.intellij.lang.documentation.ide.ui.toolWindowUI
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
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.impl.DocumentationRequest
import com.intellij.platform.backend.documentation.impl.InternalResolveLinkResult
import com.intellij.platform.backend.documentation.impl.documentationRequest
import com.intellij.platform.backend.documentation.impl.resolveLink
import com.intellij.platform.ide.documentation.DOCUMENTATION_TARGETS
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.childScope
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.ApiStatus
import java.awt.Point
import java.lang.ref.WeakReference

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class DocumentationManager(private val project: Project, private val cs: CoroutineScope) : Disposable {
  companion object {
    fun getInstance(project: Project): DocumentationManager = project.service()

    var skipPopup: Boolean by propComponentProperty(name = "documentation.skip.popup", defaultValue = false)
  }

  // separate scope is needed for the ability to cancel its children
  private val popupScope: CoroutineScope = cs.childScope()

  override fun dispose() {
    cs.cancel()
  }

  private val toolWindowManager: DocumentationToolWindowManager get() = DocumentationToolWindowManager.instance(project)

  fun actionPerformed(dataContext: DataContext, popupDependencies: Disposable? = null) {
    EDT.assertIsEdt()

    val editor = dataContext.getData(CommonDataKeys.EDITOR)
    val currentPopup = getPopup()
    if (currentPopup != null) {
      // focused popup would eat the shortcut itself
      // => at this point there is an unfocused documentation popup near lookup or search component
      DocumentationPopupFocusService.getInstance(project).focusExistingPopup(currentPopup)
      return
    }

    val secondaryPopupContext = lookupPopupContext(editor)?.also {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CODEASSISTS_QUICKJAVADOC_LOOKUP_FEATURE)
    } ?: quickSearchPopupContext(project)?.also {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CODEASSISTS_QUICKJAVADOC_CTRLN_FEATURE)
    }
    if (secondaryPopupContext == null) {
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

    val targets = dataContext.getData(DOCUMENTATION_TARGETS)
    // This happens in the UI thread because IntelliJ action system returns `DocumentationTarget` instance from the `DataContext`,
    // and it's not possible to guarantee that it will still be valid when sent to another thread,
    // so we create pointers and presentations right in the UI thread.
    val requests = targets?.map { it.documentationRequest() }

    if (requests.isNullOrEmpty()) return
    val popupContext = secondaryPopupContext ?: DefaultPopupContext(project, editor)
    showDocumentation(requests, popupContext, popupDependencies)
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

  private fun setPopup(popup: AbstractPopup, popupDependencies: Disposable?) {
    EDT.assertIsEdt()
    this.popup = WeakReference(popup)
    Disposer.register(popup) {
      EDT.assertIsEdt()
      this.popup = null
    }
    popupDependencies?.let { Disposer.register(popup, it) }
  }

  private fun showDocumentation(requests: List<DocumentationRequest>,
                                popupContext: PopupContext,
                                popupDependencies: Disposable? = null) {
    val initial = requests.first()
    if (skipPopup) {
      toolWindowManager.showInToolWindow(requests)
      return
    }
    else if (toolWindowManager.updateVisibleReusableTab(initial)) {
      return
    }

    if (getPopup() != null) {
      return
    }
    popupScope.coroutineContext.job.cancelChildren()
    popupScope.launch(context = Dispatchers.EDT + ModalityState.current().asContextElement(), start = CoroutineStart.UNDISPATCHED) {
      val popup = showDocumentationPopup(project, requests, popupContext)
      setPopup(popup, popupDependencies)
    }
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
    showDocumentation(listOf(request), LookupPopupContext(lookup))
  }

  fun navigateInlineLink(
    url: String,
    targetSupplier: () -> DocumentationTarget?
  ) {
    EDT.assertIsEdt()
    cs.launch(Dispatchers.EDT + ModalityState.current().asContextElement(), start = CoroutineStart.UNDISPATCHED) {
      val result = withContext(Dispatchers.IO + ClientId.current.asContextElement()) {
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
        browseAbsolute(project, url)
      }
      else {
        showDocumentation(listOf(result.value), InlinePopupContext(project, editor, popupPosition))
        true
      }
    }
    finally {
      pauseAutoUpdateHandle?.let(Disposer::dispose)
    }
  }
}

fun isDocumentationPopupVisible(project: Project): Boolean = DocumentationManager.getInstance(project).isPopupVisible
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.navigation.BackgroundUpdaterTaskBase
import com.intellij.codeInsight.navigation.ImplementationSearcher
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.ui.GenericListComponentUpdater
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts.PopupTitle
import com.intellij.openapi.util.Ref
import com.intellij.reference.SoftReference
import com.intellij.ui.WindowMoveListener
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.popup.PopupPositionManager
import com.intellij.ui.popup.PopupUpdateProcessor
import com.intellij.usages.Usage
import com.intellij.usages.UsageView
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.util.*
import java.util.function.Consumer

@ApiStatus.Internal
class ImplementationPopupManager {
  private var currentPopup: Reference<JBPopup>? = null
  private var currentTask: Reference<ImplementationsUpdaterTask>? = null

  fun showImplementationsPopup(session: ImplementationViewSession,
                               implementationElements: List<ImplementationViewElement>,
                               elementIndex: Int,
                               title: @PopupTitle String,
                               couldPinPopup: Boolean,
                               invokedFromEditor: Boolean,
                               invokedByShortcut: Boolean,
                               updatePopup: (lookupItemObject: Any?) -> Unit) {
    val usageView = Ref<UsageView?>()
    val showInFindWindowProcessor = if (couldPinPopup) {
      Consumer<ImplementationViewComponent> { component ->
        usageView.set(component.showInUsageView())
        currentTask = null
      }
    }
    else {
      null
    }

    var popup = SoftReference.dereference(currentPopup)
    if (popup is AbstractPopup && popup.isVisible()) {
      val component = popup.component as? ImplementationViewComponent
      if (component != null) {
        component.update(implementationElements, elementIndex)
        component.setShowInFindWindowProcessor(showInFindWindowProcessor)
        updateInBackground(session, component, popup, usageView)
        if (invokedByShortcut) {
          popup.focusPreferredComponent()
        }
        return
      }
    }

    val component = ImplementationViewComponent(implementationElements, elementIndex).apply {
      setShowInFindWindowProcessor(showInFindWindowProcessor)
    }
    if (component.hasElementsToShow()) {
      popup = createPopup(session, component, invokedFromEditor, updatePopup)
      updateInBackground(session, component, popup, usageView)
      component.setHint(popup, title)

      PopupPositionManager.positionPopupInBestPosition(popup, session.editor, DataManager.getInstance().getDataContext())
      currentPopup = WeakReference(popup)
    }
  }

  private fun createPopup(session: ImplementationViewSession,
                          component: ImplementationViewComponent,
                          invokedFromEditor: Boolean,
                          updatePopup: (lookupItemObject: Any?) -> Unit,
  ): JBPopup {
    val updateProcessor: PopupUpdateProcessor = object : PopupUpdateProcessor(session.project) {
      override fun updatePopup(lookupItemObject: Any?) {
        updatePopup(lookupItemObject)
      }

      override fun onClosed(event: LightweightWindowEvent) {
        component.cleanup()
      }
    }

    val popupBuilder = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(component, component.preferredFocusableComponent)
      .setProject(session.project)
      .addListener(updateProcessor)
      .addUserData(updateProcessor)
      .setDimensionServiceKey(session.project, DocumentationManager.JAVADOC_LOCATION_AND_SIZE, false)
      .setResizable(true)
      .setMovable(true)
      .setRequestFocus(invokedFromEditor && LookupManager.getActiveLookup(session.editor) == null)
      .setCancelCallback {
        SoftReference.dereference(currentTask)?.cancelTask()
        true
      }

    val listener = WindowMoveListener()
    listener.installTo(component)

    val popup = popupBuilder.createPopup()
    Disposer.register(popup, session)
    Disposer.register(popup, Disposable { listener.uninstallFrom(component) })

    return popup
  }

  private fun updateInBackground(session: ImplementationViewSession,
                                 component: ImplementationViewComponent,
                                 popup: JBPopup,
                                 usageView: Ref<UsageView?>) {
    SoftReference.dereference(currentTask)?.cancelTask()

    if (!session.needUpdateInBackground()) return  // already found

    val task = ImplementationsUpdaterTask(session, component).apply {
      val updater = ImplementationViewComponentUpdater(component, if (session.elementRequiresIncludeSelf()) 1 else 0)
      init(popup, updater, usageView)
    }
    currentTask = WeakReference(task)
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))
  }

  private class ImplementationsUpdaterTask(private val mySession: ImplementationViewSession,
                                           private val myComponent: ImplementationViewComponent) : BackgroundUpdaterTaskBase<ImplementationViewElement?>(
    mySession.project, ImplementationSearcher.getSearchingForImplementations(), null) {
    private var myElements: List<ImplementationViewElement>? = null
    override fun getCaption(size: Int): String? {
      return null
    }

    override fun createUsage(element: ImplementationViewElement): Usage? {
      return element.usage
    }

    override fun run(indicator: ProgressIndicator) {
      super.run(indicator)
      myElements = mySession.searchImplementationsInBackground(indicator) {
        updateComponent(it)
      }
    }

    override fun getCurrentSize(): Int {
      return myElements?.size ?: super.getCurrentSize()
    }

    override fun onSuccess() {
      if (!cancelTask()) {
        myElements?.let { myComponent.update(it, myComponent.index) }
      }
      super.onSuccess()
    }
  }


  private class ImplementationViewComponentUpdater(private val myComponent: ImplementationViewComponent,
                                                   private val myIncludeSelfIdx: Int) : GenericListComponentUpdater<ImplementationViewElement?> {
    override fun paintBusy(paintBusy: Boolean) {
      //todo notify busy
    }

    override fun replaceModel(data: List<ImplementationViewElement?>) {
      val elements = myComponent.elements
      val startIdx = elements.size - myIncludeSelfIdx
      val result: MutableList<ImplementationViewElement?> = ArrayList()
      Collections.addAll(result, *elements)
      result.addAll(data.subList(startIdx, data.size))
      myComponent.update(result, myComponent.index)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): ImplementationPopupManager = service()
  }
}

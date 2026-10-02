// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.emptyState

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.fileEditor.impl.EditorEmptyStateComponentProvider
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.fileEditor.impl.buildEditorEmptyStateComponentOnUiThread
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.platform.ide.nonModalWelcomeScreen.isNonModalWelcomeScreenEnabled
import com.intellij.platform.ide.nonModalWelcomeScreen.isWelcomeExperienceProjectSync
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeRightTabContentProvider
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabImpl
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.prepareDefaultBody
import com.intellij.platform.ide.nonModalWelcomeScreen.welcomeScreenStartupTracer
import com.intellij.ui.ClientProperty
import com.intellij.util.PlatformUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.swing.JComponent

private val TAB_KEY = Key<WelcomeScreenRightTabImpl>("EMPTY_PROVIDER")

internal class WelcomeScreenEmptyStateProvider : EditorEmptyStateComponentProvider {
  override fun isAvailable(splitters: EditorsSplitters): Boolean {
    val result = (splitters.manager.project.isWelcomeExperienceProjectSync() || PlatformUtils.isDataGrip()) &&
                 isNonModalWelcomeScreenEnabled && WelcomeRightTabContentProvider.getSingleExtension() != null
    if (result) {
      splitters.putClientProperty("WELCOME_EXPERIENCE", true)
    }
    return result
  }

  override fun isFullContent(splitters: EditorsSplitters) = isAvailable(splitters)

  override fun claimsFocus(splitters: EditorsSplitters) = isAvailable(splitters)

  override suspend fun createComponent(splitters: EditorsSplitters): JComponent? {
    if (!isAvailable(splitters)) {
      return null
    }

    val provider = WelcomeRightTabContentProvider.getSingleExtension() ?: return null
    val project = splitters.manager.project
    // Owns every resource of the tab from the first one. The tab registers itself under it on the UI thread.
    val owner = Disposer.newDisposable("welcome right tab")
    var handedOver = false
    try {
      return withContext(welcomeScreenStartupTracer.span("welcome right tab creating")) {
        val body = prepareDefaultBody(project, provider, owner)
        withContext(ModalityState.any().asContextElement()) {
          buildEditorEmptyStateComponentOnUiThread {
            val tab = WelcomeScreenRightTabImpl(project, provider, body, owner)
            ClientProperty.put(tab.component, TAB_KEY, tab)
            tab.component
          }
        }
      }.also { handedOver = true }
    }
    finally {
      if (!handedOver) {
        disposeOnEdt(owner)
      }
    }
  }

  override fun disposeComponent(component: JComponent) {
    ClientProperty.get(component, TAB_KEY)?.let { Disposer.dispose(it.owner) }
  }

  override fun getPreferredFocusedComponent(component: JComponent): JComponent? {
    return ClientProperty.get(component, TAB_KEY)?.getPreferredFocusedComponent()
  }
}

/**
 * Disposes [owner] on the EDT, also when the caller is cancelled.
 *
 * The function reads the EDT dispatcher inside [NonCancellable], because the first read creates a service.
 * A cancelled coroutine cannot create a service.
 */
private suspend fun disposeOnEdt(owner: Disposable) {
  withContext(NonCancellable) {
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      Disposer.dispose(owner)
    }
  }
}

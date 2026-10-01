// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.emptyState

import com.intellij.openapi.fileEditor.impl.EditorEmptyStateComponentProvider
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.platform.ide.nonModalWelcomeScreen.isNonModalWelcomeScreenEnabled
import com.intellij.platform.ide.nonModalWelcomeScreen.isWelcomeExperienceProjectSync
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeRightTabContentProvider
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabImpl
import com.intellij.platform.ide.nonModalWelcomeScreen.welcomeScreenStartupTracer
import com.intellij.ui.ClientProperty
import com.intellij.util.PlatformUtils
import kotlinx.coroutines.withContext
import javax.swing.JComponent

private val COMPONENT_KEY = Key<WelcomeScreenRightTabImpl>("EMPTY_PROVIDER")

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

    return withContext(welcomeScreenStartupTracer.span("welcome right tab creating")) {
      val provider = WelcomeRightTabContentProvider.getSingleExtension() ?: return@withContext null
      val componentProvider = WelcomeScreenRightTabImpl(splitters.manager.project, provider)
      val component = componentProvider.component

      ClientProperty.put(component, COMPONENT_KEY, componentProvider)

      component
    }
  }

  override fun disposeComponent(component: JComponent) {
    ClientProperty.get(component, COMPONENT_KEY)?.let(Disposer::dispose)
  }

  override fun getPreferredFocusedComponent(component: JComponent): JComponent? {
    return ClientProperty.get(component, COMPONENT_KEY)?.getPreferredFocusedComponent()
  }
}
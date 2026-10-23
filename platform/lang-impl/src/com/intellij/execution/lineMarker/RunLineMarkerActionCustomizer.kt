// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.lineMarker

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.components.serviceOrNull
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.annotations.ApiStatus

/**
 * Provides a way to customize presentation and behavior of gutter icons created by [RunLineMarkerContributorBase] for cases when such
 * action may correspond to run configurations.
 * This service is intended to be implemented only inside `intellij.platform.execution.impl` module.
 */
@ApiStatus.Internal
interface RunLineMarkerActionCustomizer {
  fun wrapChildren(origin: AnAction, element: SmartPsiElementPointer<PsiElement>): List<AnAction>?
  fun customizeDataSnapshot(sink: DataSink, element: SmartPsiElementPointer<PsiElement>)
  fun appendShortcut(tooltip: String): String

  companion object {
    private fun getInstance(): RunLineMarkerActionCustomizer? = serviceOrNull()

    @JvmStatic
    fun wrapLineMarkerActionChildren(origin: AnAction, element: SmartPsiElementPointer<PsiElement>): List<AnAction>? {
      return getInstance()?.wrapChildren(origin, element)
    }

    @JvmStatic
    fun customizeDataSnapshotForAction(sink: DataSink, element: SmartPsiElementPointer<PsiElement>) {
      getInstance()?.customizeDataSnapshot(sink, element)
    }

    @JvmStatic
    fun appendShortcutToTooltip(tooltip: String): String {
      return getInstance()?.appendShortcut(tooltip) ?: tooltip
    }
  }
}
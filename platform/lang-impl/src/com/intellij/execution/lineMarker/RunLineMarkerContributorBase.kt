// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.lineMarker

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * An extension point that allows adding a gutter icon that can start a related action.
 * Unlike `com.intellij.execution.lineMarker.RunLineMarkerContributor`, it doesn't use classes from the run configurations subsystem.
 *
 * **This is a temporary API**, it'll be changed in future versions. For now, `com.intellij.execution.lineMarker.RunLineMarkerContributor`
 * should be used instead unless it's necessary to have the implementation loaded even if `intellij.platform.execution` module is not
 * available.
 */
@ApiStatus.Experimental
interface RunLineMarkerContributorBase : PossiblyDumbAware {
  fun getInfo(element: PsiElement): InfoBase?
  fun getSlowInfo(element: PsiElement): InfoBase? = null
  fun producesAllPossibleConfigurations(psiFile: PsiFile): Boolean = true

  interface InfoBase {
    val icon: Icon
    val actions: List<AnAction>
    val tooltipProvider: ((PsiElement) -> String?)?
    fun shouldReplace(other: InfoBase): Boolean = false
  }
}

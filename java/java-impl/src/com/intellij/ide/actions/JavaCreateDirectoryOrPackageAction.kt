// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.util.ThreeState
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
public open class JavaCreateDirectoryOrPackageAction : CreateDirectoryOrPackageAction(true) {
  public override fun isPackage(project: Project, directories: List<PsiDirectory>): Boolean {
    if (super.isPackage(project, directories)) return true
    return CreateJavaClassActionAvailabilityOverride.getAvailabilityOverride(directories.toTypedArray()) == ThreeState.YES
  }
}

internal class JavaNewDirActionCustomizer : ActionConfigurationCustomizer, ActionConfigurationCustomizer.LightCustomizeStrategy {
  override suspend fun customize(actionRegistrar: ActionRuntimeRegistrar) {
    actionRegistrar.replaceAction("NewDir", JavaCreateDirectoryOrPackageAction())
  }
}

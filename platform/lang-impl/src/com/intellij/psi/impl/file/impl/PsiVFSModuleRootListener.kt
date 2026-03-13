// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener

internal class PsiVFSModuleRootListener(listenerProject: Project) : ModuleRootListener {
  private val service = listenerProject.service<PsiVFSModuleRootListenerImpl>()

  override fun beforeRootsChange(event: ModuleRootEvent) {
    service.beforeRootsChange(event.isCausedByFileTypesChange)
  }

  override fun rootsChanged(event: ModuleRootEvent) {
    service.rootsChanged(event.isCausedByFileTypesChange)
  }
}
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ThreeState
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SilentChangeVetoer {
  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName.create<SilentChangeVetoer>("com.intellij.silentChangeVetoer")
  }

  fun canChangeFileSilently(project: Project, virtualFile: VirtualFile): ThreeState = ThreeState.UNSURE
}
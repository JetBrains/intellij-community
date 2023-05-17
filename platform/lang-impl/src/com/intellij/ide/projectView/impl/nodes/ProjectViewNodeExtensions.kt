// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.openapi.vfs.VirtualFile

internal fun <T> ProjectViewNode<T>.getVirtualFileForNodeOrItsPSI(): VirtualFile? =
  virtualFile ?: if (this is AbstractPsiBasedNode) virtualFileForValue else null

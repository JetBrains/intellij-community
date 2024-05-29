// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.PresentableChange
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
data class ActivityDiffData(val presentableChanges: Iterable<PresentableChange>,
                            val diffMode: DirectoryDiffMode,
                            val isSingleSelection: Boolean)

@ApiStatus.Experimental
interface ActivityFileChange : PresentableChange {
  fun createProducer(project: Project?): ChangeDiffRequestChain.Producer
}
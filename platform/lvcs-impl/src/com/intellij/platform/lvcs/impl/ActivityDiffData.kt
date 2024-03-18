// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ActivityDiffData {
  fun getPresentableChanges(project: Project): Iterable<ChangeViewDiffRequestProcessor.Wrapper>
}
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface ReadActionSupport {

  fun smartModeConstraint(project: Project): ReadConstraint

  fun committedDocumentsConstraint(project: Project): ReadConstraint

  suspend fun <X> executeReadAction(constraints: List<ReadConstraint>, blocking: Boolean, action: () -> X): X

  fun <X, E : Throwable> computeCancellable(action: ThrowableComputable<X, E>): X
}

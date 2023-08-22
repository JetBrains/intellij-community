// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
abstract class PerformanceAssertions {

  abstract fun checkDoesNotAffectHighlighting()

  companion object {

    /**
     * Checks if current code is not called from a performance critical path
     * e.g. code highlighting, [com.intellij.psi.PsiElement.getReference].
     *
     * It is similar to [SlowOperations.assertSlowOperationsAreAllowed] but imposes more strict restrictions.
     */
    @JvmStatic
    fun assertDoesNotAffectHighlighting() {
      ApplicationManager.getApplication().service<PerformanceAssertions>().checkDoesNotAffectHighlighting()
    }

  }

}
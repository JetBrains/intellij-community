// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus.OverrideOnly

@OverrideOnly // no new implementations are expected currently, this 'constraint' may be relaxed later
interface ReadConstraint {

  /**
   * @return `true` if this constraint is satisfied in the current read action, otherwise `false`,
   * in which case [awaitConstraint] will be called
   */
  @RequiresReadLock
  fun isSatisfied(): Boolean

  /**
   * Suspends until it's possible to obtain the read lock to find [isSatisfied] is `true`,
   * for example, if the project is in dumb mode, then it does not make sense to check [isSatisfied] in a loop,
   * instead, this function is called to suspend until it will make sense to check [isSatisfied].
   */
  suspend fun awaitConstraint()

  companion object {

    fun inSmartMode(project: Project): ReadConstraint {
      return ApplicationManager.getApplication().getService(ReadWriteActionSupport::class.java).smartModeConstraint(project)
    }

    fun withDocumentsCommitted(project: Project): ReadConstraint {
      return ApplicationManager.getApplication().getService(ReadWriteActionSupport::class.java).committedDocumentsConstraint(project)
    }
  }
}

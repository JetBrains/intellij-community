// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
interface ReadConstraint {

  /**
   * @return `true` if this constraint is satisfied in the current read action, otherwise `false`, in which case [schedule] will be called
   */
  @RequiresReadLock
  fun isSatisfied(): Boolean

  /**
   * Schedules the [runnable] to be executed when this constraint can be satisfied.
   */
  @RequiresReadLock
  fun schedule(runnable: Runnable)

  companion object {

    fun inSmartMode(project: Project): ReadConstraint {
      return ApplicationManager.getApplication().getService(ReadActionSupport::class.java).smartModeConstraint(project)
    }

    fun withDocumentsCommitted(project: Project): ReadConstraint {
      return ApplicationManager.getApplication().getService(ReadActionSupport::class.java).committedDocumentsConstraint(project)
    }
  }
}

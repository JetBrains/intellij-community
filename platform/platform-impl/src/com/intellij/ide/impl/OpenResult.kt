// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Represents the result of opening a project.
 */
@ApiStatus.Experimental
internal sealed interface OpenResult {

  class Success(val project: Project) : OpenResult

  /**
   * The IDE failed to open the project by using a certain method, the client is free to try to open it some other way.
   */
  object Failure: OpenResult

  /**
   * The opening has been canceled by the user or by the IDE, there should be no further attempts to open this project.
   */
  object Cancel: OpenResult

  companion object {
    @JvmStatic
    fun cancel(): OpenResult = Cancel

    @JvmStatic
    fun failure(): OpenResult = Failure
  }
}
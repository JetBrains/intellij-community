// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Represents the result of opening a project.
 */
@ApiStatus.Experimental
internal interface OpenResult {

  class Success(val project: Project) : OpenResult

  /**
   * The IDE couldn't open the project by using a certain method, the client is free to try to open it some other way.
   */
  object CouldntOpen: OpenResult

  /**
   * The opening has been canceled by the user or by the IDE, there should be no further attempts to open this project.
   */
  object Canceled: OpenResult

  companion object {
    @JvmStatic
    fun canceled(): OpenResult = Canceled

    @JvmStatic
    fun couldntOpen(): OpenResult = CouldntOpen
  }
}
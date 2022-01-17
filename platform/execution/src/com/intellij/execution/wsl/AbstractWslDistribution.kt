// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.NlsSafe

/**
 * This is a temporary interface used for wsl classes transition. Please, do not use it
 */
interface AbstractWslDistribution {
  /**
   * @return Linux path for a file pointed by `windowsPath` or null if unavailable, like \\MACHINE\path
   */
  @NlsSafe
  fun getWslPath(windowsPath: String): String?

  /**
   * @return creates and patches command line, e.g:
   * `ruby -v` => `bash -c "ruby -v"`
   */
  @Throws(ExecutionException::class)
  fun createWslCommandLine(vararg command: String): GeneralCommandLine
}
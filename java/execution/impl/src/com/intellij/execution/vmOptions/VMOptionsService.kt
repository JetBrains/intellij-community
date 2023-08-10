// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.vmOptions

import com.intellij.openapi.components.service
import java.util.concurrent.CompletableFuture

interface VMOptionsService {
  companion object {
    @JvmStatic
    fun getInstance(): VMOptionsService = service()
  }

  fun getOrComputeOptionsForJdk(javaHome: String): CompletableFuture<JdkOptionsData>
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.vmModules

import com.intellij.openapi.components.service
import java.util.concurrent.CompletableFuture

interface VmModulesService {
  companion object {
    @JvmStatic
    fun getInstance(): VmModulesService = service()
  }

  fun getOrComputeModulesForJdk(javaHome: String): CompletableFuture<List<String>>
}
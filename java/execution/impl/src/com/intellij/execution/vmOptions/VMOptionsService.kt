// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.vmOptions

import com.intellij.openapi.components.service
import java.util.concurrent.CompletableFuture

interface VMOptionsService {
  companion object {
    @JvmStatic
    fun getInstance(): VMOptionsService = service()
  }

  /**
   * Calculates all the options for the `java` command.
   */
  fun getOrComputeOptionsForJdk(javaHome: String): CompletableFuture<JdkOptionsData>

  /**
   * Calculates all the options for the `javac` command.
   */
  fun getOrComputeOptionsForJavac(javaHome: String): CompletableFuture<JdkOptionsData> =
    CompletableFuture.completedFuture(JdkOptionsData(emptyList()))

  /**
   * @return options that appears in all `java` commands starting from java 8 except informational (help, version)
   */
  fun getStandardOptions(): JdkOptionsData = JdkOptionsData(emptyList())

  /**
   * @return options that appears in all `javac` commands starting from java 8 except informational (help, version)
   */
  fun getStandardJavacOptions(): JdkOptionsData = JdkOptionsData(emptyList())
}
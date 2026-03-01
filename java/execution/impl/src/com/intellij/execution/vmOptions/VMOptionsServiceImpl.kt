// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.vmOptions

import java.util.concurrent.CompletableFuture

class VMOptionsServiceImpl : VMOptionsService {
  companion object {
    @JvmStatic
    @get:JvmName("getStandardOptionList")
    val STANDARD_OPTION_LIST : List<VMOption> = CommandOptionsExtractor.Java.STANDARD_OPTION_LIST
  }

  override fun getOrComputeOptionsForJdk(javaHome: String): CompletableFuture<JdkOptionsData> {
    return CommandOptionsExtractor.Java.getOrComputeOptions(javaHome)
  }

  override fun getOrComputeOptionsForJavac(javaHome: String): CompletableFuture<JdkOptionsData> {
    return CommandOptionsExtractor.Javac.getOrComputeOptions(javaHome)
  }

  override fun getStandardOptions(): JdkOptionsData = JdkOptionsData(CommandOptionsExtractor.Java.STANDARD_OPTION_LIST)

  override fun getStandardJavacOptions(): JdkOptionsData = JdkOptionsData(CommandOptionsExtractor.Javac.STANDARD_OPTIONS_LIST)
}
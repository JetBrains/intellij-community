// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devServer

import com.intellij.openapi.application.PathManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

fun main(rawArgs: Array<String>) {
  runBlocking(Dispatchers.Default) {
    buildProductInProcess(BuildRequest(
      platformPrefix = System.getProperty("idea.platform.prefix") ?: "idea",
      additionalModules = getAdditionalModules()?.toList() ?: emptyList(),
      homePath = Path.of(PathManager.getHomePath()),
      keepHttpClient = false,
    ))
  }

  System.setProperty("idea.vendor.name", "JetBrains")
  System.setProperty("idea.use.dev.build.server", "true")
  com.intellij.idea.main(rawArgs)
}
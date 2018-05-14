// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File
import java.util.function.Consumer

internal lateinit var logger: Consumer<String>

internal fun log(msg: String) = logger.accept(msg)

internal fun String.splitWithSpace(): List<String> = this.split(" ").filter { it.isNotBlank() }

internal fun String.splitWithTab(): List<String> = this.split("\t".toRegex())

internal fun String.execute(workingDir: File?): String {
  log("Executing command $this")
  val start = System.currentTimeMillis()
  return try {
    val process = ProcessBuilder(*this.splitWithSpace().toTypedArray())
      .directory(workingDir)
      .redirectOutput(ProcessBuilder.Redirect.PIPE)
      .redirectError(ProcessBuilder.Redirect.PIPE)
      .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    process.waitFor()
    output
  }
  finally {
    log("Took ${System.currentTimeMillis() - start} ms")
  }
}
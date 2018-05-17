// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File
import java.util.function.Consumer

internal lateinit var logger: Consumer<String>

internal fun log(msg: String) = logger.accept(msg)

internal fun String.splitWithSpace(): List<String> = this.split(" ").filter { it.isNotBlank() }

internal fun String.splitWithTab(): List<String> = this.split("\t".toRegex())

internal fun String.execute(workingDir: String): String = execute(File(workingDir))

internal fun String.execute(workingDir: File?, silent: Boolean = false): String =
  this.splitWithSpace().execute(workingDir, silent)

internal fun List<String>.execute(workingDir: File?, silent: Boolean = false): String {
  if (!silent) log("Executing command ${this.joinToString(" ")}")
  val start = System.currentTimeMillis()
  return try {
    val process = ProcessBuilder(*this.toTypedArray())
      .directory(workingDir)
      .redirectOutput(ProcessBuilder.Redirect.PIPE)
      .redirectError(ProcessBuilder.Redirect.PIPE)
      .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    process.waitFor()
    output
  }
  finally {
    if (!silent) log("Took ${System.currentTimeMillis() - start} ms")
  }
}

internal fun <T> List<T>.split(eachSize: Int): List<List<T>> {
  if (this.size < eachSize) return listOf(this)
  val result = mutableListOf<List<T>>()
  var start = 0
  while (start < this.size) {
    val sub = this.subList(start, Math.min(start + eachSize, this.size))
    if (!sub.isEmpty()) result += sub
    start += eachSize
  }
  return result
}
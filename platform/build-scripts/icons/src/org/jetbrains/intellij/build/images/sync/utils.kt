// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File
import java.util.function.Consumer

internal lateinit var logger: Consumer<String>

internal fun log(msg: String) = logger.accept(msg)

internal fun String.splitWithSpace(): List<String> = this.splitNotBlank(" ")

internal fun String.splitNotBlank(delimiter : String): List<String> = this.split(delimiter).filter { it.isNotBlank() }

internal fun String.splitWithTab(): List<String> = this.split("\t".toRegex())

internal fun List<String>.execute(workingDir: File?, silent: Boolean = false): String {
  val processCall = {
    val process = ProcessBuilder(*this.filter { it.isNotBlank() }.toTypedArray())
      .directory(workingDir)
      .redirectOutput(ProcessBuilder.Redirect.PIPE)
      .redirectError(ProcessBuilder.Redirect.PIPE)
      .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    process.waitFor()
    output
  }
  return if (silent) {
    processCall()
  }
  else {
    callWithTimer("Executing command ${this.joinToString(" ")}", processCall)
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

internal fun callSafely(call: () -> Unit) {
  try {
    call()
  }
  catch (e: Exception) {
    e.printStackTrace()
    log(e.message ?: e.javaClass.canonicalName)
  }
}

internal fun <T> callWithTimer(msg: String? = null, call: () -> T): T {
  if (msg != null) log(msg)
  val start = System.currentTimeMillis()
  try {
    return call()
  }
  finally {
    log("Took ${System.currentTimeMillis() - start} ms")
  }
}
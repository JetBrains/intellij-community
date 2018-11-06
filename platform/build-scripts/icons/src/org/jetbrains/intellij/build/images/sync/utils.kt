// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

internal lateinit var logger: Consumer<String>

internal fun log(msg: String) = logger.accept(msg)

internal fun String.splitWithSpace(): List<String> = this.splitNotBlank(" ")

internal fun String.splitNotBlank(delimiter: String): List<String> = this.split(delimiter).filter { it.isNotBlank() }

internal fun String.splitWithTab(): List<String> = this.split("\t".toRegex())

internal fun execute(workingDir: File?, vararg command: String, silent: Boolean = false): String {
  val errOutputFile = File.createTempFile("errOutput", "txt")
  val processCall = {
    val process = ProcessBuilder(*command.filter { it.isNotBlank() }.toTypedArray())
      .directory(workingDir)
      .redirectOutput(ProcessBuilder.Redirect.PIPE)
      .redirectError(errOutputFile)
      .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    process.waitFor(1, TimeUnit.MINUTES)
    val error = errOutputFile.readText().trim()
    if (process.exitValue() != 0) {
      error("Command ${command.joinToString(" ")} failed with ${process.exitValue()} : $output\n$error")
    }
    output
  }
  return try {
    if (silent) processCall() else callWithTimer("Executing command ${command.joinToString(" ")}", processCall)
  }
  finally {
    errOutputFile.delete()
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

internal fun <T> callSafely(call: () -> T): T? = try {
  call()
}
catch (e: Exception) {
  e.printStackTrace()
  log(e.message ?: e.javaClass.canonicalName)
  null
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

internal fun <T> retry(maxRetries: Int = 20,
                       secondsBeforeRetry: Long = 30,
                       doRetry: (Throwable) -> Boolean = { true },
                       action: () -> T): T {
  repeat(maxRetries) {
    try {
      return action()
    }
    catch (e: Exception) {
      if (doRetry(e)) {
        log("${it + 1} attempt of $maxRetries has failed. Retrying in ${secondsBeforeRetry}s..")
        TimeUnit.SECONDS.sleep(secondsBeforeRetry)
      }
      else throw e
    }
  }
  error("Unable to complete")
}
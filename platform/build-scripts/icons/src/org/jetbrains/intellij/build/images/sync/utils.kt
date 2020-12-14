// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

internal var logger: Consumer<String> = Consumer { println(it) }

internal fun log(msg: String) = logger.accept(msg)

internal fun String.splitWithSpace(): List<String> = this.splitNotBlank(" ")

internal fun String.splitNotBlank(delimiter: String): List<String> = this.split(delimiter).filter { it.isNotBlank() }

internal fun String.splitWithTab(): List<String> = this.split("\t".toRegex())

internal fun execute(workingDir: Path?, vararg command: String, withTimer: Boolean = false): String {
  val errOutputFile = File.createTempFile("errOutput", "txt")
  val processCall = {
    val process = ProcessBuilder(*command.filter { it.isNotBlank() }.toTypedArray())
      .directory(workingDir?.toFile())
      .redirectOutput(ProcessBuilder.Redirect.PIPE)
      .redirectError(errOutputFile)
      .apply {
        environment()["LANG"] = "en_US.UTF-8"
        if (environment()["GIT_SSH_COMMAND"].isNullOrEmpty()) {
          environment()["GIT_SSH_COMMAND"] = "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no"
        }
      }.start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    process.waitFor(1, TimeUnit.MINUTES)
    val error = errOutputFile.readText().trim()
    if (process.exitValue() != 0) {
      error("Command ${command.joinToString(" ")} failed in ${workingDir?.toAbsolutePath()} with ${process.exitValue()} : $output\n$error")
    }
    output
  }
  return try {
    if (withTimer) callWithTimer("Executing command ${command.joinToString(" ")}", processCall) else processCall()
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
    val sub = this.subList(start, (start + eachSize).coerceAtMost(this.size))
    if (sub.isNotEmpty()) result += sub
    start += eachSize
  }
  return result
}

internal fun <T> callSafely(printStackTrace: Boolean = false, call: () -> T): T? = try {
  call()
}
catch (e: Exception) {
  if (printStackTrace) e.printStackTrace() else log(e.message ?: e::class.java.simpleName)
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
    val number = it + 1
    try {
      return action()
    }
    catch (e: Exception) {
      log("$number attempt of $maxRetries has failed with ${e.message}")
      if (number < maxRetries && doRetry(e)) {
        log("Retrying in ${secondsBeforeRetry}s..")
        TimeUnit.SECONDS.sleep(secondsBeforeRetry)
      }
      else throw e
    }
  }
  error("Unable to complete")
}

internal fun guessEmail(invalidEmail: String): Collection<String> {
  val (username, domain) = invalidEmail.split("@")
  val guesses = mutableListOf(
    username.splitNotBlank(".").joinToString(".", transform = String::capitalize) + "@$domain",
    invalidEmail.toLowerCase()
  )
  if (domain != "jetbrains.com") guesses += "$username@jetbrains.com"
  return guesses
}

internal inline fun <T> protectStdErr(block: () -> T): T {
  val err = System.err
  return try {
    block()
  }
  finally {
    System.setErr(err)
  }
}

private val mutedStream = PrintStream(object : OutputStream() {
  override fun write(b: ByteArray) {}
  override fun write(b: ByteArray, off: Int, len: Int) {}
  override fun write(b: Int) {}
})

internal inline fun <T> muteStdOut(block: () -> T): T {
  val out = System.out
  System.setOut(mutedStream)
  return try {
    block()
  }
  finally {
    System.setOut(out)
  }
}

internal inline fun <T> muteStdErr(block: () -> T): T = protectStdErr {
  System.setErr(mutedStream)
  return block()
}

internal fun Path.isAncestorOf(file: Path): Boolean {
  return file.startsWith(this)
}
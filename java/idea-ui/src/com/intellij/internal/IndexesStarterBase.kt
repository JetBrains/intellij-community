// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.isFile
import com.intellij.util.io.sizeOrNull
import java.io.File
import java.nio.file.Files
import kotlin.system.exitProcess

abstract class IndexesStarterBase(
  private val commandName: String
) : ApplicationStarter {
  final override fun getCommandName() = commandName

  final override fun main(args: Array<out String>) {
    try {
      mainImpl(args)
    } catch (t: Throwable) {
      LOG.error("JDK Indexing failed unexpectedly. ${t.message}", t)
      exitProcess(1)
    }
  }

  protected abstract fun mainImpl(args: Array<out String>)

  protected object LOG {
    fun info(message: String) {
      println(message)
    }

    fun error(message: String, cause: Throwable? = null) {
      Logger.getInstance(
        DumpJdkIndexStarter::class.java).error(message, cause)
      println("ERROR - $message")
      cause?.printStackTrace()
    }
  }

  protected fun Array<out String>.arg(arg: String, default: String? = null): String {
    val key = "/$arg="
    val values = filter { it.startsWith(key) }.map { it.removePrefix(key) }

    if (values.isEmpty() && default != null) {
      return default
    }
    require(values.size == 1) { "Commandline argument $key is missing or defined multiple times" }
    return values.first()
  }

  protected fun Array<out String>.argFile(arg: String, default: String? = null) = File(arg(arg, default)).canonicalFile

  protected fun <Y: Any> runAndCatchNotNull(errorMessage: String, action: () -> Y?) : Y {
    try {
      return action() ?: error("<null> was returned!")
    }
    catch (t: Throwable) {
      throw Error("Failed to $errorMessage. ${t.message}", t)
    }
  }

  protected fun File.sha256(): String {
    val digest = DigestUtil.sha256()
    DigestUtil.updateContentHash(digest, this.toPath())
    return StringUtil.toHexString(digest.digest());
  }

  protected fun File.recreateDir() = apply {
    FileUtil.delete(this)
    FileUtil.createDirectory(this)
  }

  protected fun File.totalSize(): Long {
    if (isFile) return length()
    return Files.walk(this.toPath()).mapToLong {
      when {
        it.isFile() -> java.lang.Long.max(it.sizeOrNull(), 0L)
        else -> 0L
      }
    }.sum()
  }
}

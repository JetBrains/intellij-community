// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.testApp

import com.intellij.terminal.TestJavaMainClassCommand
import kotlin.system.exitProcess

internal object SimpleCliApp {
  @JvmStatic
  fun main(args: Array<String>) {
    val console = System.console() ?: error("No console available")
    val options = readOptions(args)
    val stdout = console.writer()
    stdout.print(options.textToPrint)
    stdout.flush()
    if (options.readInputStringToExit != null) {
      val stdin = console.reader().buffered()
      do {
        val line = stdin.readLine()
        stdout.println("Read line: $line")
        stdout.flush()
      }
      while (line != options.readInputStringToExit)
    }
    if (options.exitCode != 0) {
      exitProcess(options.exitCode)
    }
  }

  fun readOptions(args: Array<String>): Options {
    val textToPrint = args.getOrNull(0) ?: error("No textToPrint specified")
    val exitCode = args.getOrNull(1)?.toIntOrNull() ?: error("No exitCode specified")
    val readInputStringToExit = args.getOrNull(2)
    return Options(textToPrint, exitCode, readInputStringToExit)
  }

  class Options(val textToPrint: String, val exitCode: Int, val readInputStringToExit: String?)

  object NonRuntime {
    fun createCommand(options: Options): TestJavaMainClassCommand = TestJavaMainClassCommand(
      SimpleCliApp::class.java, emptyList(),
      listOf(options.textToPrint, options.exitCode.toString()) + listOfNotNull(options.readInputStringToExit)
    )
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.testApp

import com.intellij.terminal.TestJavaMainClassCommand
import java.io.PrintWriter

internal object SimplePrinterApp {
  @JvmStatic
  fun main(args: Array<String>) {
    val console = System.console() ?: error("No console available")
    val initialOptions = readOptions(args.toList())
    val stdout = console.writer()
    process(stdout, initialOptions)
    val stdin = console.reader().buffered()
    while (true) {
      stdout.print("Input:")
      stdout.flush()
      val line = stdin.readLine() ?: error("Reached end of input stream")
      if (line == EXIT) {
        break
      }
      val option = readOptions(line.split(' ', limit = 2))
      process(stdout, option)
    }
  }

  private fun process(stdout: PrintWriter, options: Options) {
    (1..options.repeatCount).forEach {
      stdout.println(options.textToPrint + it)
    }
    stdout.flush()
  }

  private fun readOptions(args: List<String>): Options {
    val repeatCount = args.getOrNull(0)?.toIntOrNull() ?: error("No repeatCount specified")
    val textToPrint = args.getOrNull(1) ?: error("No textToPrint specified")
    return Options(textToPrint.trim(), repeatCount)
  }

  class Options(val textToPrint: String, val repeatCount: Int)

  const val EXIT: String = "exit"

  object NonRuntime {

    fun createCommand(options: Options): TestJavaMainClassCommand = TestJavaMainClassCommand(
      SimplePrinterApp::class.java, emptyList(),
      listOf(options.repeatCount.toString(), options.textToPrint)
    )
  }
}

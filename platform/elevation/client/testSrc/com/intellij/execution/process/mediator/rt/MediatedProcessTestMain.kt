// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.rt

import java.util.*
import kotlin.system.exitProcess

class MediatedProcessTestMain {
  object True {
    @JvmStatic
    fun main(args: Array<String>) {
      exitProcess(0)
    }
  }

  object False {
    @JvmStatic
    fun main(args: Array<String>) {
      exitProcess(42)
    }
  }

  object Loop {
    @JvmStatic
    fun main(args: Array<String>) {
      repeat(60) {
        Thread.sleep(500)
      }
      exitProcess(1)
    }
  }

  object Echo {
    @JvmStatic
    fun main(args: Array<String>) {
      val scanner = Scanner(System.`in`)
      val printToStderr = args.isNotEmpty() && args[0] == "with_stderr"
      while (scanner.hasNextLine()) {
        val line = scanner.nextLine()
        println(line)

        if (printToStderr) {
          System.err.println(line)
        }
      }
    }
  }

  object StreamInterruptor {
    @JvmStatic
    fun main(args: Array<String>) {
      try {
        System.out.close()
        System.err.close()
        System.`in`.close()
      }
      catch (e: Exception) {
        exitProcess(2)
      }

      repeat(60) {
        Thread.sleep(500)
      }
      exitProcess(1)
    }
  }

  object TestClosedStream {
    @JvmStatic
    fun main(args: Array<String>) {
      Thread.sleep(1500)
      if (System.`in`.read() != -1) {
        exitProcess(42)
      }
      // System.out and System.err (write -> flush) work even output stream is closed
    }
  }

}
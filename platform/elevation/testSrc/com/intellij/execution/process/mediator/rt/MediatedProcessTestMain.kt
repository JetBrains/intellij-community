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
      while (true) {
        Thread.sleep(500)
      }
    }
  }

  object Echo {
    @JvmStatic
    fun main(args: Array<String>) {
      val scanner = Scanner(System.`in`)
      while (scanner.hasNextLine()) {
        println(scanner.nextLine())
      }
    }
  }

}
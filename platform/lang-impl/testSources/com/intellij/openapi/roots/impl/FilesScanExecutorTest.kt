// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.roots.impl.FilesScanExecutor.runOnAllThreads
import com.intellij.testFramework.LightPlatformTestCase

class FilesScanExecutorTest : LightPlatformTestCase() {
  fun testScanExecutorSupportsRecursiveTasks() {
    val testTask = RecursionTestTask()
    val t = Thread(testTask, "testScanExecutorSupportsRecursiveTasks")

    try {
      t.start()
      t.join(5000)
      assertFalse("Tasks are still running, but they should not. Looks like a deadlock.", t.isAlive)
    }
    finally {
      if (t.isAlive) t.interrupt()
    }
  }

  class RecursionTestTask : Runnable {
    override fun run() {
      runOnAllThreads {
        runOnAllThreads {
          runOnAllThreads {
            Thread.yield()
          }
        }
      }
    }
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.logging.jps;

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.awaitLogQueueProcessed
import org.junit.After
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.readText

class JpsFileLoggingTest {
  private val defaultFactory = Logger.getFactory()
  private val logFile = Files.createTempFile("jps", ".log")
  private val log by lazy { Logger.getInstance(JpsFileLoggingTest::class.java) }

  @After
  fun cleanUp() {
    Logger.setFactory(defaultFactory)
    Files.deleteIfExists(logFile)
  }

  private fun `test logging`(debugCategories: String, test: () -> Unit) {
    JpsLoggerFactory.fileLoggerFactory = JpsFileLoggerFactory(logFile, debugCategories)
    Logger.setFactory(JpsLoggerFactory::class.java)
    test()
  }

  @Test
  fun `test debug logging enabled`() {
    `test logging`(debugCategories = "#org.jetbrains") {
      assert(log.isDebugEnabled())
      val debugMessage = "Debug message should be printed"
      log.debug(debugMessage)
      awaitLogQueueProcessed()
      assert(logFile.readText().contains(debugMessage))
    }
  }

  @Test
  fun `test debug logging disabled`() {
    `test logging`(debugCategories = "") {
      assert(!log.isDebugEnabled())
      log.debug("Debug message should not be printed")
      awaitLogQueueProcessed()
      assert(logFile.readText().isEmpty())
    }
  }
}
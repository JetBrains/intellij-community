// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.tasks

import com.intellij.testFramework.rules.InMemoryFsRule
import org.assertj.core.api.Assertions
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.util.*

class TaskTest {
  @JvmField
  @Rule
  val fsRule = InMemoryFsRule()

  companion object {
    @JvmStatic
    val logger = object : System.Logger {
      override fun getName() = ""

      override fun isLoggable(level: System.Logger.Level) = true

      override fun log(level: System.Logger.Level, bundle: ResourceBundle?, message: String, thrown: Throwable?) {
        if (level == System.Logger.Level.ERROR) {
          throw RuntimeException(message, thrown)
        }
        else {
          println(message)
        }
      }

      override fun log(level: System.Logger.Level, bundle: ResourceBundle?, message: String, vararg params: Any?) {
        log(level, bundle = null, message = message, thrown = null)
      }

      override fun log(level: System.Logger.Level, message: String) {
        log(level = level, bundle = null, message = message, thrown = null)
      }
    }
  }

  @Test
  fun `broken plugins`() {
    val targetFile = fsRule.fs.getPath("/result")
    buildBrokenPlugins(targetFile, "2020.3", isInDevelopmentMode = false, logger)
    val data = Files.readAllBytes(targetFile)
    Assertions.assertThat(data).isNotEmpty()
    Assertions.assertThat(data[0]).isEqualTo(1)
  }
}
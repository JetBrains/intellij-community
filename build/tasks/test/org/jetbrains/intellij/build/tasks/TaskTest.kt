// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UsePropertyAccessSyntax")
package org.jetbrains.intellij.build.tasks

import com.intellij.testFramework.rules.InMemoryFsExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.nio.file.Files
import java.util.*

class TaskTest {
  @RegisterExtension
  @JvmField
  val fs = InMemoryFsExtension()

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
    val targetFile = fs.root.resolve("result")
    buildBrokenPlugins(targetFile, "2020.3", isInDevelopmentMode = false)
    DataInputStream(BufferedInputStream(Files.newInputStream(targetFile), 32_000)).use { stream ->
      assertThat(stream.readByte()).isEqualTo(2)
      assertThat(stream.readUTF()).isEqualTo("2020.3")
    }
  }
}
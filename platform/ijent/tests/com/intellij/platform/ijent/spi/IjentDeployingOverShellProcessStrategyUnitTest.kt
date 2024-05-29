// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.be
import io.kotest.matchers.collections.beIn
import io.kotest.matchers.should
import io.kotest.matchers.string.include
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class IjentDeployingOverShellProcessStrategyUnitTest {
  @Nested
  inner class `test createDeployingContext` {
    @Test
    fun `all commands with busybox`(): Unit = runBlocking {
      val context = createDeployingContext { commands ->
        commands.map { "/bin/$it" }
      }
      context should be(DeployingContext(
        chmod = "/bin/chmod",
        cp = "/bin/cp",
        cut = "/bin/cut",
        env = "/bin/env",
        getent = "/bin/getent",
        head = "/bin/head",
        mktemp = "/bin/mktemp",
        uname = "/bin/uname",
        whoami = "/bin/whoami",
      ))
    }

    @Test
    fun `all commands without busybox`(): Unit = runBlocking {
      val context = createDeployingContext { commands ->
        "busybox" should beIn(commands)
        (commands - "busybox").map { "/bin/$it" }
      }
      context should be(DeployingContext(
        chmod = "/bin/chmod",
        cp = "/bin/cp",
        cut = "/bin/cut",
        env = "/bin/env",
        getent = "/bin/getent",
        head = "/bin/head",
        mktemp = "/bin/mktemp",
        uname = "/bin/uname",
        whoami = "/bin/whoami",
      ))
    }

    @Test
    fun `all commands without chmod`(): Unit = runBlocking {
      val context = createDeployingContext { commands ->
        "chmod" should beIn(commands)
        (commands - "chmod").map { "/bin/$it" }
      }
      context should be(DeployingContext(
        chmod = "/bin/busybox chmod",
        cp = "/bin/cp",
        cut = "/bin/cut",
        env = "/bin/env",
        getent = "/bin/getent",
        head = "/bin/head",
        mktemp = "/bin/mktemp",
        uname = "/bin/uname",
        whoami = "/bin/whoami",
      ))
    }

    @Test
    fun `only busybox`(): Unit = runBlocking {
      val context = createDeployingContext { commands ->
        "busybox" should beIn(commands)
        listOf("/bin/busybox")
      }
      context should be(DeployingContext(
        chmod = "/bin/busybox chmod",
        cp = "/bin/busybox cp",
        cut = "/bin/busybox cut",
        env = "/bin/busybox env",
        getent = "/bin/busybox getent",
        head = "/bin/busybox head",
        mktemp = "/bin/busybox mktemp",
        uname = "/bin/busybox uname",
        whoami = "/bin/busybox whoami",
      ))
    }

    @Test
    fun `no chmod and no busybox`(): Unit = runBlocking {
      val errorAssertion = shouldThrow<IjentStartupError.IncompatibleTarget> {
        createDeployingContext { commands ->
          "busybox" should beIn(commands)
          "chmod" should beIn(commands)
          commands - "busybox" - "chmod"
        }
      }
      errorAssertion.message should include("busybox")
    }
  }
}
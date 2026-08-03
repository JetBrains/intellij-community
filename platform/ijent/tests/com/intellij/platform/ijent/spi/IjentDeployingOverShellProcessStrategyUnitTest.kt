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
        commands
      }
      context should be(DeployingContext(
        chmod = "chmod",
        cp = "cp",
        cut = "cut",
        env = "env",
        head = "head",
        mktemp = "mktemp",
        rm = "rm",
        sed = "sed",
        tail = "tail",
        uname = "uname",
        whoami = "whoami",
        getent = "getent",
        id = "id",
      ))
    }

    @Test
    fun `all commands without busybox`(): Unit = runBlocking {
      val context = createDeployingContext { commands ->
        "busybox" should beIn(commands)
        (commands - "busybox")
      }
      context should be(DeployingContext(
        chmod = "chmod",
        cp = "cp",
        cut = "cut",
        env = "env",
        head = "head",
        mktemp = "mktemp",
        rm = "rm",
        sed = "sed",
        tail = "tail",
        uname = "uname",
        whoami = "whoami",
        getent = "getent",
        id = "id",
      ))
    }

    @Test
    fun `all commands without chmod`(): Unit = runBlocking {
      val context = createDeployingContext { commands ->
        "chmod" should beIn(commands)
        (commands - "chmod")
      }
      context should be(DeployingContext(
        chmod = "busybox chmod",
        cp = "cp",
        cut = "cut",
        env = "env",
        head = "head",
        mktemp = "mktemp",
        rm = "rm",
        sed = "sed",
        tail = "tail",
        uname = "uname",
        whoami = "whoami",
        getent = "getent",
        id = "id",
      ))
    }

    /** macOS has neither `getent` nor `busybox`, but the deployment must work there anyway. */
    @Test
    fun `all commands without getent and without busybox`(): Unit = runBlocking {
      val context = createDeployingContext { commands ->
        "busybox" should beIn(commands)
        "getent" should beIn(commands)
        commands - "busybox" - "getent"
      }
      context should be(DeployingContext(
        chmod = "chmod",
        cp = "cp",
        cut = "cut",
        env = "env",
        head = "head",
        mktemp = "mktemp",
        rm = "rm",
        sed = "sed",
        tail = "tail",
        uname = "uname",
        whoami = "whoami",
        getent = null,
        id = "id",
      ))
    }

    @Test
    fun `only busybox`(): Unit = runBlocking {
      val context = createDeployingContext { commands ->
        "busybox" should beIn(commands)
        listOf("busybox")
      }
      context should be(DeployingContext(
        chmod = "busybox chmod",
        cp = "busybox cp",
        cut = "busybox cut",
        env = "busybox env",
        head = "busybox head",
        mktemp = "busybox mktemp",
        rm = "busybox rm",
        sed = "busybox sed",
        tail = "busybox tail",
        uname = "busybox uname",
        whoami = "busybox whoami",
        getent = "busybox getent",
        id = "busybox id",
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
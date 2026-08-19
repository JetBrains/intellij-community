// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Unit tests for [classifyOpensshStderrLine] — the OpenSSH stderr level classification used by
 * [IjentSessionMediatorUtils] so that OpenSSH's own `debug1/2/3` chatter is logged at DEBUG and its
 * problems at WARN/ERROR instead of everything landing at INFO (IJPL-253080).
 */
class IjentOpensshStderrLevelTest {
  @Test
  fun `debug1, debug2 and debug3 all map to DEBUG`() {
    classifyOpensshStderrLine("debug1: Reading configuration data /etc/ssh/ssh_config") shouldBe OpensshStderrLevel.DEBUG
    classifyOpensshStderrLine("debug2: resolve_addr: could not resolve name fakebox as address") shouldBe OpensshStderrLevel.DEBUG
    classifyOpensshStderrLine("debug3: send packet: type 5") shouldBe OpensshStderrLevel.DEBUG
  }

  @Test
  fun `error and fatal map to ERROR`() {
    classifyOpensshStderrLine("error: Permission denied (publickey).") shouldBe OpensshStderrLevel.ERROR
    classifyOpensshStderrLine("fatal: Timeout before authentication for 10.0.0.1") shouldBe OpensshStderrLevel.ERROR
  }

  @Test
  fun `warning notices map to WARN`() {
    classifyOpensshStderrLine("warning: something odd happened") shouldBe OpensshStderrLevel.WARN
    classifyOpensshStderrLine("Warning: Permanently added 'host' (ED25519) to the list of known hosts.") shouldBe OpensshStderrLevel.WARN
  }

  @Test
  fun `lines without a recognizable level tag are left unclassified`() {
    // Version banner: no level tag at all.
    classifyOpensshStderrLine("OpenSSH_9.6p1 Ubuntu-3ubuntu13.15, OpenSSL 3.0.13 30 Jan 2024") shouldBe null
    // progname-prefixed fatal without a level tag must NOT be misread as an error-level line.
    classifyOpensshStderrLine("ssh: Could not resolve host \"fakebox\"") shouldBe null
    // A sentence that merely contains a colon must not be treated as a tag.
    classifyOpensshStderrLine("Connection to host closed: bye") shouldBe null
    // A leading timestamp (IJent's own format, normally matched earlier) is not an OpenSSH tag.
    classifyOpensshStderrLine("2026-08-17T10:00:00.000Z INFO ijent::-something") shouldBe null
    // An empty line.
    classifyOpensshStderrLine("") shouldBe null
  }

  @Test
  fun `an embedded openssh message still classifies by its leading tag`() {
    // Real sample: OpenSSH wraps its own progname message inside a debug1 line.
    classifyOpensshStderrLine("debug1: ssh: Could not resolve hostname fakebox.bad1.test.: Name or service not known") shouldBe OpensshStderrLevel.DEBUG
  }
}

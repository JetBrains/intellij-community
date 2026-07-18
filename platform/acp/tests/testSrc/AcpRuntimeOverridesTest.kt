// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.acp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class AcpRuntimeOverridesTest {
  @Test
  fun `matching runtime replaces command and prefixes runner arguments`() {
    val workingDir = Path.of("agent")
    val config = AcpAgentStartConfig.create(
      command = "npx",
      baseArgs = listOf("package"),
      acpArgs = listOf("--acp"),
      env = mapOf("TOKEN" to "value"),
      workingDir = workingDir,
    )

    val resolved = AcpRuntimeOverrides(
      mapOf("npx" to AcpRuntimeCommandOverride("/tools/bun", listOf("x", "--bun")))
    ).applyTo(config)

    assertThat(resolved.command).isEqualTo("/tools/bun")
    assertThat(resolved.baseArgs).containsExactly("x", "--bun", "package")
    assertThat(resolved.acpArgs).containsExactly("--acp")
    assertThat(resolved.env).containsExactlyEntriesOf(config.env)
    assertThat(resolved.workingDir).isEqualTo(workingDir)
  }

  @Test
  fun `unmatched runtime preserves start config`() {
    val config = AcpAgentStartConfig.create(
      command = "uvx",
      baseArgs = listOf("package"),
      acpArgs = emptyList(),
      env = emptyMap(),
    )

    assertThat(
      AcpRuntimeOverrides(mapOf("npx" to AcpRuntimeCommandOverride("/tools/bun"))).applyTo(config)
    ).isSameAs(config)
  }
}

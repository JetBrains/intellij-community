// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.acp

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for [AcpAgentId] type-safe wrapper and its implementations.
 */
class AcpAgentIdTest {

  @Nested
  inner class ParseMethod {
    @Test
    fun `parse creates custom agent from acp prefix`() {
      val id = AcpAgentId.parse("acp.goose")
      assertThat(id).isInstanceOf(AcpCustomAgentId::class.java)
      assertThat(id.rawId).isEqualTo("goose")
      assertThat(id.fullId).isEqualTo("acp.goose")
    }

    @Test
    fun `parse creates registry agent from acp registry prefix`() {
      val id = AcpAgentId.parse("acp.registry.goose")
      assertThat(id).isInstanceOf(AcpRegistryAgentId::class.java)
      assertThat(id.rawId).isEqualTo("goose")
      assertThat(id.fullId).isEqualTo("acp.registry.goose")
    }

    @Test
    fun `parse throws on raw id without prefix`() {
      assertThatThrownBy { AcpAgentId.parse("goose") }
        .isInstanceOf(IllegalArgumentException::class.java)
        .hasMessageContaining("missing prefix")
    }

    @Test
    fun `parse handles complex agent names`() {
      val customId = AcpAgentId.parse("acp.claude-code-v2")
      assertThat(customId.rawId).isEqualTo("claude-code-v2")

      val registryId = AcpAgentId.parse("acp.registry.claude-code-v2")
      assertThat(registryId.rawId).isEqualTo("claude-code-v2")
    }
  }

  @Nested
  inner class CustomAgentIdFromRaw {
    @Test
    fun `fromRaw creates custom agent id from raw id`() {
      val id = AcpCustomAgentId("goose")
      assertThat(id.rawId).isEqualTo("goose")
      assertThat(id.fullId).isEqualTo("acp.goose")
    }
  }

  @Nested
  inner class RegistryAgentIdFromRaw {
    @Test
    fun `fromRaw creates registry agent id from raw id`() {
      val id = AcpRegistryAgentId("goose")
      assertThat(id.rawId).isEqualTo("goose")
      assertThat(id.fullId).isEqualTo("acp.registry.goose")
    }
  }

  @Nested
  inner class IsAcpAgentMethod {
    @Test
    fun `isAcpAgent returns true for custom agent ids`() {
      assertThat(AcpAgentId.isAcpAgent("acp.goose")).isTrue()
      assertThat(AcpAgentId.isAcpAgent("acp.claude-code")).isTrue()
    }

    @Test
    fun `isAcpAgent returns true for registry agent ids`() {
      assertThat(AcpAgentId.isAcpAgent("acp.registry.goose")).isTrue()
      assertThat(AcpAgentId.isAcpAgent("acp.registry.claude-code")).isTrue()
    }

    @Test
    fun `isAcpAgent returns false for non-prefixed ids`() {
      assertThat(AcpAgentId.isAcpAgent("goose")).isFalse()
      assertThat(AcpAgentId.isAcpAgent("chat")).isFalse()
      assertThat(AcpAgentId.isAcpAgent("")).isFalse()
    }
  }

  @Nested
  inner class SanitizeMethod {
    @Test
    fun `sanitize converts to lowercase`() {
      assertThat(AcpAgentId.sanitize("MyAgent")).isEqualTo("myagent")
      assertThat(AcpAgentId.sanitize("UPPERCASE")).isEqualTo("uppercase")
    }

    @Test
    fun `sanitize replaces spaces with hyphens`() {
      assertThat(AcpAgentId.sanitize("My Custom Agent")).isEqualTo("my-custom-agent")
      assertThat(AcpAgentId.sanitize("Agent With Spaces")).isEqualTo("agent-with-spaces")
    }

    @Test
    fun `sanitize replaces special characters with hyphens`() {
      assertThat(AcpAgentId.sanitize("Agent@123")).isEqualTo("agent-123")
      assertThat(AcpAgentId.sanitize("Agent#Special!")).isEqualTo("agent-special")
    }

    @Test
    fun `sanitize handles emoji by replacing with hyphens`() {
      assertThat(AcpAgentId.sanitize("Goose 🪿")).isEqualTo("goose")
      assertThat(AcpAgentId.sanitize("Agent 🤖 Bot")).isEqualTo("agent-bot")
    }

    @Test
    fun `sanitize collapses multiple non-alphanumeric characters`() {
      assertThat(AcpAgentId.sanitize("Agent---Name")).isEqualTo("agent-name")
      assertThat(AcpAgentId.sanitize("Agent   Name")).isEqualTo("agent-name")
      assertThat(AcpAgentId.sanitize($$"Agent@#$Name")).isEqualTo("agent-name")
    }

    @Test
    fun `sanitize trims leading and trailing hyphens`() {
      assertThat(AcpAgentId.sanitize("---agent---")).isEqualTo("agent")
      assertThat(AcpAgentId.sanitize("  Agent  ")).isEqualTo("agent")
      assertThat(AcpAgentId.sanitize("@@@agent@@@")).isEqualTo("agent")
    }

    @Test
    fun `sanitize preserves already valid ids`() {
      assertThat(AcpAgentId.sanitize("goose")).isEqualTo("goose")
      assertThat(AcpAgentId.sanitize("claude-code")).isEqualTo("claude-code")
      assertThat(AcpAgentId.sanitize("agent123")).isEqualTo("agent123")
    }

    @Test
    fun `sanitize handles empty string`() {
      assertThat(AcpAgentId.sanitize("")).isEqualTo("")
      assertThat(AcpAgentId.sanitize("   ")).isEqualTo("")
      assertThat(AcpAgentId.sanitize("@#$")).isEqualTo("")
    }
  }

  @Nested
  inner class FromNameMethod {
    @Test
    fun `fromName creates custom id from display name`() {
      val id = AcpCustomAgentId.fromName("My Custom Agent")
      assertThat(id).isInstanceOf(AcpCustomAgentId::class.java)
      assertThat(id.rawId).isEqualTo("my-custom-agent")
      assertThat(id.fullId).isEqualTo("acp.my-custom-agent")
    }

    @Test
    fun `fromName handles emoji in name`() {
      val id = AcpCustomAgentId.fromName("Goose 🪿")
      assertThat(id.rawId).isEqualTo("goose")
      assertThat(id.fullId).isEqualTo("acp.goose")
    }

    @Test
    fun `fromName handles special characters`() {
      val id = AcpCustomAgentId.fromName("Agent@Special#123!")
      assertThat(id.rawId).isEqualTo("agent-special-123")
      assertThat(id.fullId).isEqualTo("acp.agent-special-123")
    }
  }

  @Nested
  inner class ToStringMethod {
    @Test
    fun `toString returns fullId for custom agent`() {
      val id = AcpCustomAgentId("goose")
      assertThat(id.toString()).isEqualTo("acp.goose")
    }

    @Test
    fun `toString returns fullId for registry agent`() {
      val id = AcpRegistryAgentId("goose")
      assertThat(id.toString()).isEqualTo("acp.registry.goose")
    }
  }

  @Nested
  inner class EqualityTests {
    @Test
    fun `custom ids with same raw value are equal`() {
      val id1 = AcpCustomAgentId("goose")
      val id2 = AcpCustomAgentId("goose")
      val id3 = AcpAgentId.parse("acp.goose")

      assertThat(id1).isEqualTo(id2)
      assertThat(id1).isEqualTo(id3)
    }

    @Test
    fun `registry ids with same raw value are equal`() {
      val id1 = AcpRegistryAgentId("goose")
      val id2 = AcpRegistryAgentId("goose")
      val id3 = AcpAgentId.parse("acp.registry.goose")

      assertThat(id1).isEqualTo(id2)
      assertThat(id1).isEqualTo(id3)
    }

    @Test
    fun `custom and registry ids with same raw value are NOT equal`() {
      val customId = AcpCustomAgentId("goose")
      val registryId = AcpRegistryAgentId("goose")

      assertThat(customId.fullId).isNotEqualTo(registryId.fullId)
      assertThat(customId.rawId).isEqualTo(registryId.rawId) // But raw IDs are same
    }

    @Test
    fun `ids with different raw values are not equal`() {
      val id1 = AcpCustomAgentId("goose")
      val id2 = AcpCustomAgentId("claude-code")

      assertThat(id1).isNotEqualTo(id2)
    }
  }

  @Nested
  inner class TypeChecks {
    @Test
    fun `parsed custom agent is AcpCustomAgentId`() {
      val id = AcpAgentId.parse("acp.goose")
      assertThat(id is AcpCustomAgentId).isTrue()
      assertThat(id is AcpRegistryAgentId).isFalse()
    }

    @Test
    fun `parsed registry agent is AcpRegistryAgentId`() {
      val id = AcpAgentId.parse("acp.registry.goose")
      assertThat(id is AcpRegistryAgentId).isTrue()
      assertThat(id is AcpCustomAgentId).isFalse()
    }
  }
}

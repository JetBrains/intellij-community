package com.intellij.codeowners

import com.intellij.codeowners.model.GroupName
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GroupNameTest {

  @Test
  fun `uniqueId builds github-friendly id replacing dots and ampersands`() {
    val name = GroupName.build(null, "Core.Team & Tools")
    // dots -> " dot ", ampersands -> " and ", other non-alnum/hyphen (spaces) -> '-'
    Assertions.assertEquals("core-dot-team-and-tools", name.uniqueId)
  }

  @Test
  fun `validateName allows valid characters and build stores original stringName`() {
    val original = "Alpha_12-3 .&"
    // should not throw
    GroupName.validateName(source = null, stringName = original)
    val gn = GroupName.build(null, original)
    Assertions.assertEquals(original, gn.toString())
  }

  @Test
  fun `validateName throws on invalid characters and message contains source when provided`() {
    val ex = assertThrows<IllegalArgumentException> {
      GroupName.validateName(source = "groups.yaml", stringName = "Invalid/Name!")
    }
    Assertions.assertTrue(ex.message!!.contains("groups.yaml:"))
    Assertions.assertTrue(ex.message!!.contains("must match"))
  }

  @Test
  fun `GroupName equality is case-insensitive via uniqueId`() {
    val a = GroupName.build(null, "Platform Team")
    val b = GroupName.build(null, "platform team")
    Assertions.assertEquals(a, b)
    Assertions.assertEquals(a.hashCode(), b.hashCode())
  }

  @Test
  fun `GroupName inequality when uniqueId differs due to dot and ampersand expansions`() {
    val withSymbols = GroupName.build(null, "Core.Team & Tools")
    val plainWords = GroupName.build(null, "Core Team and Tools")
    // "Core.Team & Tools" -> core-dot-team-and-tools, while the second becomes core-team-and-tools
    Assertions.assertNotEquals(withSymbols, plainWords)
  }
}

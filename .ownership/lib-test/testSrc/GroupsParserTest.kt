package com.intellij.codeowners

import com.intellij.codeowners.model.GroupName
import com.intellij.codeowners.model.OrgUnit
import com.intellij.codeowners.serialization.GroupsParser
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

class GroupsParserTest {

  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `loads single group with members and normalizes orgUnit`() {
    writeGroups(
      """
      groups:
        - name: Core Team
          orgUnit: "bob#'JetBrains \\ IntelliJ'"
          members:
            - email: core1@jetbrains.com
            - email: core2@jetbrains.com
      """.trimIndent()
    )

    val groups = GroupsParser.loadGroups(tempDir)

    Assertions.assertEquals(1, groups.nameToGroup.size)
    val key = GroupName.build(null, "Core Team")
    val group = groups.nameToGroup[key]
    Assertions.assertNotNull(group)
    Assertions.assertEquals(key, group!!.name)
    Assertions.assertNull(group.parent)
    Assertions.assertEquals(2, group.members.size)
    // orgUnit normalization: strip bob# and surrounding quotes, keep exact path and trimming
    Assertions.assertEquals("JetBrains \\ IntelliJ", (group.orgUnit as OrgUnit.HiBobTeam).teamFullPath)
  }

  @Test
  fun `builds parent-child hierarchy`() {
    writeGroups(
      """
      groups:
        - name: Platform
          orgUnit: "bob#'JetBrains \\ Platform'"
          members:
            - email: p1@jetbrains.com
        - name: IDE
          orgUnit: "bob#'JetBrains \\ Platform \ IDE'"
          parent: Platform
          members:
            - email: i1@jetbrains.com
      """.trimIndent()
    )

    val groups = GroupsParser.loadGroups(tempDir).nameToGroup

    val platform = groups[GroupName.build(null, "Platform")]!!
    val ide = groups[GroupName.build(null, "IDE")]!!

    Assertions.assertNull(platform.parent)
    Assertions.assertEquals(platform, ide.parent)
    // Child without orgUnit is allowed if it has a parent
    Assertions.assertNotNull(platform.orgUnit)
  }

  @Test
  fun `returns empty map for empty groups file`() {
    writeGroups(
      """
      groups: []
      """.trimIndent()
    )

    val groups = GroupsParser.loadGroups(tempDir)
    Assertions.assertTrue(groups.nameToGroup.isEmpty())
  }

  @Test
  fun `throws when groups file missing`() {
    // Ensure file not created at all
    Assertions.assertFalse(tempDir.resolve(".ownership/groups.yaml").exists())
    val ex = assertThrows<IllegalArgumentException> {
      GroupsParser.loadGroups(tempDir)
    }
    Assertions.assertTrue(ex.message!!.contains("Ownership groups file not found"))
  }

  @Test
  fun `throws on duplicate group names`() {
    writeGroups(
      """
      groups:
        - name: Duplicated
          orgUnit: "bob#'Org'"
          members: [{ email: a@jetbrains.com }]
        - name: Duplicated
          orgUnit: "bob#'Org'"
          members: [{ email: b@jetbrains.com }]
      """.trimIndent()
    )

    val ex = assertThrows<IllegalArgumentException> {
      GroupsParser.loadGroups(tempDir)
    }
    Assertions.assertTrue(ex.message!!.contains("Duplicate group names"))
  }

  @Test
  fun `throws when referencing missing parent`() {
    writeGroups(
      """
      groups:
        - name: Child
          orgUnit: "bob#'JetBrains'"
          parent: NoSuchParent
          members: [{ email: child@jetbrains.com }]
      """.trimIndent()
    )

    val ex = assertThrows<IllegalArgumentException> {
      GroupsParser.loadGroups(tempDir)
    }
    Assertions.assertTrue(ex.message!!.contains("references missing parent"), ex.message)
  }

  @Test
  fun `detects cycles in parent chain`() {
    writeGroups(
      """
      groups:
        - name: A
          orgUnit: "bob#'A'"
          members: [{ email: a@jetbrains.com }]
          parent: B
        - name: B
          orgUnit: "bob#'B'"
          members: [{ email: b@jetbrains.com }]
          parent: A
      """.trimIndent()
    )

    val ex = assertThrows<IllegalStateException> {
      GroupsParser.loadGroups(tempDir)
    }
    Assertions.assertTrue(ex.message!!.contains("Cycle detected"))
  }

  @Test
  fun `throws when orgUnit has unexpected prefix`() {
    writeGroups(
      """
      groups:
        - name: Strange
          orgUnit: "team#'Some Org'"
          members:
            - email: strange@jetbrains.com
      """.trimIndent()
    )

    val ex = assertThrows<IllegalStateException> {
      GroupsParser.loadGroups(tempDir)
    }
    Assertions.assertTrue(ex.message!!.contains("unexpected orgUnit"), ex.message)
  }

  @Test
  fun `throws when orgUnit with bob# has missing team path`() {
    writeGroups(
      """
      groups:
        - name: EmptyOrg
          orgUnit: "bob#   "
          members:
            - email: e1@jetbrains.com
      """.trimIndent()
    )

    val ex = assertThrows<IllegalArgumentException> {
      GroupsParser.loadGroups(tempDir)
    }
    Assertions.assertTrue(ex.message!!.contains("missing team path"), ex.message)
  }

  @Test
  fun `throws on duplicated member emails after normalization`() {
    writeGroups(
      """
      groups:
        - name: Duo
          orgUnit: "bob#'Org'"
          members:
            - email: Dev@jetbrains.com
            - email: " dev@jetbrains.com "
      """.trimIndent()
    )

    val ex = assertThrows<IllegalArgumentException> {
      GroupsParser.loadGroups(tempDir)
    }
    Assertions.assertTrue(ex.message!!.contains("Duplicated member emails"), ex.message)
  }

  @Test
  fun `throws on empty member emails`() {
    writeGroups(
      """
      groups:
        - name: EmptyEmail
          orgUnit: "bob#'Org'"
          members:
            - email: "   "
            - email: valid@jetbrains.com
      """.trimIndent()
    )

    val ex = assertThrows<IllegalArgumentException> {
      GroupsParser.loadGroups(tempDir)
    }
    Assertions.assertTrue(ex.message!!.contains("Members have empty emails"), ex.message)
  }

  @Test
  fun `throws on non-jetbrains member email domain`() {
    writeGroups(
      """
      groups:
        - name: Foreign
          orgUnit: "bob#'Org'"
          members:
            - email: user@gmail.com
      """.trimIndent()
    )

    val ex = assertThrows<IllegalArgumentException> {
      GroupsParser.loadGroups(tempDir)
    }
    Assertions.assertTrue(ex.message!!.contains("only @jetbrains.com allowed"), ex.message)
  }

  @Test
  fun `reuses visited parent for multiple children`() {
    writeGroups(
      """
      groups:
        - name: Parent
          orgUnit: "bob#'Org'"
          members:
            - email: p@jetbrains.com
        - name: Child1
          parent: Parent
          orgUnit: "bob#'Org \\ Child1'"
          members:
            - email: c1@jetbrains.com
        - name: Child2
          parent: Parent
          orgUnit: "bob#'Org \\ Child2'"
          members:
            - email: c2@jetbrains.com
      """.trimIndent()
    )

    val groups = GroupsParser.loadGroups(tempDir).nameToGroup
    val parent = groups[GroupName.build(null, "Parent")]!!
    val c1 = groups[GroupName.build(null, "Child1")]!!
    val c2 = groups[GroupName.build(null, "Child2")]!!

    Assertions.assertEquals(3, groups.size)
    Assertions.assertNull(parent.parent)
    Assertions.assertEquals(parent, c1.parent)
    Assertions.assertEquals(parent, c2.parent)
  }

  @Test
  fun `normalizes member emails to lowercase and trimmed`() {
    writeGroups(
      """
      groups:
        - name: Norm
          orgUnit: "bob#'Org'"
          members:
            - email: " USER@JetBrains.com "
      """.trimIndent()
    )

    val groups = GroupsParser.loadGroups(tempDir).nameToGroup
    val norm = groups[GroupName.build(null, "Norm")]!!
    Assertions.assertEquals("user@jetbrains.com", norm.members.first().email)
  }

  @Test
  fun `maps member external flag and default`() {
    writeGroups(
      """
      groups:
        - name: Exts
          orgUnit: "bob#'Org'"
          members:
            - email: internal@jetbrains.com
            - email: ext@jetbrains.com
              external: true
      """.trimIndent()
    )

    val groups = GroupsParser.loadGroups(tempDir).nameToGroup
    val g = groups[GroupName.build(null, "Exts")]!!
    Assertions.assertEquals(2, g.members.size)
    Assertions.assertFalse(g.members[0].external)
    Assertions.assertTrue(g.members[1].external)
  }

  @Test
  fun `computes fullName and allParentsNames for deep hierarchy`() {
    writeGroups(
      """
      groups:
        - name: Root
          orgUnit: "bob#'Company'"
          members: [{ email: r@jetbrains.com }]
        - name: Mid
          parent: Root
          orgUnit: "bob#'Company \\ Dept'"
          members: [{ email: m@jetbrains.com }]
        - name: Leaf
          parent: Mid
          orgUnit: "bob#'Company \\ Dept \\ Team'"
          members: [{ email: l@jetbrains.com }]
      """.trimIndent()
    )

    val groups = GroupsParser.loadGroups(tempDir).nameToGroup
    val root = groups[GroupName.build(null, "Root")]!!
    val mid = groups[GroupName.build(null, "Mid")]!!
    val leaf = groups[GroupName.build(null, "Leaf")]!!

    Assertions.assertEquals("Root", root.fullName)
    Assertions.assertEquals("Root / Mid", mid.fullName)
    Assertions.assertEquals("Root / Mid / Leaf", leaf.fullName)

    Assertions.assertTrue(leaf.allParentsNames.containsAll(setOf(root.name, mid.name, leaf.name)))
  }

  @Test
  fun `child with no members but with parent is not allowed`() {
    writeGroups(
      """
      groups:
        - name: ParentOnly
          orgUnit: "bob#'Org'"
          members: [{ email: p@jetbrains.com }]
        - name: ChildEmpty
          parent: ParentOnly
          orgUnit: "bob#'Org \\ Child'"
      """.trimIndent()
    )

    val ex = assertThrows<IllegalStateException> {
      GroupsParser.loadGroups(tempDir)
    }
    Assertions.assertTrue(ex.message!!.contains("must have at least one member"))
  }

  @Test
  fun `nameToGroup is keyed by GroupName uniqueId (case-insensitive lookup)`() {
    writeGroups(
      """
      groups:
        - name: Platform
          orgUnit: "bob#'Org'"
          members: [{ email: p@jetbrains.com }]
      """.trimIndent()
    )

    val groups = GroupsParser.loadGroups(tempDir).nameToGroup
    val byLower = groups[GroupName.build(null, "platform")]
    val byOriginal = groups[GroupName.build(null, "Platform")]
    Assertions.assertNotNull(byLower)
    Assertions.assertEquals(byOriginal, byLower)
    Assertions.assertEquals("Platform", byLower!!.name.stringName)
  }

  @Test
  fun `parses group with slackChannel`() {
    writeGroups(
      """
      groups:
        - name: Platform
          orgUnit: "bob#'Org'"
          slackChannel: '#ij-platform'
          members: [{ email: p@jetbrains.com }]
      """.trimIndent()
    )

    val groups = GroupsParser.loadGroups(tempDir).nameToGroup
    val platform = groups[GroupName.build(null, "Platform")]!!
    Assertions.assertEquals("#ij-platform", platform.slackChannel)
  }

  @Test
  fun `slackChannel is null when not specified`() {
    writeGroups(
      """
      groups:
        - name: Platform
          orgUnit: "bob#'Org'"
          members: [{ email: p@jetbrains.com }]
      """.trimIndent()
    )

    val groups = GroupsParser.loadGroups(tempDir).nameToGroup
    val platform = groups[GroupName.build(null, "Platform")]!!
    Assertions.assertNull(platform.slackChannel)
  }

  @Test
  fun `throws when slackChannel does not start with hash`() {
    writeGroups(
      """
      groups:
        - name: Platform
          orgUnit: "bob#'Org'"
          slackChannel: 'ij-platform'
          members: [{ email: p@jetbrains.com }]
      """.trimIndent()
    )

    val ex = assertThrows<IllegalArgumentException> {
      GroupsParser.loadGroups(tempDir)
    }
    Assertions.assertTrue(ex.message!!.contains("slackChannel must start with '#'"), ex.message)
  }

  @Test
  fun `throws when slackChannel contains spaces`() {
    writeGroups(
      """
      groups:
        - name: Platform
          orgUnit: "bob#'Org'"
          slackChannel: '#ij platform'
          members: [{ email: p@jetbrains.com }]
      """.trimIndent()
    )

    val ex = assertThrows<IllegalArgumentException> {
      GroupsParser.loadGroups(tempDir)
    }
    Assertions.assertTrue(ex.message!!.contains("slackChannel must not contain spaces"), ex.message)
  }

  private fun writeGroups(content: String) {
    val dir = tempDir.resolve(".ownership")
    dir.createDirectories()
    dir.resolve("groups.yaml").writeText(content)
  }
}

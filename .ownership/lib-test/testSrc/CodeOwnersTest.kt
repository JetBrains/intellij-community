package com.intellij.codeowners

import com.intellij.codeowners.model.Group
import com.intellij.codeowners.model.GroupMember
import com.intellij.codeowners.model.GroupName
import com.intellij.codeowners.model.Groups
import com.intellij.codeowners.model.OrgUnit
import com.intellij.codeowners.model.OwnerReviewRule
import com.intellij.codeowners.model.OwnershipMapping
import com.intellij.codeowners.model.OwnershipMappingEntry
import com.intellij.codeowners.model.ReviewRuleOverride
import com.intellij.codeowners.model.ReviewRules
import com.intellij.codeowners.model.SlackNotificationsSpec
import com.intellij.codeowners.serialization.OwnershipParser
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class CodeOwnersTest {

  @TempDir
  lateinit var tempDir: Path

  // --- Helpers ---
  private fun group(name: String, parent: Group? = null): Pair<GroupName, Group> {
    val gName = GroupName.build(null, name)
    val grp = Group(
      name = gName,
      parent = parent,
      members = listOf(GroupMember(email = "a@jetbrains.com", external = false)),
      orgUnit = OrgUnit.HiBobTeam("Org \\ Team"),
      slackChannel = null,
    )
    return gName to grp
  }

  private fun groupsMap(vararg names: String): Groups {
    val map = mutableMapOf<GroupName, Group>()
    names.forEach { n ->
      val (name, grp) = group(n)
      map[name] = grp
    }
    return Groups(map)
  }

  private fun ownership(vararg entries: OwnershipMappingEntry): OwnershipMapping =
    OwnershipMapping(entries.toList())

  private fun entry(sourceFile: String, rule: String, owner: String): OwnershipMappingEntry =
    OwnershipMappingEntry(
      sourceFile = sourceFile,
      rule = rule,
      owner = GroupName.build(sourceFile, owner),
    )

  private fun reviewRules(vararg rules: OwnerReviewRule): ReviewRules = ReviewRules(rules.toList())

  private fun rule(
    branches: List<String>,
    ownerReview: Boolean,
    overrides: Map<String, ReviewRuleOverride> = emptyMap(),
  ): OwnerReviewRule = OwnerReviewRule(
    branches = branches.map { it.toRegex() },
    ownerReview = ownerReview,
    overrides = overrides.mapKeys { (k, _) -> GroupName.build(null, k) },
  )

  private fun override(ownerReview: Boolean, extra: List<String> = emptyList(), slack: List<SlackNotificationsSpec> = emptyList()): ReviewRuleOverride =
    ReviewRuleOverride(
      ownerReview = ownerReview,
      extraReview = extra.map { GroupName.build(null, it) },
      slackNotifications = slack,
    )

  // --- Tests ---

  @Test
  fun `constructor with project root loads files and initializes successfully`() {
    // groups.yaml
    val ownershipDir = tempDir.resolve(".ownership").also { it.createDirectories() }
    ownershipDir.resolve("groups.yaml").writeText(
      """
      groups:
        - name: Alpha
          orgUnit: "bob#'Org'"
          members:
            - email: a@jetbrains.com
      """.trimIndent()
    )

    // review-rules.yaml (no overrides to keep it simple)
    ownershipDir.resolve("review-rules.yaml").writeText(
      """
      reviewRules:
        - branches: [ 'master' ]
      """.trimIndent()
    )

    // ownership mapping file via API to ensure correct format
    val mapping = ownership(
      // Ensure entries are sorted by depth and sourceFile as required
      entry(".patronus/OWNERSHIP", "/**", "Alpha"),
    )
    OwnershipParser.saveMapping(tempDir, mapping)

    // should not throw
    val codeOwners = CodeOwners(tempDir)
    Assertions.assertEquals(1, codeOwners.groups.nameToGroup.size)
    Assertions.assertEquals(1, codeOwners.reviewRules.entries.size)
    Assertions.assertEquals(1, codeOwners.ownershipMapping.entries.size)
  }

  @Test
  fun `init throws when groups referenced in mapping or overrides are not defined`() {
    val groups = groupsMap("Defined")
    val mapping = ownership(
      // owner 'Missing' is not in groups
      entry("a/OWNERSHIP", "/**/*.kt", "Missing"),
    )
    val rules = reviewRules(
      rule(
        branches = listOf("master"),
        ownerReview = true,
        overrides = mapOf(
          // override references another missing group
          "Also Missing" to override(ownerReview = true, extra = listOf("Defined"))
        )
      )
    )

    val ex = assertThrows<IllegalArgumentException> {
      CodeOwners(groups, rules, mapping)
    }
    Assertions.assertTrue(ex.message!!.contains("Groups used but not defined"), ex.message)
    // The message should list at least one missing group name
    Assertions.assertTrue(ex.message!!.contains("Missing") || ex.message!!.contains("Also Missing"))
  }

  @Test
  fun `getOwners returns unique owners with matched file and ignores non-matching files`() {
    val groups = groupsMap("A", "B")
    val mapping = ownership(
      entry("a/OWNERSHIP", "/**/*.kt", "A"),
      entry("b/OWNERSHIP", "/**/*.java", "B"),
      // Another rule for A should not produce duplicate match
      entry("c/OWNERSHIP", "/x/**", "A"),
    )
    val rules = reviewRules(rule(listOf("master"), ownerReview = true))

    val codeOwners = CodeOwners(groups, rules, mapping)
    val matches = codeOwners.getOwners(listOf("editor/Editor.kt", "editor/Other.kt", "build/Build.java", "README.md"))

    // Two unique owners A and B
    Assertions.assertEquals(2, matches.size)
    val byGroup = matches.associateBy { it.group.name.stringName }
    Assertions.assertEquals("editor/Editor.kt", byGroup.getValue("A").matchedFilePath)
    Assertions.assertEquals("build/Build.java", byGroup.getValue("B").matchedFilePath)
  }

  @Test
  fun `getOwner test`() {
    val groups = groupsMap("A", "B")
    val mapping = ownership(
      entry(sourceFile = "a/OWNERSHIP", rule = "/a/**/*.kt", owner = "A"),
      entry(sourceFile = "a/OWNERSHIP", rule = "/a/**/Editor.kt", owner = "B"),
    )
    val rules = reviewRules(rule(listOf("master"), ownerReview = true))

    val codeOwners = CodeOwners(groups, rules, mapping)
    Assertions.assertEquals(
      groups.nameToGroup.getValue(GroupName.build(null, "B")),
      codeOwners.getOwner("a/editor/Editor.kt")?.group,
    )
    Assertions.assertEquals(
      groups.nameToGroup.getValue(GroupName.build(null, "A")),
      codeOwners.getOwner("a/editor/NotEditor.kt")?.group,
    )
    Assertions.assertNull(codeOwners.getOwner("a/editor/Editor.md")?.group)
  }

  @Test
  fun `getReviewRules returns empty review when no rule matches branch`() {
    val groups = groupsMap("A", "B")
    val mapping = ownership(
      entry("a/OWNERSHIP", "/**/*.kt", "A"),
      entry("b/OWNERSHIP", "/**/*.java", "B"),
    )
    val rules = reviewRules(
      rule(listOf("master"), ownerReview = true)
    )

    val codeOwners = CodeOwners(groups, rules, mapping)
    val rr = codeOwners.getReviewRules(listOf("f.kt", "g.java"), targetBranch = "feature/xyz")

    Assertions.assertEquals(2, rr.entries.size)
    rr.entries.forEach { e ->
      Assertions.assertTrue(e.reviewRequired.isEmpty())
      Assertions.assertTrue(e.slackNotifications.isEmpty())
    }
  }

  @Test
  fun `getReviewRules applies ownerReview and overrides with extra reviewers and slack notifications`() {
    val groups = run {
      val base = groupsMap("A", "B", "QA")
      base
    }
    val mapping = ownership(
      entry("a/OWNERSHIP", "/**/*.kt", "A"),
      entry("b/OWNERSHIP", "/**/*.java", "B"),
    )
    val overrideForA = override(
      ownerReview = false, // disable owner review for A
      extra = listOf("QA"),
      slack = listOf(SlackNotificationsSpec(channel = "#channel", onStart = false))
    )
    val rules = reviewRules(
      rule(
        branches = listOf("release/.+"),
        ownerReview = true, // by default owners must review
        overrides = mapOf(
          "A" to overrideForA
        )
      )
    )

    val codeOwners = CodeOwners(groups, rules, mapping)
    val rr = codeOwners.getReviewRules(listOf("src/Main.kt", "src/Main.java"), targetBranch = "release/243")

    // Expect entries for both A and B
    Assertions.assertEquals(2, rr.entries.size)
    val entriesByOwner = rr.entries.associateBy { it.match.group.name.stringName }

    // For A: owner review disabled by override, only extra QA is required, and slack notifications present
    val a = entriesByOwner.getValue("A")
    Assertions.assertEquals(setOf(groups.nameToGroup.getValue(GroupName.build(null, "QA"))), a.reviewRequired)
    Assertions.assertEquals(1, a.slackNotifications.size)
    Assertions.assertEquals("#channel", a.slackNotifications[0].channel)
    Assertions.assertFalse(a.slackNotifications[0].onStart)

    // For B: owner review required (no override), no extra reviewers, no slack notifications
    val b = entriesByOwner.getValue("B")
    Assertions.assertEquals(setOf(b.match.group), b.reviewRequired)
    Assertions.assertTrue(b.slackNotifications.isEmpty())
  }
}

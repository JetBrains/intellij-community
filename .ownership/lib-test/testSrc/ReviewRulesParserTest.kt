package com.intellij.codeowners

import com.intellij.codeowners.model.GroupName
import com.intellij.codeowners.serialization.ReviewRulesParser
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

class ReviewRulesParserTest {

  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `throws when review-rules file missing`() {
    val path = tempDir.resolve(".ownership/review-rules.yaml")
    Assertions.assertFalse(path.exists())
    val ex = assertThrows<IllegalArgumentException> {
      ReviewRulesParser.loadReviewRules(tempDir)
    }
    Assertions.assertTrue(ex.message!!.contains("Review rules file not found"))
  }

  @Test
  fun `parses minimal single rule and applies default ownerReview true`() {
    writeReviewRules(
      """
      reviewRules:
        - branches: [ 'master' ]
      """.trimIndent()
    )

    val rules = ReviewRulesParser.loadReviewRules(tempDir)
    Assertions.assertEquals(1, rules.entries.size)
    val e = rules.entries.first()
    // default ownerReview should be true when not specified
    Assertions.assertTrue(e.ownerReview)
    // branches converted to regex and should match exactly 'master'
    Assertions.assertTrue(e.branches.any { it.matches("master") })
    Assertions.assertFalse(e.branches.any { it.matches("release/123") })
    // no overrides
    Assertions.assertTrue(e.overrides.isEmpty())
  }

  @Test
  fun `parses rule with explicit ownerReview false and override ownerReview true`() {
    writeReviewRules(
      """
      reviewRules:
        - branches: [ '\d+' ]
          ownerReview: false
          overrides:
            - owner: 'IntelliJ Platform'
              ownerReview: true
      """.trimIndent()
    )

    val rules = ReviewRulesParser.loadReviewRules(tempDir)
    Assertions.assertEquals(1, rules.entries.size)
    val e = rules.entries.first()
    // rule ownerReview explicitly false
    Assertions.assertFalse(e.ownerReview)
    // branch regex should match numeric branches like "123"
    Assertions.assertTrue(e.branches.any { it.matches("123") })
    Assertions.assertFalse(e.branches.any { it.matches("master") })

    val key = GroupName.build(".ownership/review-rules.yaml", "IntelliJ Platform")
    val ov = e.overrides[key]
    Assertions.assertNotNull(ov)
    // override ownerReview should be true (overrides rule value)
    Assertions.assertTrue(ov!!.ownerReview)
    // defaults for lists
    Assertions.assertTrue(ov.extraReview.isEmpty())
    Assertions.assertTrue(ov.slackNotifications.isEmpty())
  }

  @Test
  fun `parses extraReview and slackNotifications lists`() {
    writeReviewRules(
      """
      reviewRules:
        - branches: [ 'release/.+' ]
          ownerReview: false
          overrides:
            - owner: 'IntelliJ Platform'
              extraReview:
                - group: 'IntelliJ Platform QA'
                - group: 'Build Scripts Maintainers'
              slackNotifications:
                - channel: '#ij-eap-release-cherrypicks'
                  onStart: false
                - channel: '#another-channel'
                  onStart: true
      """.trimIndent()
    )

    val rules = ReviewRulesParser.loadReviewRules(tempDir)
    val e = rules.entries.single()
    Assertions.assertFalse(e.ownerReview)
    Assertions.assertTrue(e.branches.any { it.matches("release/243") })

    val key = GroupName.build(".ownership/review-rules.yaml", "IntelliJ Platform")
    val ov = e.overrides[key]!!
    // override ownerReview should fall back to rule value when missing (false here)
    Assertions.assertFalse(ov.ownerReview)

    // extraReview groups are converted to GroupName with review-rules.yaml as source
    val qa = GroupName.build(".ownership/review-rules.yaml", "IntelliJ Platform QA")
    val buildScripts = GroupName.build(".ownership/review-rules.yaml", "Build Scripts Maintainers")
    Assertions.assertEquals(listOf(qa, buildScripts), ov.extraReview)

    // slack notifications preserved
    Assertions.assertEquals(2, ov.slackNotifications.size)
    Assertions.assertEquals("#ij-eap-release-cherrypicks", ov.slackNotifications[0].channel)
    Assertions.assertFalse(ov.slackNotifications[0].onStart)
    Assertions.assertEquals("#another-channel", ov.slackNotifications[1].channel)
    Assertions.assertTrue(ov.slackNotifications[1].onStart)
  }

  @Test
  fun `parses multiple rules entries`() {
    writeReviewRules(
      """
      reviewRules:
        - branches: [ 'master' ]
          ownerReview: true
        - branches: [ 'release/.+' ]
          ownerReview: false
      """.trimIndent()
    )

    val rules = ReviewRulesParser.loadReviewRules(tempDir)
    Assertions.assertEquals(2, rules.entries.size)
    val r1 = rules.entries[0]
    val r2 = rules.entries[1]
    Assertions.assertTrue(r1.ownerReview)
    Assertions.assertTrue(r1.branches.any { it.matches("master") })
    Assertions.assertFalse(r1.branches.any { it.matches("release/1") })

    Assertions.assertFalse(r2.ownerReview)
    Assertions.assertTrue(r2.branches.any { it.matches("release/1") })
  }

  private fun writeReviewRules(content: String) {
    val dir = tempDir.resolve(".ownership")
    dir.createDirectories()
    dir.resolve("review-rules.yaml").writeText(content)
  }
}

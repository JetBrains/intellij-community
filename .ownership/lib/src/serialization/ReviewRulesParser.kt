
package com.intellij.codeowners.serialization

import com.charleskorn.kaml.Yaml
import com.intellij.codeowners.Constants
import com.intellij.codeowners.model.GroupName
import com.intellij.codeowners.model.OwnerReviewRule
import com.intellij.codeowners.model.ReviewRuleOverride
import com.intellij.codeowners.model.ReviewRules
import com.intellij.codeowners.model.SlackNotificationsSpec
import com.intellij.codeowners.serialization.schema.ReviewRulesConfig
import kotlinx.serialization.decodeFromString
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText

object ReviewRulesParser {
  private val logger: Logger = Logger.getLogger(ReviewRulesParser::class.java.name)
  fun loadReviewRules(projectRoot: Path): ReviewRules {
    val reviewRulesFile = projectRoot / Constants.REVIEW_RULES_FILE_RELATIVE_PATH
    require(reviewRulesFile.exists()) { "Review rules file not found: $reviewRulesFile" }
    logger.info("Loading review rules from $reviewRulesFile")
    val content = reviewRulesFile.readText(Charsets.UTF_8)
    val raw = Yaml.default.decodeFromString<ReviewRulesConfig>(content)
    val built = buildReviewRules(raw)
    logger.info("Parsed ${built.entries.size} review rule entries")
    return built
  }

  private fun buildReviewRules(definition: ReviewRulesConfig): ReviewRules {
    val entries = mutableListOf<OwnerReviewRule>()
    definition.reviewRules.forEach { rawOwnerReviewRule ->
      val branches = rawOwnerReviewRule.branches.map { it.toRegex() }
      val ruleRequireOwnerReview = rawOwnerReviewRule.ownerReview ?: Constants.REVIEW_RULES_REQUIRE_OWNER_REVIEW_DEFAULT

      val rule = OwnerReviewRule(
        branches = branches,
        ownerReview = ruleRequireOwnerReview,
        overrides = rawOwnerReviewRule.overrides.associate { overrideConfig ->
          val groupName = GroupName.build(Constants.REVIEW_RULES_FILE_RELATIVE_PATH, overrideConfig.owner)
          val override = ReviewRuleOverride(
            ownerReview = overrideConfig.ownerReview ?: ruleRequireOwnerReview,
            extraReview = overrideConfig.extraReview.map { GroupName.build(Constants.REVIEW_RULES_FILE_RELATIVE_PATH, it.group) },
            slackNotifications = overrideConfig.slackNotifications.map { SlackNotificationsSpec(channel = it.channel, onStart = it.onStart) }
          )
          groupName to override
        },
      )
      entries.add(rule)
    }
    return ReviewRules(entries)
  }
}
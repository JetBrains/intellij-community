package com.intellij.codeowners.serialization.schema

import kotlinx.serialization.Serializable

/**
 * Describes `.ownership/review-rules.yaml` file schema.
 */
@Serializable
internal data class ReviewRulesConfig(
  /**
   * Review rules
   */
  val reviewRules: List<ReviewRuleConfig> = emptyList(),

  )

/**
 * Code owners review rules configuration.
 */
@Serializable
internal data class ReviewRuleConfig(
  /**
   * Apply the current rule for [branches]. Each [branches] entry is a regular expression.
   */
  val branches: List<String> = emptyList(),

  /**
   * Whether to request review (e.g., some codebase part can allow merges without a review)
   */
  val ownerReview: Boolean? = null,

  /**
   * Specific configuration which overrides [ownerReview] and other settings scoped to a specific code owner
   */
  val overrides: List<ReviewRuleOverrideConfig> = emptyList(),
)

/**
 * Describes review rules configuration for a specific owner by its [GroupConfig.name].
 */
@Serializable
internal data class ReviewRuleOverrideConfig(
  /**
   * [GroupConfig.name] of the group to apply the current rule for.
   */
  val owner: String,

  /**
   * Whether to request owner's review (e.g., some codebase part can allow merges without a review)
   */
  val ownerReview: Boolean? = null,

  /**
   * Require an ext
   */
  val extraReview: List<ExtraReviewConfig> = emptyList(),

  val slackNotifications: List<SlackNotificationsConfig> = emptyList(),
)


@Serializable
internal data class ExtraReviewConfig(
  val group: String,
)


@Serializable
internal data class SlackNotificationsConfig(
  val channel: String,
  val onStart: Boolean = false,
) {
  init {
      require(channel.startsWith("#")) {
        "Error while parsing slack notifications config: channel '$channel' should start with #"
      }
      require(channel.substringAfter("#").isNotBlank()) {
        "Error while parsing slack notifications config: channel '$channel' should not be empty"
      }
  }
}

package com.intellij.codeowners.model

data class ReviewRules(
  val entries: List<OwnerReviewRule>,
)

data class OwnerReviewRule(
  val branches: List<Regex>,
  val ownerReview: Boolean,
  val overrides: Map<GroupName, ReviewRuleOverride>,
)

data class ReviewRuleOverride(
  val ownerReview: Boolean,
  val extraReview: List<GroupName>,
  val slackNotifications: List<SlackNotificationsSpec>,
)

data class SlackNotificationsSpec(
  val channel: String,
  val onStart: Boolean,
)
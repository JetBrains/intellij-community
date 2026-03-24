package com.intellij.codeowners.model

data class OwnershipMatch(
  val group: Group,
  val matchedFilePath: String,
  val ownersMeta: OwnershipMappingEntry,
) {
  init {
      require(group.name == ownersMeta.owner)
  }
}

data class ReviewRulesMatch(
  val entries: List<ReviewRulesMatchEntry>,
)

data class ReviewRulesMatchEntry(
  val match: OwnershipMatch,
  val reviewRequired: Set<Group>,
  val slackNotifications: List<SlackNotificationsSpec>,
)
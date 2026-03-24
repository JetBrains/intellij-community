package com.intellij.codeowners.serialization.schema

import kotlinx.serialization.Serializable


/**
 * Describes `.ownership/groups.yaml` file schema.
 *
 * Defines people groups used when defining code ownership.
 */
@Serializable
internal data class GroupsConfig(
  val groupSettings: GroupsSettingsConfig = GroupsSettingsConfig(),
  val groups: List<GroupConfig>,
)

/**
 * Defines groups settings
 */
@Serializable
data class GroupsSettingsConfig(
  val minMembers: Int = 0,
)

/**
 * Describes a people group used when defining code ownership.
 */
@Serializable
internal data class GroupConfig(
  /**
   * Group name to reference in `.ownership/review_rules.yaml` and `OWNERSHIP` files
   */
  val name: String,

  /**
   * An identifier of the organization unit which the current group belongs to,
   * for example, a department or a team.
   *
   * The unit lead (Team Lead, Department Lead) is accountable for keeping
   * the current group and subgroups (referenced with [parent]) up to date.
   *
   * All groups must be associated with organization units, so either [orgUnit] or [parent] must be specified.
   *
   * Examples:
   *   * bob@'JetBrains \ IntelliJ' - IntelliJ department
   *   * bob@'JetBrains \ Head Office \ Developer Productivity \ Monorepo Infrastructure' - Monorepo Infrastructure team
   */
  val orgUnit: String,

  /**
   * Optional parent group [name].
   * Can be used to define nested groups to represent hierarchy.
   */
  val parent: String? = null,

  /**
   * Group members.
   */
  val members: List<GroupMemberConfig> = emptyList(),

  /**
   * Optional Slack channel for the group.
   * Used for notifications about code review requests and other group-related communications.
   *
   * Example: #idea-java-alerts
   */
  val slackChannel: String? = null,
)

/**
 * Describes [GroupConfig] member.
 */
@Serializable
internal data class GroupMemberConfig(
  /**
   * Group member's email.
   * Only `@jetbrains.com` addresses are supported.
   */
  val email: String,

  /**
   * If true, the member is not required to belong to the organization unit the groups is assigned to.
   */
  val external: Boolean = false,
)
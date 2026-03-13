package com.intellij.codeowners.serialization

import com.charleskorn.kaml.Yaml
import com.intellij.codeowners.Constants
import com.intellij.codeowners.model.Group
import com.intellij.codeowners.model.GroupMember
import com.intellij.codeowners.model.GroupName
import com.intellij.codeowners.model.Groups
import com.intellij.codeowners.model.OrgUnit
import com.intellij.codeowners.serialization.schema.GroupConfig
import com.intellij.codeowners.serialization.schema.GroupsConfig
import kotlinx.serialization.decodeFromString
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText

object GroupsParser {
  private val logger: Logger = Logger.getLogger(GroupsParser::class.java.name)
  fun loadGroups(projectRoot: Path): Groups {
    val groupsFile = projectRoot / Constants.GROUPS_FILE_RELATIVE_PATH
    require(groupsFile.exists()) { "Ownership groups file not found: $groupsFile" }
    logger.info("Loading groups from $groupsFile")
    val content = groupsFile.readText(Charsets.UTF_8)
    val raw = Yaml.default.decodeFromString<GroupsConfig>(content)

    if (raw.groups.isEmpty()) {
      logger.info("No groups defined in $groupsFile")
      return Groups(emptyMap())
    }

    checkNoNamesDuplicated(raw)

    val rawByName: Map<GroupName, GroupNoParentResolved> = raw.groups.associate { g ->
      val name = GroupName.build(Constants.GROUPS_FILE_RELATIVE_PATH, g.name)

      name to GroupNoParentResolved(
        name = name,
        members = mapAndValidateGroupMembers(minMembers = raw.groupSettings.minMembers, g),
        orgUnit = mapAndValidateOrgUnit(g),
        parentName = g.parent?.let { GroupName.build(Constants.GROUPS_FILE_RELATIVE_PATH, it) },
        slackChannel = mapAndValidateSlackChannel(g),
      )
    }

    return Groups(buildGroupsWithParents(rawByName))
  }

  private fun mapAndValidateOrgUnit(group: GroupConfig): OrgUnit.HiBobTeam? = when {
    group.orgUnit.startsWith("bob#") -> {
      val teamPathString = group.orgUnit.removePrefix("bob#").trim(' ', '\'')
      require(!teamPathString.isBlank()) {
        "Group: ${group.name} org unit: missing team path after 'bob#' prefix"
      }
      OrgUnit.HiBobTeam(teamPathString)
    }

    else -> error("Group ${group.name}: unexpected orgUnit=${group.orgUnit}")
  }

  private fun mapAndValidateSlackChannel(group: GroupConfig): String? {
    val slackChannel = group.slackChannel?.trim()
    if (slackChannel.isNullOrEmpty()) return null
    require(slackChannel.startsWith("#")) {
      "Group '${group.name}': slackChannel must start with '#', got '$slackChannel'"
    }
    require(!slackChannel.contains(" ")) {
      "Group '${group.name}': slackChannel must not contain spaces, got '$slackChannel'"
    }
    return slackChannel
  }

  private fun mapAndValidateGroupMembers(minMembers: Int, group: GroupConfig): List<GroupMember> {
    val membersMapped = group.members.map {
      GroupMember(
        email = it.email.lowercase().trim(),
        external = it.external,
      )
    }

    val duplicatedEmailMembers = membersMapped.groupBy { it.email }.filter { it.value.size > 1 }.map { it.key }
    require(duplicatedEmailMembers.isEmpty()) {
      "Duplicated member emails in group '${group.name}': [${duplicatedEmailMembers.joinToString(", ")}]"
    }

    val emptyEmails = membersMapped.map { it.email }.filter { it.isEmpty() }
    require(emptyEmails.isEmpty()) {
      "Members have empty emails '${group.name}': [${emptyEmails.joinToString(", ")}]"
    }

    val nonJetBrainsCom = membersMapped.map { it.email }.filter { !it.endsWith("@jetbrains.com") }
    require(nonJetBrainsCom.isEmpty()) {
      "Members have emails with a wrong domain, only @jetbrains.com allowed '${group.name}': [${nonJetBrainsCom.joinToString(", ")}]"
    }
    return membersMapped
  }

  private fun checkNoNamesDuplicated(raw: GroupsConfig) {
    val duplicateNames = raw.groups
      .map { GroupName.build(Constants.GROUPS_FILE_RELATIVE_PATH, it.name) }
      .groupingBy { it }
      .eachCount()
      .filterValues { it > 1 }
      .keys

    require(duplicateNames.isEmpty()) {
      "Duplicate group names found: $duplicateNames"
    }
  }


  private enum class State { UNVISITED, VISITING, VISITED }

  private fun buildGroupsWithParents(rawByName: Map<GroupName, GroupNoParentResolved>): Map<GroupName, Group> {
    val stateByName = HashMap<GroupName, State>(rawByName.size)
    val resolvedByName = HashMap<GroupName, Group>(rawByName.size)

    fun resolve(name: GroupName): Group {
      when (stateByName[name] ?: State.UNVISITED) {
        State.VISITED -> return resolvedByName.getValue(name)
        State.VISITING -> {
          logger.severe("Cycle detected at '$name'")
          error("Cycle detected at '$name'")
        }
        State.UNVISITED -> Unit
      }

      val raw = rawByName[name] ?: error("Unknown group '$name'")
      stateByName[name] = State.VISITING

      val parent = raw.parentName?.let { parentName ->
        val parentRawExists = rawByName.containsKey(parentName)
        if (!parentRawExists) {
          logger.severe("Group '$name' references missing parent '$parentName'")
        }
        require(parentRawExists) { "Group '$name' references missing parent '$parentName'" }
        resolve(parentName)
      }

      val built = raw.toGroup(parent)
      resolvedByName[name] = built
      stateByName[name] = State.VISITED
      return built
    }

    for (name in rawByName.keys) {
      val state = stateByName.getOrDefault(name, State.UNVISITED)
      if (state == State.UNVISITED) {
        resolve(name)
      }
    }

    return resolvedByName
  }


  private data class GroupNoParentResolved(
    val name: GroupName,
    val members: List<GroupMember>,
    val orgUnit: OrgUnit?,
    val parentName: GroupName?,
    val slackChannel: String?,
  ) {
    fun toGroup(parent: Group?): Group =
      Group(
        name = name,
        members = members,
        parent = parent,
        orgUnit = orgUnit,
        slackChannel = slackChannel,
      )
  }
}
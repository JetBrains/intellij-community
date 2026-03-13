package com.intellij.codeowners.model

import com.intellij.codeowners.Constants

data class Groups(
  val nameToGroup: Map<GroupName, Group>,
) {
  init {
    require(nameToGroup.entries.none { (name, group) -> group.name != name})
    require(nameToGroup.values.mapNotNull { it.parent?.name }.distinct().all { parentName -> parentName in nameToGroup })
  }
}

@ConsistentCopyVisibility
data class GroupName private constructor(val stringName: String) {
  companion object {
    fun build(source: String?, stringName: String): GroupName {
      validateName(source, stringName)
      return GroupName(stringName)
    }

    fun validateName(source: String?, stringName: String) {
      require(stringName.matches(Constants.GROUP_NAME_VALIDATION_REGEX)) {
        val suffix = "Group name '$stringName' must match ${Constants.GROUP_NAME_VALIDATION_REGEX.pattern} pattern"
        if (source.isNullOrBlank()) suffix else "$source: $suffix"
      }
    }
  }
  /**
   * GitHub-compliant unique ID for the group.
   * To be used in `.github/CODEOWNERS` in the future.
   */
  val uniqueId: String = stringName.lowercase()
    .replace("\\.+".toRegex(), " dot ")
    .replace("&+".toRegex(), " and ")
    .replace("[^a-zA-Z0-9\\-]+".toRegex(), "-")

  init {
    validateName(source = null, stringName)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GroupName

    return uniqueId == other.uniqueId
  }

  override fun hashCode(): Int {
    return uniqueId.hashCode()
  }

  override fun toString(): String = stringName
}

data class Group(
  val name: GroupName,
  val parent: Group?,
  val members: List<GroupMember>,
  val orgUnit: OrgUnit?,
  val slackChannel: String?,
) {
  init {
    check(members.isNotEmpty()) { "Group '$name' must have at least one member or a parent" }
    check(orgUnit != null || parent != null) { "Group '$name' must have orgUnit or parent" }
    check(slackChannel == null || slackChannel.startsWith("#")) { "Group '$name' slackChannel must start with '#'" }
  }

  val fullName: String = if (parent == null) {
    name.stringName
  } else {
    "${parent.fullName} / ${name.stringName}"
  }

  val allParentsNames: Set<GroupName> = setOf(name) + (parent?.allParentsNames ?: emptySet())
}

data class GroupMember(
  val email: String,
  val external: Boolean,
)

sealed interface OrgUnit {
  data class HiBobTeam(val teamFullPath: String) : OrgUnit {
    init {
      require(teamFullPath.isNotBlank()) { "Team full path must not be blank" }
      require(teamFullPath.trim() == teamFullPath) { "Team full path trim result must be equal to original $teamFullPath" }
    }
  }
}
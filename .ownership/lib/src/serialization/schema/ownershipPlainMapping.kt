package com.intellij.codeowners.serialization.schema

import com.intellij.codeowners.model.GroupName
import com.intellij.codeowners.model.OwnershipMapping
import com.intellij.codeowners.model.OwnershipMappingEntry
import kotlinx.serialization.Serializable



@Serializable
internal data class OwnershipMappingEntryConfig(
  val sourceFile: String,
  val rule: String,
  val owner: String,
) {
  constructor(entry: OwnershipMappingEntry) : this(
    sourceFile = entry.sourceFile,
    rule = entry.rule,
    owner = entry.owner.stringName,
  )


  fun toModel(): OwnershipMappingEntry = OwnershipMappingEntry(
    sourceFile = sourceFile,
    rule = rule,
    owner = GroupName.build("Ownership mapping (based on $sourceFile)", owner),
  )
}

@Serializable
internal data class OwnershipMappingConfig(
  val entries: List<OwnershipMappingEntryConfig>,
) {
  constructor(mapping: OwnershipMapping) : this(mapping.entries.map { OwnershipMappingEntryConfig(it) })

  fun toModel(): OwnershipMapping = OwnershipMapping(entries.map { it.toModel() })

}
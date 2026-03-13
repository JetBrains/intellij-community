package com.intellij.codeowners

import com.intellij.codeowners.model.GroupName
import com.intellij.codeowners.model.Groups
import com.intellij.codeowners.model.OwnershipMapping
import com.intellij.codeowners.model.OwnershipMatch
import com.intellij.codeowners.model.ReviewRules
import com.intellij.codeowners.model.ReviewRulesMatch
import com.intellij.codeowners.model.ReviewRulesMatchEntry
import com.intellij.codeowners.serialization.GroupsParser
import com.intellij.codeowners.serialization.OwnershipParser
import com.intellij.codeowners.serialization.ReviewRulesParser
import java.nio.file.Path
import java.util.logging.Logger

class CodeOwners(
    val groups: Groups,
    val reviewRules: ReviewRules,
    val ownershipMapping: OwnershipMapping,
) {
  companion object {
    private val logger: Logger = Logger.getLogger(CodeOwners::class.java.name)
  }

  constructor(projectRoot: Path) : this(
    groups = GroupsParser.loadGroups(projectRoot),
    reviewRules = ReviewRulesParser.loadReviewRules(projectRoot),
    ownershipMapping = OwnershipParser.loadMapping(projectRoot) ?: error("Failed to load mapping"),
  )

  init {
    logger.info("Initializing groups=${groups.nameToGroup.size}, reviewRules=${reviewRules.entries.size}, mappingEntries=${ownershipMapping.entries.size}")

    val notDefinedGroups = mutableMapOf<GroupName, String>()
    ownershipMapping.entries
      .groupBy { it.owner }
      .forEach { (owner, entries) ->
        if (owner !in groups.nameToGroup) {
          notDefinedGroups[owner] = "OWNERSHIP files [${entries.joinToString { it.sourceFile }}]"
        }
      }

    reviewRules.entries.forEach { rule ->
      rule.overrides.forEach { (groupName, override) ->
        if (groupName !in groups.nameToGroup) {
          notDefinedGroups[groupName] = "Review rules"
        }

        override.extraReview.forEach { groupName ->
          if (groupName !in groups.nameToGroup) {
            notDefinedGroups[groupName] = "Review rules"
          }
        }
      }
    }

    require(notDefinedGroups.isEmpty()) {
      buildString {
        appendLine("Groups used but not defined: ")
        notDefinedGroups.forEach { (groupName, source) ->
          appendLine("  * '$groupName' used in $source")
        }
      }
    }
    logger.info("Initialization complete")
  }

  fun getOwner(file: String): OwnershipMatch? {
    logger.info("Resolving owner for ${file} files")
    val maybeMatch = ownershipMapping.getMatch(file)
    if (maybeMatch == null) {
      logger.info("No owner found for ${file}")
      return null
    }

    logger.info("Owner found for ${file}: ${maybeMatch.owner}")
    return OwnershipMatch(
        group = groups.nameToGroup.getValue(maybeMatch.owner),
        matchedFilePath = file,
        ownersMeta = maybeMatch,
    )
  }

  fun getOwners(changedFiles: List<String>): List<OwnershipMatch> {
    logger.info("Resolving owners for ${changedFiles.size} changed files")
    val matches = mutableMapOf<GroupName, OwnershipMatch>()
    changedFiles.forEach { path ->
      val maybeMatch = ownershipMapping.getMatch(path)

      if (maybeMatch != null && maybeMatch.owner !in matches) {
        matches[maybeMatch.owner] = OwnershipMatch(
            group = groups.nameToGroup.getValue(maybeMatch.owner),
            matchedFilePath = path,
            ownersMeta = maybeMatch,
        )
      }
    }

    val result = matches.values.toList()
    logger.info("Resolved ${result.size} owner matches")
    return result
  }

  fun getReviewRules(changedFiles: List<String>, targetBranch: String): ReviewRulesMatch {
    logger.info("Evaluating review rules for branch '$targetBranch' and ${changedFiles.size} files")
    val matchedOwners = getOwners(changedFiles)

    val reviewRule = reviewRules.entries.firstOrNull { rule ->
      rule.branches.any { branchRegexp -> branchRegexp.matches(targetBranch) }
    }

    if (reviewRule == null) {
      logger.info("No review rule matched for branch '$targetBranch'")
      return ReviewRulesMatch(
          entries = matchedOwners.map { owner ->
              ReviewRulesMatchEntry(
                  match = owner,
                  // no review rule matched - no review required
                  reviewRequired = emptySet(),
                  slackNotifications = emptyList(),
              )
          }
      )
    }

    logger.info("Matched review rule for branch '$targetBranch'")
    val result = matchedOwners.map { owner ->
      val matchedOverride = reviewRule.overrides[owner.group.name]

      val ownerReviewRequired: Boolean =
        // owner-specific override
        matchedOverride?.ownerReview
        // branch-specific value
        ?: reviewRule.ownerReview

      val extraReviewGroups = matchedOverride?.extraReview?.asSequence()
        ?.map { name -> groups.nameToGroup.getValue(name) }
        .orEmpty()

        ReviewRulesMatchEntry(
            match = owner,
            reviewRequired =
                // owner review
                setOfNotNull(owner.group.takeIf { ownerReviewRequired }) +
                        // extra review
                        extraReviewGroups,
            slackNotifications = matchedOverride?.slackNotifications.orEmpty(),
        )
    }
    logger.info("Built ${result.size} review rule entries")
    return ReviewRulesMatch(result)
  }
}
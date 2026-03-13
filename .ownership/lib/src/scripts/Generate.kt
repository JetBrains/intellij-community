package com.intellij.codeowners.scripts

import com.intellij.codeowners.CodeOwners
import com.intellij.codeowners.scripts.common.configureLogging
import com.intellij.codeowners.scripts.common.ultimateRoot
import com.intellij.codeowners.serialization.GitHubOwnersGenerator
import com.intellij.codeowners.serialization.GroupsParser
import com.intellij.codeowners.serialization.OwnershipParser
import com.intellij.codeowners.serialization.ReviewRulesParser
import java.nio.file.Path
import java.util.logging.Logger

internal object Generate {
  private val logger: Logger = Logger.getLogger(Generate::class.java.name)

  @JvmStatic
  fun main(args: Array<String>) {
    configureLogging()
    val projectRoot: Path = ultimateRoot

    logger.info("Generating OWNERSHIP at $projectRoot")
    val scanResult = OwnershipParser.scanProjectTree(projectRoot)
    OwnershipParser.saveMapping(projectRoot, scanResult)
    val owners = checkNotNull(OwnershipParser.loadMapping(projectRoot))

    val groups = GroupsParser.loadGroups(projectRoot)
    val reviewRules = ReviewRulesParser.loadReviewRules(projectRoot)
    val codeOwners = CodeOwners(groups, reviewRules, owners)

    val gitHubOwnersContent = GitHubOwnersGenerator.generateContent(codeOwners)
    GitHubOwnersGenerator.writeContentToFile(projectRoot, gitHubOwnersContent)
    logger.info("Generating OWNERSHIP at $projectRoot DONE")
  }
}

package com.intellij.codeowners.scripts

import com.intellij.codeowners.CodeOwners
import com.intellij.codeowners.scripts.common.UnifiedDiff
import com.intellij.codeowners.scripts.common.configureLogging
import com.intellij.codeowners.scripts.common.runReportingErrorsAsTests
import com.intellij.codeowners.scripts.common.ultimateRoot
import com.intellij.codeowners.serialization.GitHubOwnersGenerator
import com.intellij.codeowners.serialization.GroupsParser
import com.intellij.codeowners.serialization.OwnershipParser
import com.intellij.codeowners.serialization.ReviewRulesParser
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

internal object Validate {
  private val logger: Logger = Logger.getLogger(Validate::class.java.name)

  @JvmStatic
  fun main(args: Array<String>) {
    configureLogging()
    val projectRoot: Path = ultimateRoot

    logger.info("Validation OWNERSHIP at $projectRoot")
    val groups = runReportingErrorsAsTests(generateTestName("loadGroups")) { GroupsParser.loadGroups(projectRoot) }
    val reviewRules = runReportingErrorsAsTests(generateTestName("loadReviewRules")) { ReviewRulesParser.loadReviewRules(projectRoot) }
    val ownershipMapping = runReportingErrorsAsTests(generateTestName("scanProjectTree")) { OwnershipParser.scanProjectTree(projectRoot) }
    val codeOwners = runReportingErrorsAsTests(generateTestName("codeOwnersInit")) { CodeOwners(groups, reviewRules, ownershipMapping) }
    runReportingErrorsAsTests(generateTestName("checkGeneratedFilesContent")) { checkGeneratedFilesContent(projectRoot, codeOwners) }
    logger.info("Validation OWNERSHIP at $projectRoot DONE")
  }

  private fun checkGeneratedFilesContent(projectRoot: Path, codeOwners: CodeOwners) {
    val diffs = mutableListOf<UnifiedDiff>()

    listOf(
      OwnershipParser.getMappingFilePath(projectRoot) to OwnershipParser.generateMappingFile(codeOwners.ownershipMapping),
      GitHubOwnersGenerator.getOwnersFilePath(projectRoot) to GitHubOwnersGenerator.generateContent(codeOwners),
    ).forEach { (filePath, expectedContent) ->
      val fileName = filePath.relativeTo(projectRoot).invariantSeparatorsPathString
      val content =
        if (filePath.exists()) filePath.readText(Charsets.UTF_8)
        else ""

      if (content != expectedContent) {
        logger.warning("Content of $fileName differs from expected")
        diffs += UnifiedDiff.build(fileName, content, expectedContent)
      }
      else {
        logger.info("Content of $fileName matches expected")
      }
    }

    val nonEmptyDiffs = diffs.filterNot { it.isEmpty }
    if (nonEmptyDiffs.isEmpty()) {
      return
    }

    val errorMessage = buildString {
      appendLine("Please run './.ownership/generate.cmd' or apply the patch below to fix:")
      appendLine()
      nonEmptyDiffs.forEach {
        appendLine(it.diff)
        appendLine()
        appendLine()
      }
    }
    error(errorMessage)
  }

  private fun generateTestName(suffix: String) = "${this::class.qualifiedName!!}.$suffix"
}

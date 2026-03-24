package com.intellij.codeowners.serialization

import com.intellij.codeowners.Constants
import com.intellij.codeowners.CodeOwners
import java.util.logging.Logger
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object GitHubOwnersGenerator {
  private val logger: Logger = Logger.getLogger(GitHubOwnersGenerator::class.java.name)
  fun generateContent(codeOwners: CodeOwners): String = buildString {
    logger.info("Generating CODEOWNERS content from mapping with ${codeOwners.ownershipMapping.entries.size} entries")
    appendLine("# ${Constants.GENERATED_FILES_HEADER}")
    appendLine()
    codeOwners.ownershipMapping.entries.forEach { entry ->
      val groupName = entry.owner
      val group = codeOwners.groups.nameToGroup.getValue(groupName)
      val membersEmails = group.members.joinToString(" ") { it.email }
      appendLine("${entry.rule} $membersEmails # based on ${entry.sourceFile}")
    }
  }

  fun getOwnersFilePath(projectRoot: Path): Path = projectRoot / ".github" / "CODEOWNERS"

  fun writeContentToFile(projectRoot: Path, content: String) {
    val ownersFilePath = getOwnersFilePath(projectRoot)
    logger.info("Writing CODEOWNERS to $ownersFilePath")
    ownersFilePath.createParentDirectories()
    ownersFilePath.writeText(content, Charsets.UTF_8)
  }

  fun readContentFromFile(projectRoot: Path): String? {
    val ownersFilePath = getOwnersFilePath(projectRoot)
    if (!ownersFilePath.exists()) {
      logger.info("CODEOWNERS file does not exist at $ownersFilePath")
      return null
    }
    logger.info("Reading CODEOWNERS from $ownersFilePath")
    return ownersFilePath.readText(Charsets.UTF_8)
  }

}
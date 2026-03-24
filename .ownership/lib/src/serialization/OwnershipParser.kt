package com.intellij.codeowners.serialization

import com.charleskorn.kaml.Yaml
import com.intellij.codeowners.Constants
import com.intellij.codeowners.model.GroupName
import com.intellij.codeowners.model.OwnershipMapping
import com.intellij.codeowners.model.OwnershipMappingEntry
import com.intellij.codeowners.serialization.schema.OwnershipMappingConfig
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.io.path.createParentDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText


object OwnershipParser {
  private val OWNER_LINE_REGEX = "owner '(.+)'".toRegex()
  private val logger: Logger = Logger.getLogger(OwnershipParser::class.java.name)

  fun scanProjectTree(projectRoot: Path): OwnershipMapping {
    val ownershipDirectory = projectRoot / Constants.OWNERSHIP_DIRECTORY_NAME
    require(ownershipDirectory.isDirectory()) {  "Ownership directory not found: $ownershipDirectory" }

    logger.info("Scanning project tree for ownership files at $projectRoot")
    val scanResult = OwnershipScanner.doScan(projectRoot)
    logger.info("Scan completed, ${scanResult.size} files to parse")
    val parsedFiles = scanResult.map { parse(projectRoot, it) }
    val mapping = buildMapping(parsedFiles)
    logger.info("Built ownership mapping with ${mapping.entries.size} entries")
    return mapping
  }

  fun getMappingFilePath(projectRoot: Path): Path {
    return projectRoot / Constants.OWNERSHIP_MAPPING_FILE_RELATIVE_PATH
  }

  fun saveMapping(projectRoot: Path, mapping: OwnershipMapping) {
    val content = generateMappingFile(mapping)
    val mappingFile = getMappingFilePath(projectRoot)
    mappingFile.createParentDirectories()
    mappingFile.writeText(content, Charsets.UTF_8)
  }

  fun generateMappingFile(mapping: OwnershipMapping): String {
    logger.info("Serializing ownership mapping of ${mapping.entries.size} entries")
    val mappingToSerialize = OwnershipMappingConfig(mapping)

    return buildString {
      appendLine("# ${Constants.GENERATED_FILES_HEADER}")
      appendLine(Yaml.default.encodeToString(mappingToSerialize))
    }
  }

  fun loadMapping(projectRoot: Path): OwnershipMapping? {
    val mappingFile = getMappingFilePath(projectRoot)
    if (!mappingFile.exists()) return null

    return try {
      logger.info("Loading ownership mapping from $mappingFile")
      val content = mappingFile.readText(Charsets.UTF_8)
      val model = Yaml.default.decodeFromString<OwnershipMappingConfig>(content).toModel()
      logger.info("Loaded ownership mapping with ${model.entries.size} entries")
      model
    }
    catch (e: Exception) {
      logger.severe("Failed to parse $mappingFile: ${e.message}")
      throw IllegalStateException("Failed to parse $mappingFile", e)
    }
  }

  private fun buildMapping(files: List<OwnershipFile>): OwnershipMapping {
    val fileToDepth = files.groupBy { it.depth }

    val result = mutableListOf<OwnershipMappingEntry>()

    fileToDepth.keys.sorted().forEach { depth ->
      val filesAtDepth = fileToDepth.getValue(depth)
      filesAtDepth.sortedBy { it.sourceFilePath }.forEach { file ->
        file.entries.forEach { entry ->
          entry.rules.forEach { rule ->
            result += OwnershipMappingEntry(
              sourceFile = file.sourceFilePath,
              rule = "${file.rulePrefix}/${rule.removePrefix("/")}",
              owner = GroupName.build(file.sourceFilePath, entry.owner),
            ).also { check(it.depth == depth) }
          }
        }
      }
    }

    return OwnershipMapping(result)
  }

  private fun parse(projectRoot: Path, ownershipFile: Path): OwnershipFile {
    logger.info("Parsing ownership file $ownershipFile")
    val lines = ownershipFile.readLines(Charsets.UTF_8)
    val entries = mutableListOf<OwnershipFileEntry>()

    val nextOwnerRules = mutableListOf<String>()

    lines.forEachIndexed { index, line ->
      when {
        // owner section end
        line.startsWith("owner ") -> {
          val matchResult = OWNER_LINE_REGEX.matchEntire(line)
          if (matchResult == null) {
            logger.severe("${ownershipFile.relativeTo(projectRoot)}: Invalid line $index, allowed format: owner 'owner-name'")
            error("${ownershipFile.relativeTo(projectRoot)}: Invalid line $index, allowed format: owner 'owner-name'")
          }
          val owner = matchResult.groupValues[1]
          val rules =
            if (nextOwnerRules.isEmpty()) listOf("/**") // no rules specified = all files implicitly
            else nextOwnerRules.toList()

          nextOwnerRules.clear()
          entries.add(OwnershipFileEntry(owner, rules))
        }

        // rule
        line.startsWith("/") -> nextOwnerRules.add(line)
        // comment
        line.startsWith("#") -> {}

        line.isBlank() -> {}

        else -> {
          logger.severe("${ownershipFile.relativeTo(projectRoot)} Invalid line $index, allowed prefixes: ['#', 'owner', '/']")
          error("${ownershipFile.relativeTo(projectRoot)} Invalid line $index, allowed prefixes: ['#', 'owner', '/']")
        }
      }
    }

    if (nextOwnerRules.isNotEmpty()) {
      logger.severe("${ownershipFile.relativeTo(projectRoot)} Invalid file, 'owner' section is not closed")
      error("${ownershipFile.relativeTo(projectRoot)} Invalid file, 'owner' section is not closed")
    }

    val relativeToRoot = ownershipFile.relativeTo(projectRoot)

    return OwnershipFile(
      sourceFilePath = relativeToRoot.invariantSeparatorsPathString,
      rulePrefix = relativeToRoot.parent?.invariantSeparatorsPathString ?: "",
      entries = entries,
    )
  }

  private data class OwnershipFile(
    val sourceFilePath: String,
    val rulePrefix: String,
    val entries: List<OwnershipFileEntry>,
  ) {
    val depth = sourceFilePath.count { it == '/' }
  }

  private data class OwnershipFileEntry(
    val owner: String,
    val rules: List<String>,
  )

}
package com.intellij.codeowners.serialization

import com.intellij.codeowners.Constants
import com.intellij.codeowners.serialization.OwnershipScanner.doScan
import org.eclipse.jgit.ignore.IgnoreNode
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.RecursiveTask
import java.util.logging.Logger
import kotlin.io.path.invariantSeparatorsPathString

/**
 * See [doScan].
 */
object OwnershipScanner {
  private val logger: Logger = Logger.getLogger(OwnershipScanner::class.java.name)

  /**
   * Recursively scan [rootDir] for [com.intellij.codeowners.Constants.OWNERSHIP_FILE_NAME] files.
   *
   * Always skips processing '[rootDir]/.git'.
   *
   * Uses [ForkJoinPool] with work-stealing to parallelize directory traversal,
   * automatically balancing load across threads regardless of subtree sizes.
   */
  fun doScan(rootDir: Path): List<Path> {
    val dotGit = rootDir.resolve(".git").takeIf { Files.isDirectory(it) }
    logger.info("Starting ownership files scan in $rootDir")

    val result = ForkJoinPool.commonPool().invoke(ScanDirectoryAction(
      dir = rootDir,
      dotGit = dotGit,
      scanIgnoreFileName = Constants.OWNERSHIP_SCAN_IGNORE,
      parentChain = null,
    ))

    logger.info("Finished scan in $rootDir, found ${result.size} ownership files")
    return result
  }

  /**
   * Immutable singly-linked list of ignore contexts from current directory up to root.
   * Uses string-based prefix matching to avoid repeated [Path.relativize] allocations.
   * Thread-safe by construction (all fields are val).
   */
  private class IgnoreChain(
    private val dirPrefix: String,
    val ignore: IgnoreNode,
    val parent: IgnoreChain?,
  ) {
    fun isIgnored(entryInvariantPath: String, isDirectory: Boolean): Boolean {
      var current: IgnoreChain? = this
      while (current != null) {
        if (entryInvariantPath.startsWith(current.dirPrefix)) {
          val rel = entryInvariantPath.substring(current.dirPrefix.length)
          val decision: Boolean? = current.ignore.checkIgnored(rel, isDirectory)
          if (decision != null) {
            logger.finest("Ignore check for $entryInvariantPath relative '$rel' (dir=$isDirectory) => $decision")
            return decision
          }
        }
        current = current.parent
      }
      return false
    }
  }

  private class ScanDirectoryAction(
    private val dir: Path,
    private val dotGit: Path?,
    private val scanIgnoreFileName: String?,
    private val parentChain: IgnoreChain?,
  ) : RecursiveTask<List<Path>>() {
    override fun compute(): List<Path> {
      val dirInvariantPath = dir.invariantSeparatorsPathString
      val subDirs = mutableListOf<Pair<Path, String>>()
      val ownershipFiles = mutableListOf<Pair<Path, String>>()
      var ignoreFilePath: Path? = null

      try {
        Files.newDirectoryStream(dir).use { stream ->
          for (entry in stream) {
            val attrs: BasicFileAttributes = try {
              Files.readAttributes(entry, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            }
            catch (e: IOException) {
              logger.warning("Failed to load attributes for $entry: ${e.message}")
              continue
            }

            if (attrs.isDirectory) {
              if (dotGit != null && entry == dotGit) continue
              subDirs.add(entry to entry.invariantSeparatorsPathString)
            }
            else when (entry.fileName.toString()) {
              Constants.OWNERSHIP_FILE_NAME -> ownershipFiles.add(entry to entry.invariantSeparatorsPathString)
              scanIgnoreFileName -> ignoreFilePath = entry
            }
          }
        }
      }
      catch (e: IOException) {
        logger.warning("Cannot list directory $dir: ${e.message}")
        return emptyList()
      }

      val dirPrefix = if (dirInvariantPath.endsWith("/")) dirInvariantPath else "$dirInvariantPath/"
      val currentChain = ignoreFilePath
                           ?.let { loadIgnoreNode(it) }
                           ?.let { IgnoreChain(dirPrefix = dirPrefix, ignore = it, parent = parentChain) }
                         ?: parentChain

      val localResults = mutableListOf<Path>()
      for ((entry, entryPath) in ownershipFiles) {
        if (currentChain == null || !currentChain.isIgnored(entryPath, isDirectory = false)) {
          logger.info("Found ownership file: $entry")
          localResults.add(entry)
        }
      }

      val subtasks = mutableListOf<ScanDirectoryAction>()
      for ((entry, entryPath) in subDirs) {
        if (currentChain == null || !currentChain.isIgnored(entryPath, isDirectory = true)) {
          subtasks.add(ScanDirectoryAction(dir = entry, dotGit = null, scanIgnoreFileName = scanIgnoreFileName, parentChain = currentChain))
        }
        else {
          logger.info("Directory ignored by rules: $entry")
        }
      }

      if (subtasks.isNotEmpty()) {
        invokeAll(subtasks)
        for (task in subtasks) localResults.addAll(task.join())
      }

      return localResults
    }
  }

  private fun loadIgnoreNode(path: Path): IgnoreNode? {
    return try {
      val node = Files.newInputStream(path).use { input ->
        IgnoreNode().apply { parse(input) }
      }
      logger.info("Loaded ignore rules from $path")
      node
    }
    catch (e: IOException) {
      logger.warning("Failed to load ignore node for $path: ${e.message}")
      null
    }
  }
}

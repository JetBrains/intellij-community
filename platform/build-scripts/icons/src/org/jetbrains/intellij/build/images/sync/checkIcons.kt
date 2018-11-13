// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import org.jetbrains.intellij.build.images.imageSize
import org.jetbrains.intellij.build.images.isImage
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Path
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.streams.toList

private lateinit var icons: Map<String, GitObject>
private lateinit var devIcons: Map<String, GitObject>

fun main(args: Array<String>) = try {
  checkIcons()
}
catch (e: Throwable) {
  e.printStackTrace()
}

internal fun checkIcons(context: Context = Context(), loggerImpl: Consumer<String> = Consumer { println(it) }) {
  logger = loggerImpl
  context.iconsRepo = findGitRepoRoot(context.iconsRepoDir)
  icons = readIconsRepo(context.iconsRepo, context.iconsRepoDir)
  val devRepoRoot = findGitRepoRoot(context.devRepoDir)
  val devRepoVcsRoots = vcsRoots(devRepoRoot)
  devIcons = readDevRepo(devRepoRoot, context.devRepoDir, devRepoVcsRoots, context.skipDirsPattern)
  val devIconsTmp = HashMap(devIcons)
  val modified = mutableListOf<String>()
  val consistent = mutableListOf<String>()
  icons.forEach { icon, gitObject ->
    if (!devIconsTmp.containsKey(icon)) {
      context.addedByDesigners += icon
    }
    else if (gitObject.hash != devIconsTmp[icon]?.hash) {
      modified += icon
    }
    else {
      consistent += icon
    }
    devIconsTmp.remove(icon)
  }
  context.addedByDev = devIconsTmp.keys
  callWithTimer("Searching for changed icons..") {
    Stream.of(
      { SearchType.MODIFIED to modifiedByDev(modified) },
      { SearchType.REMOVED_BY_DEV to removedByDev(context.addedByDesigners, devRepoVcsRoots, File(context.devRepoDir)) },
      {
        val iconsDir = File(context.iconsRepoDir).relativeTo(context.iconsRepo).path.let { if (it.isEmpty()) "" else "$it/" }
        SearchType.REMOVED_BY_DESIGNERS to removedByDesigners(context.addedByDev, context.iconsRepo, iconsDir)
      }
    ).parallel().map { it() }.toList().forEach {
      val (searchType, searchResult) = it
      when (searchType) {
        SearchType.MODIFIED -> {
          context.modifiedByDev = searchResult
          context.modifiedByDesigners = modified.filter { file ->
            !context.modifiedByDev.contains(file)
          }.toMutableList()
        }
        SearchType.REMOVED_BY_DEV -> {
          context.removedByDev = searchResult
          context.addedByDesigners.removeAll(searchResult)
        }
        SearchType.REMOVED_BY_DESIGNERS -> {
          context.removedByDesigners = searchResult
          context.addedByDev.removeAll(searchResult)
        }
      }
    }
  }
  syncIcons(context, devIcons, icons)
  report(context, devRepoRoot, devIcons.size, icons.size, skippedDirs.size, consistent)
}

private enum class SearchType { MODIFIED, REMOVED_BY_DEV, REMOVED_BY_DESIGNERS }

private fun readIconsRepo(iconsRepo: File, iconsRepoDir: String) = protectStdErr {
  listGitObjects(iconsRepo, iconsRepoDir) {
    // read icon hashes
    isValidIcon(it.toPath())
  }.also {
    if (it.isEmpty()) error("Icons repo doesn't contain icons")
  }
}

private fun readDevRepo(devRepoRoot: File, devRepoDir: String,
                        devRepoVcsRoots: List<File>, skipDirsPattern: String?) = protectStdErr {
  val testRoots = searchTestRoots(devRepoRoot.absolutePath)
  log("Found ${testRoots.size} test roots")
  if (skipDirsPattern != null) log("Using pattern $skipDirsPattern to skip dirs")
  val skipDirsRegex = skipDirsPattern?.toRegex()
  val devRepoIconFilter = { file: File ->
    // read icon hashes skipping test roots
    !inTestRoot(file, testRoots, skipDirsRegex) && isValidIcon(file.toPath())
  }
  val devIcons = if (devRepoVcsRoots.size == 1
                     && devRepoVcsRoots.contains(devRepoRoot)) {
    // read icons from devRepoRoot
    listGitObjects(devRepoRoot, devRepoDir, devRepoIconFilter)
  }
  else {
    // read icons from multiple repos in devRepoRoot
    listGitObjects(devRepoRoot, devRepoVcsRoots, devRepoIconFilter)
  }
  if (devIcons.isEmpty()) error("Dev repo doesn't contain icons")
  devIcons.toMutableMap()
}

private fun searchTestRoots(devRepoDir: String) = try {
  JpsSerializationManager.getInstance()
    .loadModel(devRepoDir, null)
    .project.modules.flatMap {
    it.getSourceRoots(JavaSourceRootType.TEST_SOURCE) +
    it.getSourceRoots(JavaResourceRootType.TEST_RESOURCE)
  }.mapTo(mutableSetOf()) { it.file }
}
catch (e: IOException) {
  e.printStackTrace()
  emptySet<File>()
}

private inline fun <T> protectStdErr(block: () -> T): T {
  val err = System.err
  return try {
    block()
  }
  finally {
    System.setErr(err)
  }
}

private val mutedStream = PrintStream(object : OutputStream() {
  override fun write(b: ByteArray) {}
  override fun write(b: ByteArray, off: Int, len: Int) {}
  override fun write(b: Int) {}
})

private fun isValidIcon(file: Path) = protectStdErr {
  try {
    System.setErr(mutedStream)
    // image
    isImage(file) && imageSize(file)?.let { size ->
      val pixels = if (file.fileName.toString().contains("@2x")) 64 else 32
      // small
      size.height <= pixels && size.width <= pixels
    } ?: false
  }
  catch (e: Exception) {
    log("WARNING: $file: ${e.message}")
    false
  }
}

private val skippedDirs = mutableSetOf<File>()

private fun inTestRoot(file: File, testRoots: Set<File>, skipDirsRegex: Regex?): Boolean {
  val inTestRoot = file.isDirectory &&
                   // is test root
                   (testRoots.contains(file) ||
                    // or matches pattern
                    skipDirsRegex != null && file.name.matches(skipDirsRegex))
  if (inTestRoot) skippedDirs += file
  return inTestRoot
         // or check parent
         || file.parentFile != null && inTestRoot(file.parentFile, testRoots, skipDirsRegex)
}

private fun removedByDesigners(addedByDev: Collection<String>,
                               iconsRepo: File, iconsDir: String) = addedByDev.parallelStream().filter {
  val byDesigners = latestChangeTime("$iconsDir$it", iconsRepo)
  // latest changes are made by designers
  byDesigners > 0 && latestChangeTime(devIcons[it]) < byDesigners
}.toList()

private fun removedByDev(addedByDesigners: Collection<String>,
                         devRepos: Collection<File>,
                         devRepoDir: File) = addedByDesigners.parallelStream().filter {
  val byDev = latestChangeTime(File(devRepoDir, it).absolutePath, devRepos)
  // latest changes are made by developers
  byDev > 0 && latestChangeTime(icons[it]) < byDev
}.toList()

private fun latestChangeTime(file: String, repos: Collection<File>): Long {
  for (repo in repos) {
    val prefix = "${repo.absolutePath}/"
    if (file.startsWith(prefix)) {
      val lct = latestChangeTime(file.removePrefix(prefix), repo)
      if (lct > 0) return lct
    }
  }
  return -1
}

private fun modifiedByDev(modified: Collection<String>) = modified.parallelStream()
  // latest changes are made by developers
  .filter { latestChangeTime(icons[it]) < latestChangeTime(devIcons[it]) }
  .collect(Collectors.toList())

private fun latestChangeTime(obj: GitObject?) =
  latestChangeTime(obj!!.path, obj.repo).also {
    if (it <= 0) error(obj.toString())
  }
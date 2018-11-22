// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import org.jetbrains.intellij.build.images.ImageExtension
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

fun main(args: Array<String>) = try {
  checkIcons()
}
catch (e: Throwable) {
  e.printStackTrace()
}

internal fun checkIcons(context: Context = Context(), loggerImpl: Consumer<String> = Consumer { println(it) }) {
  logger = loggerImpl
  context.iconsRepo = findGitRepoRoot(context.iconsRepoDir)
  context.icons = readIconsRepo(context.iconsRepo, context.iconsRepoDir)
  context.devRepoRoot = findGitRepoRoot(context.devRepoDir)
  val devRepoVcsRoots = vcsRoots(context.devRepoRoot)
  context.devIcons = readDevRepo(context.devRepoRoot, context.devRepoDir, devRepoVcsRoots, context.skipDirsPattern)
  callWithTimer("Searching for changed icons..") {
    if (context.iconsCommitHashesToSync.isNotEmpty()) {
      searchForChangedIconsByDesigners(context)
    }
    else {
      searchForAllChangedIcons(context, devRepoVcsRoots)
    }
  }
  syncIcons(context)
  report(context, skippedDirs.size)
}

private enum class SearchType { MODIFIED, REMOVED_BY_DEV, REMOVED_BY_DESIGNERS }

private fun searchForAllChangedIcons(context: Context, devRepoVcsRoots: Collection<File>) {
  val devIconsTmp = HashMap(context.devIcons)
  val modified = mutableListOf<String>()
  val consistent = mutableListOf<String>()
  context.icons.forEach { icon, gitObject ->
    when {
      !devIconsTmp.containsKey(icon) -> context.addedByDesigners += icon
      gitObject.hash != devIconsTmp[icon]?.hash -> modified += icon
      else -> consistent += icon
    }
    devIconsTmp.remove(icon)
  }
  context.addedByDev = devIconsTmp.keys
  Stream.of(
    { SearchType.MODIFIED to modifiedByDev(context, modified) },
    { SearchType.REMOVED_BY_DEV to removedByDev(context, context.addedByDesigners, devRepoVcsRoots, context.devRepoDir) },
    {
      val iconsDir = context.iconsRepoDir.relativeTo(context.iconsRepo).path.let { if (it.isEmpty()) "" else "$it/" }
      SearchType.REMOVED_BY_DESIGNERS to removedByDesigners(context, context.addedByDev, context.iconsRepo, iconsDir)
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

private fun searchForChangedIconsByDesigners(context: Context) {
  fun check(files: Collection<String>) = files
    .filter { ImageExtension.fromName(it) != null }
    .map { context.iconsRepo.resolve(it).toRelativeString(context.iconsRepoDir) }
  context.iconsCommitHashesToSync.forEach { commit ->
    changesFromCommit(context.iconsRepo, commit).forEach { type, files ->
      when (type) {
        ChangeType.ADDED -> context.addedByDesigners += check(files)
        ChangeType.MODIFIED -> context.modifiedByDesigners += check(files)
        ChangeType.DELETED -> context.removedByDesigners += check(files)
      }
    }
  }
}

private fun readIconsRepo(iconsRepo: File, iconsRepoDir: File) = protectStdErr {
  listGitObjects(iconsRepo, iconsRepoDir) { file, _ ->
    // read icon hashes
    isValidIcon(file.toPath())
  }.also {
    if (it.isEmpty()) error("Icons repo doesn't contain icons")
  }
}

private fun readDevRepo(devRepoRoot: File, devRepoDir: File,
                        devRepoVcsRoots: List<File>, skipDirsPattern: String?) = protectStdErr {
  val testRoots = searchTestRoots(devRepoRoot.absolutePath)
  log("Found ${testRoots.size} test roots")
  if (skipDirsPattern != null) log("Using pattern $skipDirsPattern to skip dirs")
  val skipDirsRegex = skipDirsPattern?.toRegex()
  val devRepoIconFilter = { file: File, repo: File ->
    !doSkip(file, repo, testRoots, skipDirsRegex) &&
    (isValidIcon(file.toPath()) || IconRobotsDataReader.isSyncForced(file))
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

@Volatile
private var skippedDirs = emptySet<File>()
private var skippedDirsGuard = Any()

private fun doSkip(file: File, repo: File, testRoots: Set<File>, skipDirsRegex: Regex?): Boolean {
  val skipDir = file.isDirectory &&
                // is test root
                (testRoots.contains(file) ||
                 // or matches skip dir pattern
                 skipDirsRegex != null && file.name.matches(skipDirsRegex))
  if (skipDir) synchronized(skippedDirsGuard) {
    skippedDirs += file
  }
  return skipDir ||
         // or sync skipped in icon-robots.txt
         IconRobotsDataReader.isSyncSkipped(file) ||
         // or check parent
         file.parentFile != null && doSkip(file.parentFile, repo, testRoots, skipDirsRegex)
}

private fun removedByDesigners(context: Context, addedByDev: Collection<String>,
                               iconsRepo: File, iconsDir: String) = addedByDev.parallelStream().filter {
  val byDesigners = latestChangeTime("$iconsDir$it", iconsRepo)
  // latest changes are made by designers
  byDesigners > 0 && latestChangeTime(context.devIcons[it]) < byDesigners
}.toList()

private fun removedByDev(context: Context,
                         addedByDesigners: Collection<String>,
                         devRepos: Collection<File>,
                         devRepoDir: File) = addedByDesigners.parallelStream().filter {
  val byDev = latestChangeTime(File(devRepoDir, it).absolutePath, devRepos)
  // latest changes are made by developers
  byDev > 0 && latestChangeTime(context.icons[it]) < byDev
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

private fun modifiedByDev(context: Context, modified: Collection<String>) = modified.parallelStream()
  // latest changes are made by developers
  .filter { latestChangeTime(context.icons[it]) < latestChangeTime(context.devIcons[it]) }
  .collect(Collectors.toList())

private fun latestChangeTime(obj: GitObject?) =
  latestChangeTime(obj!!.path, obj.repo).also {
    if (it <= 0) error(obj.toString())
  }
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
import java.nio.file.Files
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
  context.icons = readIconsRepo(context)
  context.devRepoRoot = findGitRepoRoot(context.devRepoDir)
  val devRepoVcsRoots = vcsRoots(context.devRepoRoot)
  context.devIcons = readDevRepo(context, devRepoVcsRoots)
  callWithTimer("Searching for changed icons..") {
    when {
      context.iconsCommitHashesToSync.isNotEmpty() -> searchForChangedIconsByDesigners(context)
      context.devIconsCommitHashesToSync.isNotEmpty() -> searchForChangedIconsByDev(context, devRepoVcsRoots)
      else -> searchForAllChangedIcons(context, devRepoVcsRoots)
    }
  }
  syncDevRepo(context)
  if (!context.iconsSyncRequired() && !context.devSyncRequired()) {
    if (isUnderTeamCity() && isPreviousBuildFailed()) {
      context.doFail("No changes are found")
    }
    else log("No changes are found")
  }
  else if (isUnderTeamCity()) {
    findCommitsToSync(context)
    createReviews(context)
    val investigator = if (context.isFail() && context.assignInvestigation) {
      assignInvestigation(context)
    }
    else null
    if (context.notifySlack) sendNotification(investigator, context)
  }
  syncIconsRepo(context)
  val report = report(context, skippedDirs.size)
  when {
    !isUnderTeamCity() -> log(report)
    context.isFail() -> context.doFail(report)
    // partial sync shouldn't make build successful
    context.devIconsCommitHashesToSync.isNotEmpty() && isPreviousBuildFailed() -> context.doFail(report)
    // reviews should be created
    context.iconsCommitHashesToSync.isNotEmpty() && context.devReviews().isEmpty() -> context.doFail(report)
    else -> log(report)
  }
}

private enum class SearchType { MODIFIED, REMOVED_BY_DEV, REMOVED_BY_DESIGNERS }

private fun searchForAllChangedIcons(context: Context, devRepoVcsRoots: Collection<File>) {
  log("Searching for all")
  val devIconsTmp = HashMap(context.devIcons)
  val modified = mutableListOf<String>()
  context.icons.forEach { icon, gitObject ->
    when {
      !devIconsTmp.containsKey(icon) -> context.byDesigners.added += icon
      gitObject.hash != devIconsTmp[icon]?.hash -> modified += icon
      else -> context.consistent += icon
    }
    devIconsTmp.remove(icon)
  }
  context.byDev.added += devIconsTmp.keys
  Stream.of(
    { SearchType.MODIFIED to modifiedByDev(context, modified) },
    { SearchType.REMOVED_BY_DEV to removedByDev(context, context.byDesigners.added, devRepoVcsRoots, context.devRepoDir) },
    {
      val iconsDir = context.iconsRepoDir.relativeTo(context.iconsRepo).path.let { if (it.isEmpty()) "" else "$it/" }
      SearchType.REMOVED_BY_DESIGNERS to removedByDesigners(context, context.byDev.added, context.iconsRepo, iconsDir)
    }
  ).parallel().map { it() }.toList().forEach {
    val (searchType, searchResult) = it
    when (searchType) {
      SearchType.MODIFIED -> {
        context.byDev.modified += searchResult
        context.byDesigners.modified += modified.filter { file ->
          !context.byDev.modified.contains(file)
        }.toMutableList()
      }
      SearchType.REMOVED_BY_DEV -> {
        context.byDev.removed += searchResult
        context.byDesigners.added.removeAll(searchResult)
      }
      SearchType.REMOVED_BY_DESIGNERS -> {
        context.byDesigners.removed += searchResult
        context.byDev.added.removeAll(searchResult)
      }
    }
  }
}

private fun searchForChangedIconsByDesigners(context: Context) {
  fun asIcons(files: Collection<String>) = files
    .filter { ImageExtension.fromName(it) != null }
    .map { context.iconsRepo.resolve(it).toRelativeString(context.iconsRepoDir) }
  ArrayList(context.iconsCommitHashesToSync).map {
    commitInfo(context.iconsRepo, it) ?: error("Commit $it is not found in ${context.iconsRepoName}")
  }.sortedBy { it.timestamp }.forEach {
    val commit = it.hash
    val before = context.iconsChanges().size
    changesFromCommit(context.iconsRepo, commit).forEach { type, files ->
      context.byDesigners.register(type, asIcons(files))
    }
    if (context.iconsChanges().size == before) {
      log("No icons in $commit, skipping")
      context.iconsCommitHashesToSync.remove(commit)
    }
  }
  log("Found ${context.iconsCommitHashesToSync.size} commits to sync from ${context.iconsRepoName} to ${context.devRepoName}")
  log(context.iconsCommitHashesToSync.joinToString())
}

private fun searchForChangedIconsByDev(context: Context, devRepoVcsRoots: List<File>) {
  fun asIcons(files: Collection<String>, repo: File) = files.asSequence()
    .filter { ImageExtension.fromName(it) != null }
    .map(repo::resolve)
    .filter(context.devIconsFilter)
    .map { it.toRelativeString(context.devRepoRoot) }.toList()
  ArrayList(context.devIconsCommitHashesToSync).mapNotNull { commit ->
    devRepoVcsRoots.asSequence()
      .map { repo -> callSafely { commitInfo(repo, commit) } }
      .filterNotNull().firstOrNull()
      .apply {
        if (this == null) {
          log("No repo is found for $commit, skipping")
          context.devIconsCommitHashesToSync.remove(commit)
        }
      }
  }.sortedBy { it.timestamp }.forEach {
    val commit = it.hash
    val before = context.devChanges().size
    changesFromCommit(it.repo, commit).forEach { type, files ->
      context.byDev.register(type, asIcons(files, it.repo))
    }
    if (context.devChanges().size == before) {
      log("No icons in $commit, skipping")
      context.devIconsCommitHashesToSync.remove(commit)
    }
  }
  log("Found ${context.devIconsCommitHashesToSync.size} commits to sync from ${context.devRepoName} to ${context.iconsRepoName}")
  log(context.devIconsCommitHashesToSync.joinToString())
}

private fun readIconsRepo(context: Context) = protectStdErr {
  val (iconsRepo, iconsRepoDir) = context.iconsRepo to context.iconsRepoDir
  listGitObjects(iconsRepo, iconsRepoDir) { file ->
    // read icon hashes
    isValidIcon(file.toPath())
  }.also {
    if (it.isEmpty()) error("${context.iconsRepoName} repo doesn't contain icons")
  }
}

private fun readDevRepo(context: Context, devRepoVcsRoots: List<File>) = protectStdErr {
  val testRoots = searchTestRoots(context.devRepoRoot.absolutePath)
  log("Found ${testRoots.size} test roots")
  if (context.skipDirsPattern != null) {
    log("Using pattern ${context.skipDirsPattern} to skip dirs")
  }
  val skipDirsRegex = context.skipDirsPattern?.toRegex()
  context.devIconsFilter = { file: File ->
    filterDevIcon(file, testRoots, skipDirsRegex, context)
  }
  val devIcons = if (devRepoVcsRoots.size == 1
                     && devRepoVcsRoots.contains(context.devRepoRoot)) {
    // read icons from devRepoRoot
    listGitObjects(context.devRepoRoot, context.devRepoDir, context.devIconsFilter)
  }
  else {
    // read icons from multiple repos in devRepoRoot
    listGitObjects(context.devRepoRoot, devRepoVcsRoots, context.devIconsFilter)
  }
  if (devIcons.isEmpty()) error("${context.devRepoName} doesn't contain icons")
  devIcons.toMutableMap()
}

private fun filterDevIcon(file: File, testRoots: Set<File>, skipDirsRegex: Regex?, context: Context): Boolean {
  val path = file.toPath()
  if (doSkip(file, testRoots, skipDirsRegex)) return false
  return Files.exists(path) && isValidIcon(path) ||
         // if not exists then check respective icon in icons repo
         !Files.exists(path) && isValidIcon(context.iconsRepoDir.resolve(file.toRelativeString(context.devRepoRoot)).toPath()) ||
         IconRobotsDataReader.isSyncForced(file)
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
    Files.exists(file) && isImage(file) && imageSize(file)?.let { size ->
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

private fun doSkip(file: File, testRoots: Set<File>, skipDirsRegex: Regex?): Boolean {
  val skipDir = (file.isDirectory || !file.exists()) &&
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
         file.parentFile != null && doSkip(file.parentFile, testRoots, skipDirsRegex)
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
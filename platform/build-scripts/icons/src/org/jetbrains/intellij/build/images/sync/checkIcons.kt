// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import org.jetbrains.intellij.build.images.ImageExtension
import org.jetbrains.intellij.build.images.isImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.streams.toList

fun main(args: Array<String>) {
  if (args.isNotEmpty()) System.setProperty(Context.iconsCommitHashesToSyncArg, args.joinToString())
  checkIcons()
}

internal fun checkIcons(context: Context = Context(), loggerImpl: Consumer<String> = Consumer(::println)) {
  // required to load com.intellij.ide.plugins.newui.PluginLogo
  System.setProperty("java.awt.headless", "true")
  logger = loggerImpl
  val devRepoVcsRoots = vcsRoots(context.devRepoRoot)
  callWithTimer("Searching for changed icons..") {
    when {
      context.iconsCommitHashesToSync.isNotEmpty() -> searchForChangedIconsByDesigners(context)
      context.devIconsCommitHashesToSync.isNotEmpty() -> searchForChangedIconsByDev(context, devRepoVcsRoots)
      else -> {
        context.icons = readIconsRepo(context)
        context.devIcons = readDevRepo(context, devRepoVcsRoots)
        searchForAllChangedIcons(context, devRepoVcsRoots)
      }
    }
  }
  syncDevRepo(context)
  findCommitsToSync(context)
  when {
    !context.devIconsSyncAll && !context.iconsSyncRequired() && !context.devSyncRequired() -> log("No changes are found")
    isUnderTeamCity() -> {
      if (context.devCommitsToSync.isNotEmpty()) try {
        push(context)
      }
      catch (e: Throwable) {
        val investigator = Investigator(DEFAULT_INVESTIGATOR)
        assignInvestigation(investigator)
        if (context.notifySlack) callSafely {
          notifySlackChannel(investigator, context)
        }
        throw e
      }
      if (context.doSyncDevRepo || context.iconsCommitHashesToSync.isNotEmpty()) {
        commitAndPush(context)
      }
    }
    else -> syncIconsRepo(context)
  }
  val report = report(context, skippedDirs.size)
  if (isUnderTeamCity() && context.isFail()) {
    context.doFail(report)
  }
  else {
    log(report)
  }
}

private fun push(context: Context) {
  val pushedCommits = pushToIconsRepo(context)
  if (pushedCommits.isNotEmpty() && context.notifySlack) {
    notifySlackChannel(
      pushedCommits.joinToString("\n") { pushedCommit ->
        val devCommitsLinks = context.devCommitsToSync
          .values.asSequence().flatten()
          .filter { it.committer == pushedCommit.committer }
          .map { "'${it.subject}'" }
          .joinToString()
        "Icons from $devCommitsLinks are synced"
      }, context, success = true
    )
  }
}

private enum class SearchType { MODIFIED, REMOVED_BY_DEV, REMOVED_BY_DESIGNERS }

private fun searchForAllChangedIcons(context: Context, devRepoVcsRoots: Collection<Path>) {
  log("Searching for all")
  val devIconsTmp = HashMap(context.devIcons)
  val modified = mutableListOf<String>()
  context.icons.forEach { (icon, gitObject) ->
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
      val iconsDir = context.iconRepo.relativize(context.iconRepoDir).toString().let { if (it.isEmpty()) "" else "$it/" }
      SearchType.REMOVED_BY_DESIGNERS to removedByDesigners(context, context.byDev.added, context.iconRepo, iconsDir)
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
  if (!isUnderTeamCity()) {
    gitPull(context.iconRepo)
  }

  fun asIcons(files: Collection<String>): List<String> {
    return files.asSequence()
      .filter { ImageExtension.fromName(it) != null }
      .map(context.iconRepo::resolve)
      .filter(context.iconFilter)
      .map { context.iconRepoDir.relativize(it).toString() }
      .toList()
  }

  ArrayList(context.iconsCommitHashesToSync).map {
    commitInfo(context.iconRepo, it) ?: error("Commit $it is not found in ${context.iconsRepoName}")
  }.sortedBy(CommitInfo::timestamp).forEach {
    val commit = it.hash
    val before = context.iconsChanges().size
    changesFromCommit(context.iconRepo, commit).forEach { (type, files) ->
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

private fun searchForChangedIconsByDev(context: Context, devRepoVcsRoots: List<Path>) {
  fun asIcons(files: Collection<String>, repo: Path): List<String> {
    return files.asSequence()
      .filter { ImageExtension.fromName(it) != null }
      .map { repo.resolve(it) }
      .filter(context.devIconsFilter)
      .map { context.devRepoRoot.relativize(it).toString() }
      .toList()
  }

  ArrayList(context.devIconsCommitHashesToSync).mapNotNull { commit ->
    devRepoVcsRoots.asSequence().map { repo ->
      try {
        commitInfo(repo, commit)
      }
      catch (ignored: Exception) {
        null
      }
    }.filterNotNull().firstOrNull().apply {
      if (this == null) {
        log("No repo is found for $commit, skipping")
        context.devIconsCommitHashesToSync.remove(commit)
      }
    }
  }.sortedBy { it.timestamp }.forEach {
    val commit = it.hash
    val before = context.devChanges().size
    changesFromCommit(it.repo, commit).forEach { (type, files) ->
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
  val (iconsRepo, iconsRepoDir) = context.iconRepo to context.iconRepoDir
  listGitObjects(iconsRepo, iconsRepoDir, context.iconFilter).also {
    if (it.isEmpty()) log("${context.iconsRepoName} repo doesn't contain icons")
  }
}

private fun readDevRepo(context: Context, devRepoVcsRoots: List<Path>) = protectStdErr {
  if (context.skipDirsPattern != null) {
    log("Using pattern ${context.skipDirsPattern} to skip dirs")
  }
  val devIcons = if (devRepoVcsRoots.size == 1 && devRepoVcsRoots.contains(context.devRepoRoot)) {
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

internal fun filterDevIcon(file: Path, testRoots: Set<Path>, skipDirsRegex: Regex?, context: Context): Boolean {
  if (!isImage(file) || doSkip(file, testRoots, skipDirsRegex)) return false
  val icon = Icon(file)
  return icon.isValid ||
         // if not exists then check respective icon in icons repo
         !Files.exists(file) && Icon(context.iconRepoDir.resolve(context.devRepoRoot.relativize(file).toString())).isValid ||
         IconRobotsDataReader.isSyncForced(file)
}

@Volatile
private var skippedDirs = emptySet<Path>()
private var skippedDirsGuard = Any()

private fun doSkip(file: Path, testRoots: Set<Path>, skipDirsRegex: Regex?): Boolean {
  val skipDir = (Files.isDirectory(file) || !Files.exists(file)) &&
                // is test root
                (testRoots.contains(file) ||
                 // or matches skip dir pattern
                 skipDirsRegex != null && file.fileName?.toString()?.matches(skipDirsRegex) == true)
  if (skipDir) synchronized(skippedDirsGuard) {
    skippedDirs = skippedDirs + file
  }
  return skipDir ||
         // or sync skipped in icon-robots.txt
         IconRobotsDataReader.isSyncSkipped(file) ||
         // or check parent
         file.parent != null && doSkip(file.parent, testRoots, skipDirsRegex)
}

private fun removedByDesigners(context: Context, addedByDev: Collection<String>, iconRepo: Path, iconsDir: String): List<String> {
  return addedByDev.parallelStream().filter {
    val byDesigners = latestChangeTime("$iconsDir$it", iconRepo)
    // latest changes are made by designers
    val latestChangeTime = latestChangeTime(context.devIcons[it])
    latestChangeTime > 0 && byDesigners > 0 && latestChangeTime < byDesigners
  }.toList()
}

private fun removedByDev(context: Context,
                         addedByDesigners: Collection<String>,
                         devRepos: Collection<Path>,
                         devRepoDir: Path): List<String> {
  return addedByDesigners.parallelStream().filter {
    val byDev = latestChangeTime(devRepoDir.resolve(it).toAbsolutePath().toString(), devRepos)
    // latest changes are made by developers
    byDev > 0 && latestChangeTime(context.icons[it]) < byDev
  }.toList()
}

private fun latestChangeTime(file: String, repos: Collection<Path>): Long {
  for (repo in repos) {
    val prefix = "${repo.toAbsolutePath()}/"
    if (file.startsWith(prefix)) {
      val lct = latestChangeTime(file.removePrefix(prefix), repo)
      if (lct > 0) return lct
    }
  }
  return -1
}

private fun modifiedByDev(context: Context, modified: Collection<String>) = modified.parallelStream().filter {
  // latest changes are made by developers
  val latestChangeTimeByDev = latestChangeTime(context.devIcons[it])
  latestChangeTimeByDev > 0 && latestChangeTime(context.icons[it]) < latestChangeTimeByDev
}.collect(Collectors.toList())

private fun latestChangeTime(obj: GitObject?) = latestChangeTime(obj!!.path, obj.repo)
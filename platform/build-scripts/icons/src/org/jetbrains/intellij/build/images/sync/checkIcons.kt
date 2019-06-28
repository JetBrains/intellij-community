// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import org.jetbrains.intellij.build.images.ImageExtension
import org.jetbrains.intellij.build.images.isImage
import java.io.File
import java.nio.file.Files
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
  context.iconsRepo = findGitRepoRoot(context.iconsRepoDir)
  context.devRepoRoot = findGitRepoRoot(context.devRepoDir)
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
  when {
    !context.devIconsSyncAll && !context.iconsSyncRequired() && !context.devSyncRequired() -> log("No changes are found")
    isUnderTeamCity() -> {
      findCommitsToSync(context)
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
        createReviewForDev(context)?.also {
          context.createdReviews = listOf(it)
        }
      }
    }
    else -> syncIconsRepo(context)
  }
  val report = report(context, skippedDirs.size)
  if (isUnderTeamCity() &&
      (context.isFail() ||
       // reviews should be created
       context.iconsCommitHashesToSync.isNotEmpty() && context.devReviews().isEmpty())) context.doFail(report)
  else log(report)
}

private fun push(context: Context) {
  val pushedCommits = pushToIconsRepo(context)
  if (pushedCommits.isNotEmpty() && context.notifySlack) {
    notifySlackChannel(
      pushedCommits.joinToString("\n") { pushedCommit ->
        val devCommitsLinks = context.devCommitsToSync
          .values.asSequence().flatten()
          .filter { it.committer == pushedCommit.committer }
          .map { slackLink("'${it.subject}'", commitUrl(UPSOURCE_DEV_PROJECT_ID, it)) }
          .joinToString()
        val commitUrl = commitUrl(UPSOURCE_ICONS_PROJECT_ID, pushedCommit)
        "Icons from $devCommitsLinks are ${slackLink("synced", commitUrl)}"
      }, context, success = true
    )
  }
}

private enum class SearchType { MODIFIED, REMOVED_BY_DEV, REMOVED_BY_DESIGNERS }

private fun searchForAllChangedIcons(context: Context, devRepoVcsRoots: Collection<File>) {
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
  if (!isUnderTeamCity()) gitPull(context.iconsRepo)
  fun asIcons(files: Collection<String>) = files
    .filter { ImageExtension.fromName(it) != null }
    .map { context.iconsRepo.resolve(it).toRelativeString(context.iconsRepoDir) }
  ArrayList(context.iconsCommitHashesToSync).map {
    commitInfo(context.iconsRepo, it) ?: error("Commit $it is not found in ${context.iconsRepoName}")
  }.sortedBy(CommitInfo::timestamp).forEach {
    val commit = it.hash
    val before = context.iconsChanges().size
    changesFromCommit(context.iconsRepo, commit).forEach { (type, files) ->
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
  val (iconsRepo, iconsRepoDir) = context.iconsRepo to context.iconsRepoDir
  listGitObjects(iconsRepo, iconsRepoDir) { file ->
    // read icon hashes
    Icon(file).isValid
  }.also {
    if (it.isEmpty()) error("${context.iconsRepoName} repo doesn't contain icons")
  }
}

private fun readDevRepo(context: Context, devRepoVcsRoots: List<File>) = protectStdErr {
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

internal fun filterDevIcon(file: File, testRoots: Set<File>, skipDirsRegex: Regex?, context: Context): Boolean {
  val path = file.toPath()
  if (!isImage(path) || doSkip(file, testRoots, skipDirsRegex)) return false
  val icon = Icon(file)
  return icon.isValid ||
         // if not exists then check respective icon in icons repo
         !Files.exists(path) && Icon(context.iconsRepoDir.resolve(file.toRelativeString(context.devRepoRoot))).isValid ||
         IconRobotsDataReader.isSyncForced(file)
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
  val latestChangeTime = latestChangeTime(context.devIcons[it])
  latestChangeTime > 0 && byDesigners > 0 && latestChangeTime < byDesigners
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

private fun modifiedByDev(context: Context, modified: Collection<String>) = modified.parallelStream().filter {
  // latest changes are made by developers
  val latestChangeTimeByDev = latestChangeTime(context.devIcons[it])
  latestChangeTimeByDev > 0 && latestChangeTime(context.icons[it]) < latestChangeTimeByDev
}.collect(Collectors.toList())

private fun latestChangeTime(obj: GitObject?) = latestChangeTime(obj!!.path, obj.repo)
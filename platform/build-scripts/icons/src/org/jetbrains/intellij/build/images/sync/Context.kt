// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.intellij.build.images.ImageExtension
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.function.Consumer

internal class Context(private val errorHandler: Consumer<String> = Consumer { error(it) },
                       private val devIconsVerifier: Runnable? = null) {
  val devRepoDir: File
  val iconsRepoDir: File
  val iconsRepoName: String
  val devRepoName: String
  val skipDirsPattern: String?
  val doSyncIconsRepo: Boolean
  val doSyncDevRepo: Boolean
  val doSyncRemovedIconsInDev: Boolean
  val doSyncIconsAndCreateReview: Boolean
  val doSyncDevIconsAndCreateReview: Boolean
  private val failIfSyncDevIconsRequired: Boolean
  val assignInvestigation: Boolean
  val notifySlack: Boolean
  lateinit var iconsRepo: File
  lateinit var devRepoRoot: File
  val byDev = Changes()
  val byCommit = mutableMapOf<String, Changes>()
  val consistent: MutableCollection<String> = mutableListOf()
  var createdReviews: Collection<Review> = emptyList()
  lateinit var icons: Map<String, GitObject>
  lateinit var devIcons: Map<String, GitObject>
  var devCommitsToSync: Map<File, Collection<CommitInfo>> = emptyMap()
  var iconsCommitsToSync: Map<File, Collection<CommitInfo>> = emptyMap()
  val iconsCommitHashesToSync: MutableSet<String>
  val devIconsCommitHashesToSync: MutableSet<String>
  lateinit var devIconsFilter: (File) -> Boolean

  fun devChanges() = byDev.all()
  fun iconsChanges() = byDesigners.all()

  fun iconsSyncRequired() = devChanges().isNotEmpty()
  fun devSyncRequired() = iconsChanges().isNotEmpty()

  fun devReview(): Review? = createdReviews.firstOrNull { it.projectId == UPSOURCE_DEV_PROJECT_ID }
  fun iconsReview(): Review? = createdReviews.firstOrNull { it.projectId == UPSOURCE_ICONS_PROJECT_ID }
  fun verifyDevIcons() = devIconsVerifier?.run()
  fun doFail(report: String) {
    log(report)
    errorHandler.accept(report)
  }

  fun isFail() = (notifySlack || assignInvestigation) &&
                 (iconsSyncRequired() || failIfSyncDevIconsRequired && devSyncRequired())

  init {
    fun bool(arg: String) = System.getProperty(arg)?.toBoolean() ?: false

    fun ignoreCaseInDirName(path: String) = Files.list(Paths.get(path).parent)
      .filter { it.toAbsolutePath().toString().equals(FileUtil.toSystemDependentName(path), ignoreCase = true) }
      .findFirst()
      .get()
      .toAbsolutePath()
      .toFile()

    fun commits(arg: String) = System.getProperty(arg)
                                 ?.takeIf { it.trim() != "*" }
                                 ?.split(",", ";", " ")
                                 ?.filter { it.isNotBlank() }
                                 ?.mapTo(mutableSetOf(), String::trim) ?: mutableSetOf<String>()

    val repoArg = "repos"
    val iconsRepoNameArg = "icons.repo.name"
    val devRepoNameArg = "dev.repo.name"
    val patternArg = "skip.dirs.pattern"
    val syncIconsArg = "sync.icons"
    val syncDevIconsArg = "sync.dev.icons"
    val syncRemovedIconsInDevArg = "sync.dev.icons.removed"
    val failIfSyncDevIconsRequiredArg = "fail.if.sync.dev.icons.required"
    val syncIconsAndCreateReviewArg = "sync.icons.and.create.review"
    val syncDevIconsAndCreateReviewArg = "sync.dev.icons.and.create.review"
    val assignInvestigationArg = "assign.investigation"
    val notifySlackArg = "notify.slack"
    val iconsCommitHashesToSyncArg = "sync.icons.commits"
    val devIconsCommitHashesToSyncArg = "sync.dev.icons.commits"
    val repos = System.getProperty(repoArg)?.split(",") ?: emptyList()
    iconsRepoName = System.getProperty(iconsRepoNameArg) ?: "icons repo"
    devRepoName = System.getProperty(devRepoNameArg) ?: "dev repo"
    if (repos.size < 2) error("""
      |Usage: $repoArg=<devRepoDir>,<iconsRepoDir> [option=...]
      |Options:
      |* `$repoArg` - comma-separated repository paths, first is developers' repo, second is designers'
      |* `$iconsRepoNameArg` - designers' repo name (for report)
      |* `$devRepoNameArg` - developers' repo name (for report)
      |* `$patternArg` - test data folders regular expression
      |* `$syncDevIconsArg` - sync icons in developers' repo. Switch off to run check only
      |* `$syncIconsArg` - sync icons in designers' repo. Switch off to run check only
      |* `$syncRemovedIconsInDevArg` - remove icons in developers' repo removed by designers
      |* `$syncIconsAndCreateReviewArg` - sync icons in designers' repo and create branch review, implies $syncIconsArg
      |* `$syncDevIconsAndCreateReviewArg` - sync icons in developers' repo and create branch review, implies $syncDevIconsArg
      |* `$failIfSyncDevIconsRequiredArg` - do fail if icons sync in developers' repo is required
      |* `$assignInvestigationArg` - assign investigation if required
      |* `$notifySlackArg` - notify slack channel if required
      |* `$iconsCommitHashesToSyncArg` - commit hashes in designers' repo to sync icons from
      |* `$devIconsCommitHashesToSyncArg` - commit hashes in developers' repo to sync icons from
    """.trimMargin())
    devRepoDir = ignoreCaseInDirName(repos[0])
    iconsRepoDir = ignoreCaseInDirName(repos[1])
    skipDirsPattern = System.getProperty(patternArg)
    iconsCommitHashesToSync = commits(iconsCommitHashesToSyncArg)
    devIconsCommitHashesToSync = commits(devIconsCommitHashesToSyncArg)
      .takeIf { it.isNotEmpty() } ?: System.getProperty("teamcity.build.changedFiles.file")
      ?.takeIf { System.getProperty(devIconsCommitHashesToSyncArg)?.let(String::trim) != "*" }
      ?.takeIf { !isScheduled() }
      ?.let(::File)
      ?.takeIf(File::exists)
      ?.let(FileUtil::loadFile)
      ?.takeIf { !it.contains("<personal>") }
      ?.let(StringUtil::splitByLines)
      ?.mapNotNull {
        val split = it.split(':')
        if (split.size != 3) {
          log("WARNING: malformed line in 'teamcity.build.changedFiles.file' : $it")
          return@mapNotNull null
        }
        val (file, _, commit) = split
        if (ImageExtension.fromName(file) != null) commit else null
      }?.toMutableSet() ?: mutableSetOf()
    doSyncIconsRepo = bool(syncIconsArg)
    doSyncDevRepo = bool(syncDevIconsArg)
    doSyncRemovedIconsInDev = bool(syncRemovedIconsInDevArg) || iconsCommitHashesToSync.isNotEmpty()
    doSyncIconsAndCreateReview = bool(syncIconsAndCreateReviewArg)
    doSyncDevIconsAndCreateReview = bool(syncDevIconsAndCreateReviewArg)
    failIfSyncDevIconsRequired = bool(failIfSyncDevIconsRequiredArg)
    assignInvestigation = bool(assignInvestigationArg)
    notifySlack = bool(notifySlackArg)
  }

  val byDesigners = Changes(includeRemoved = doSyncRemovedIconsInDev)
}
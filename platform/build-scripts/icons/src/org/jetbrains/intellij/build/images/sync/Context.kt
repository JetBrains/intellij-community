// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.createDirectories
import org.jetbrains.intellij.build.images.ImageExtension
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.concurrent.thread

internal class Context {
  companion object {
    const val iconsCommitHashesToSyncArg = "sync.icons.commits"
    private const val iconsRepoArg = "icons.repo"
  }

  var devRepoDir: Path
  var iconRepoDir: Path
  val iconsRepoName: String
  val devRepoName: String
  val skipDirsPattern: String?
  val doSyncIconsRepo: Boolean
  val doSyncDevRepo: Boolean
  val doSyncRemovedIconsInDev: Boolean
  private val failIfSyncDevIconsRequired: Boolean
  val notifySlack: Boolean
  val byDev = Changes()
  val byCommit = mutableMapOf<String, Changes>()
  val consistent: MutableCollection<String> = mutableListOf()
  var icons: Map<String, GitObject> = emptyMap()
  var devIcons: Map<String, GitObject> = emptyMap()
  var devCommitsToSync: Map<Path, Collection<CommitInfo>> = emptyMap()
  var iconsCommitsToSync: Map<Path, Collection<CommitInfo>> = emptyMap()
  val iconsCommitHashesToSync: MutableSet<String>
  val devIconsCommitHashesToSync: MutableSet<String>
  val devIconsSyncAll: Boolean
  val dryRun: Boolean = System.getenv("BUILD_IS_PERSONAL") == "true"

  init {
    val devRepoArg = "dev.repo"
    val iconsRepoNameArg = "icons.repo.name"
    val iconsRepoPathArg = "icons.repo.path"
    val devRepoNameArg = "dev.repo.name"
    val patternArg = "skip.dirs.pattern"
    val syncIconsArg = "sync.icons"
    val syncDevIconsArg = "sync.dev.icons"
    val syncRemovedIconsInDevArg = "sync.dev.icons.removed"
    val failIfSyncDevIconsRequiredArg = "fail.if.sync.dev.icons.required"
    val assignInvestigationArg = "assign.investigation"
    val notifySlackArg = "notify.slack"
    val devIconsSyncAllArg = "sync.dev.icons.all"
    @Suppress("unused")
    fun usage() = println("""
      |Usage: -D$devRepoArg=<devRepoDir> -D$iconsRepoArg=<iconsRepoDir> [-Doption=...]
      |Options:
      |* `$iconsRepoArg` - designers' repo
      |* `$devRepoArg` - developers' repo
      |* `$iconsRepoPathArg` - path in designers' repo
      |* `$iconsRepoNameArg` - designers' repo name (for report)
      |* `$devRepoNameArg` - developers' repo name (for report)
      |* `$patternArg` - regular expression for names of directories to skip
      |* `$syncDevIconsArg` - sync icons in developers' repo. Switch off to run check only
      |* `$syncIconsArg` - sync icons in designers' repo. Switch off to run check only
      |* `$syncRemovedIconsInDevArg` - remove icons in developers' repo removed by designers
      |* `$failIfSyncDevIconsRequiredArg` - do fail if icons sync in developers' repo is required
      |* `$assignInvestigationArg` - assign investigation if required
      |* `$notifySlackArg` - notify slack channel if required
      |* `$iconsCommitHashesToSyncArg` - commit hashes in designers' repo to sync icons from, implies $syncDevIconsArg
      |* `$devIconsSyncAllArg` - sync all changes from developers' repo to designers' repo, implies $syncIconsArg
    """.trimMargin())

    fun bool(arg: String) = System.getProperty(arg)?.toBoolean() ?: false

    fun commits(arg: String): MutableSet<String> {
      return System.getProperty(arg)
               ?.takeIf { it.trim() != "*" }
               ?.split(",", ";", " ")
               ?.filter { it.isNotBlank() }
               ?.mapTo(mutableSetOf(), String::trim) ?: mutableSetOf()
    }

    devRepoDir = findDirectoryIgnoringCase(System.getProperty(devRepoArg)) ?: run {
      warn("$devRepoArg not found")
      Paths.get(System.getProperty("user.dir"))
    }
    val iconsRepoRelativePath = System.getProperty(iconsRepoPathArg) ?: ""
    val iconsRepoRootDir = findDirectoryIgnoringCase(System.getProperty(iconsRepoArg)) ?: cloneIconsRepoToTempDir()
    iconRepoDir = iconsRepoRootDir.resolve(iconsRepoRelativePath)
    iconRepoDir.createDirectories()
    iconsRepoName = System.getProperty(iconsRepoNameArg) ?: "icons repo"
    devRepoName = System.getProperty(devRepoNameArg) ?: "dev repo"
    skipDirsPattern = System.getProperty(patternArg)
    doSyncDevRepo = bool(syncDevIconsArg)
    doSyncIconsRepo = bool(syncIconsArg)
    failIfSyncDevIconsRequired = bool(failIfSyncDevIconsRequiredArg)
    notifySlack = bool(notifySlackArg)
    iconsCommitHashesToSync = commits(iconsCommitHashesToSyncArg)
    doSyncRemovedIconsInDev = bool(syncRemovedIconsInDevArg) || iconsCommitHashesToSync.isNotEmpty()
    // scheduled build is always full check
    devIconsSyncAll = bool(devIconsSyncAllArg) || isScheduled()
    // read TeamCity provided changes
    devIconsCommitHashesToSync = System.getProperty("teamcity.build.changedFiles.file")
      // if icons sync is required
      ?.takeIf { doSyncIconsRepo }
      // or full check is not required
      ?.takeIf { !devIconsSyncAll }
      ?.let(::File)
      ?.takeIf(File::exists)
      ?.let(FileUtil::loadFile)
      ?.takeIf { !it.contains("<personal>") }
      ?.let(StringUtil::splitByLines)
      ?.mapNotNull {
        val split = it.split(':')
        if (split.size != 3) {
          warn("malformed line in 'teamcity.build.changedFiles.file' : $it")
          return@mapNotNull null
        }
        val (file, _, commit) = split
        if (ImageExtension.fromName(file) != null) commit else null
      }?.toMutableSet() ?: mutableSetOf()
  }

  val iconRepo: Path by lazy {
    findGitRepoRoot(iconRepoDir)
  }

  val devRepoRoot: Path by lazy {
    findGitRepoRoot(devRepoDir)
  }

  private fun cloneIconsRepoToTempDir(): Path {
    val uri = "ssh://git@git.jetbrains.team/ij/IntelliJIcons.git"
    log("Please clone $uri to the same folder where IntelliJ root is. Cloning to temporary directory..")
    val tmp = Files.createTempDirectory("icons-sync")
    Runtime.getRuntime().addShutdownHook(thread(start = false) {
      tmp.toFile().deleteRecursively()
    })
    return callWithTimer("Cloning $uri into $tmp") { gitClone(uri, tmp) }
  }

  val byDesigners = Changes(includeRemoved = doSyncRemovedIconsInDev)
  val devIconsFilter: (Path) -> Boolean by lazy {
    val skipDirsRegex = skipDirsPattern?.toRegex()
    val testRoots = searchTestRoots(devRepoRoot.toAbsolutePath().toString())
    log("Found ${testRoots.size} test roots")
    return@lazy { file: Path ->
      filterDevIcon(file, testRoots, skipDirsRegex, this)
    }
  }

  var iconFilter: (Path) -> Boolean = { Icon(it).isValid }

  fun devChanges() = byDev.all()
  fun iconsChanges() = byDesigners.all()

  fun iconsSyncRequired() = devChanges().isNotEmpty()
  fun devSyncRequired() = iconsChanges().isNotEmpty()

  fun doFail(report: String) {
    log(report)
    error(report)
  }

  fun isFail() = notifySlack && failIfSyncDevIconsRequired && devSyncRequired()

  private fun findDirectoryIgnoringCase(path: String?): Path? {
    if (path == null) {
      return null
    }

    val file = Paths.get(path)
    if (Files.isDirectory(file)) {
      return file
    }

    return file.parent?.toFile()?.listFiles()?.firstOrNull {
      it.absolutePath.equals(FileUtil.toSystemDependentName(path), ignoreCase = true)
    }?.toPath()
  }

  fun warn(message: String) = System.err.println(message)
}
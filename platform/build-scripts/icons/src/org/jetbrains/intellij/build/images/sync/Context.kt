// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.function.Consumer

internal class Context(val devIconsVerifier: Runnable? = null) {
  val devRepoDir: String
  val iconsRepoDir: String
  val skipDirsPattern: String?
  val doSyncIconsRepo: Boolean
  val doSyncDevRepo: Boolean
  val doSyncRemovedIconsInDev: Boolean
  val doSyncIconsAndCreateReview: Boolean
  val doSyncDevIconsAndCreateReview: Boolean
  val assignInvestigation: Boolean
  val notifySlack: Boolean
  lateinit var iconsRepo: File
  var addedByDev: MutableCollection<String> = mutableListOf()
  var removedByDev: MutableCollection<String> = mutableListOf()
  var modifiedByDev: MutableCollection<String> = mutableListOf()
  val addedByDesigners: MutableCollection<String> = mutableListOf()
  var removedByDesigners: MutableCollection<String> = mutableListOf()
  var modifiedByDesigners: MutableCollection<String> = mutableListOf()
  var createdReviews: Collection<Review> = emptyList()
  lateinit var errorHandler: Consumer<String>
  fun isSuccess() = addedByDev.isEmpty() && removedByDev.isEmpty() && modifiedByDev.isEmpty()

  init {
    fun bool(arg: String, default: Boolean = false) = System.getProperty(arg)?.toBoolean() ?: default

    fun ignoreCaseInDirName(path: String) = Files.list(Paths.get(path).parent)
      .filter { it.toAbsolutePath().toString().equals(FileUtil.toSystemDependentName(path), ignoreCase = true) }
      .findFirst()
      .get()
      .toAbsolutePath()
      .toString()

    val repoArg = "repos"
    val patternArg = "skip.dirs.pattern"
    val syncIconsArg = "sync.icons"
    val syncDevIconsArg = "sync.dev.icons"
    val syncRemovedIconsInDevArg = "sync.dev.icons.removed"
    val syncIconsAndCreateReviewArg = "sync.icons.and.create.review"
    val syncDevIconsAndCreateReviewArg = "sync.dev.icons.and.create.review"
    val assignInvestigationArg = "assign.investigation"
    val notifySlackArg = "notify.slack"
    val repos = System.getProperty(repoArg)?.split(",") ?: emptyList()
    if (repos.size < 2) error("""
      |Usage: $repoArg=<devRepoDir>,<iconsRepoDir> [option=...]
      |Options:
      |* `$repoArg` - comma-separated repository paths, first is developers' repo, second is designers'
      |* `$patternArg` - test data folders regular expression
      |* `$syncDevIconsArg` - update icons in developers' repo. Switch off to run check only
      |* `$syncIconsArg` - update icons in designers' repo. Switch off to run check only
      |* `$syncRemovedIconsInDevArg` - remove icons in developers' repo removed by designers
      |* `$syncIconsAndCreateReviewArg` - update icons in designers' repo and create branch review, implies $syncIconsArg
      |* `$syncDevIconsAndCreateReviewArg` - update icons in developers' repo and create branch review, implies $syncDevIconsArg
      |* `$assignInvestigationArg` - assign investigation if required
      |* `$notifySlackArg` - notify slack channel if required
    """.trimMargin())
    devRepoDir = ignoreCaseInDirName(repos[0])
    iconsRepoDir = ignoreCaseInDirName(repos[1])
    skipDirsPattern = System.getProperty(patternArg)
    doSyncIconsRepo = bool(syncIconsArg)
    doSyncDevRepo = bool(syncDevIconsArg)
    doSyncRemovedIconsInDev = bool(syncRemovedIconsInDevArg, default = true)
    doSyncIconsAndCreateReview = bool(syncIconsAndCreateReviewArg)
    doSyncDevIconsAndCreateReview = bool(syncDevIconsAndCreateReviewArg)
    assignInvestigation = bool(assignInvestigationArg)
    notifySlack = bool(notifySlackArg)
  }
}
// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync.dotnet

import org.jetbrains.intellij.build.images.generateIconsClasses
import org.jetbrains.intellij.build.images.isImage
import org.jetbrains.intellij.build.images.sync.*
import java.io.File
import java.nio.file.Path
import java.util.*

@Suppress("unused")
internal object DotnetIconSync {
  private class SyncPath(val iconsPath: String, val devPath: String)

  private val syncPaths = listOf(
    SyncPath("rider", "Rider/rider/icons/resources/rider"),
    SyncPath("net", "Rider/rider/icons/resources/resharper")
  )

  private val committer by lazy(::triggeredBy)
  private val context = Context().apply {
    iconsFilter = { file ->
      // need to filter designers' icons using developers' icon-robots.txt
      Icon(file).isValid && file.toRelativeString(iconsRepoDir)
        .let(devRepoDir::resolve)
        .let(IconRobotsDataReader::isSyncSkipped)
        .not()
    }
  }

  private val targetWaveNumber by lazy {
    val prop = "icons.sync.dotnet.wave.number"
    System.getProperty(prop) ?: error("Specify property $prop")
  }
  private val branchForMerge by lazy {
    val randomPart = UUID.randomUUID().toString().substring(1..4)
    "net$targetWaveNumber-icons-sync-$randomPart"
  }
  private val mergeRobotBuildConfiguration by lazy {
    val prop = "icons.sync.dotnet.merge.robot.build.conf"
    System.getProperty(prop) ?: error("Specify property $prop")
  }

  private fun step(msg: String) = println("\n** $msg")

  fun sync() {
    val tmpCommit = transformIconsToIdeaFormat()
    try {
      syncPaths.forEach {
        context.devRepoDir = context.devRepoRoot.resolve(it.devPath)
        context.iconsRepoDir = context.iconsRepo.resolve(it.iconsPath)
        step("Syncing icons for ${it.devPath}..")
        checkIcons(context)
      }
      generateClasses()
      createBranchForMerge()
      val changes = commitChanges(tmpCommit)
      if (changes != null && isUnderTeamCity()) {
        pushBranchForMerge()
        triggerMerge()
      }
    }
    finally {
      resetToPreviousCommit(context.iconsRepo)
    }
  }

  private fun transformIconsToIdeaFormat(): CommitInfo {
    step("Transforming icons from Dotnet to Idea format..")
    syncPaths.forEach {
      val path = context.iconsRepo.resolve(it.iconsPath).toPath()
      DotnetIconsTransformation.transformToIdeaFormat(path)
    }
    return muteStdOut {
      context.iconsRepo.commit(
        "temporary commit, shouldn't be pushed",
        "DotnetIconSyncRobot",
        "dotnet-icon-sync-robot-no-reply@jetbrains.com"
      )
    } ?: error("Unable to make a commit")
  }

  private fun generateClasses() {
    step("Generating classes..")
    generateIconsClasses(DotnetIconsClasses(context.devRepoDir.absolutePath))
  }

  private fun commitChanges(tmpCommit: CommitInfo): CommitInfo? {
    step("Committing changes..")
    val changes = context.iconsCommitsToSync.mapValues {
      it.value.filterNot { commit ->
        commit.hash == tmpCommit.hash
      }
    }
    val commit = context.devRepoRoot.commit(changes.commitMessage(), committer.name, committer.email) {
      it.fileName.toString().endsWith(".java")
    }
    if (commit != null) {
      println("Committed ${commit.hash} ${commit.subject}")
    }
    else {
      println("Nothing to commit")
    }
    return commit
  }

  private fun File.commit(message: String, user: String, email: String, filter: (Path) -> Boolean = { false }) =
    gitStatus(this, includeUntracked = true).filter {
      val path = toPath().resolve(it)
      isImage(path) || filter(path)
    }.let {
      if (it.isNotEmpty()) {
        stageFiles(it, this)
        commit(this, message, user, email)
        commitInfo(this)
      }
      else null
    }

  private fun createBranchForMerge() {
    step("Creating branch $branchForMerge..")
    execute(context.devRepoRoot, GIT, "checkout", "-B", branchForMerge)
  }

  private fun pushBranchForMerge() {
    step("Pushing $branchForMerge..")
    push(context.devRepoRoot, branchForMerge)
  }

  private fun triggerMerge() {
    step("Triggering merge with $mergeRobotBuildConfiguration..")
    val response = triggerBuild(mergeRobotBuildConfiguration, branchForMerge)
    println("Response is $response")
  }
}
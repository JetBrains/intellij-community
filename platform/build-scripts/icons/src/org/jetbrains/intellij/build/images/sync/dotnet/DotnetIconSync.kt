// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync.dotnet

import org.jetbrains.intellij.build.images.generateIconsClasses
import org.jetbrains.intellij.build.images.isImage
import org.jetbrains.intellij.build.images.shutdownAppScheduledExecutorService
import org.jetbrains.intellij.build.images.sync.*
import java.util.*

fun main() = DotnetIconSync.sync()

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
      file.toRelativeString(iconsRepoDir)
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
    try {
      transformIconsToIdeaFormat()
      syncPaths.forEach(this::sync)
      generateClasses()
      if (stageChanges().isEmpty()) {
        println("Nothing to commit")
      }
      else if (isUnderTeamCity()) {
        createBranchForMerge()
        commitChanges()
        pushBranchForMerge()
        triggerMerge()
      }
      println("Done.")
    }
    finally {
      shutdownAppScheduledExecutorService()
      cleanup(context.iconsRepo)
    }
  }

  private fun transformIconsToIdeaFormat() {
    step("Transforming icons from Dotnet to Idea format..")
    syncPaths.forEach {
      val path = context.iconsRepo.resolve(it.iconsPath).toPath()
      DotnetIconsTransformation.transformToIdeaFormat(path)
    }
  }

  private fun sync(path: SyncPath) {
    step("Syncing icons for ${path.devPath}..")
    context.devRepoDir = context.devRepoRoot.resolve(path.devPath)
    context.iconsRepoDir = context.iconsRepo.resolve(path.iconsPath)
    context.devRepoDir.walkTopDown().forEach {
      if (isImage(it)) {
        it.delete() || error("Unable to delete $it")
      }
    }
    context.iconsRepoDir.walkTopDown().forEach {
      if (isImage(it) && context.iconsFilter(it)) {
        val target = context.devRepoDir.resolve(it.toRelativeString(context.iconsRepoDir))
        it.copyTo(target, overwrite = true)
      }
    }
  }

  private fun generateClasses() {
    step("Generating classes..")
    generateIconsClasses(DotnetIconsClasses(context.devRepoDir.absolutePath))
  }

  private fun stageChanges(): Collection<String> {
    step("Staging changes..")
    val changes = gitStatus(context.devRepoRoot, includeUntracked = true).all().filter {
      val file = context.devRepoRoot.resolve(it)
      isImage(file) || file.extension == "java"
    }
    if (changes.isNotEmpty()) {
      stageFiles(changes, context.devRepoRoot)
    }
    return changes
  }

  private fun commitChanges() {
    step("Committing changes..")
    commit(context.devRepoRoot, "Synced from ${getOriginUrl(context.iconsRepo)}", committer.name, committer.email)
    val commit = commitInfo(context.devRepoRoot) ?: error("Unable to perform commit")
    println("Committed ${commit.hash} '${commit.subject}'")
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
// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync.dotnet

import org.jetbrains.intellij.build.images.generateIconsClasses
import org.jetbrains.intellij.build.images.isImage
import org.jetbrains.intellij.build.images.sync.*
import java.io.File
import java.nio.file.Path

@Suppress("unused")
internal object DotnetIconSync {
  private class SyncPath(val iconsPath: String, val devPath: String)

  private val syncPaths = listOf(
    SyncPath("rider", "Rider/rider/icons/resources/rider"),
    SyncPath("net", "Rider/rider/icons/resources/resharper")
  )

  private val context = Context()

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
      if (isUnderTeamCity()) commitChanges(tmpCommit)
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
    generateIconsClasses(context.devRepoDir.absolutePath) { module ->
      // module containing directories in `sync`-list
      module.name == "rider-icons"
    }
  }

  private fun commitChanges(tmpCommit: CommitInfo) {
    step("Committing changes..")
    val changes = context.iconsCommitsToSync.mapValues {
      it.value.filterNot { commit ->
        commit.hash == tmpCommit.hash
      }
    }
    val (user, email) = triggeredBy()
    val commit = context.devRepoRoot.commit(changes.commitMessage(), user, email) {
      it.fileName.toString().endsWith(".java")
    }
    println("Committed ${commit?.hash} ${commit?.subject}")
  }

  private fun File.commit(message: String, user: String, email: String, filter: (Path) -> Boolean = { false }) =
    gitStatus(this, includeUntracked = true).filter {
      val path = toPath().resolve(it)
      isImage(path) || filter(path)
    }.let {
      stageFiles(it, this)
      commit(this, message, user, email)
      commitInfo(this)
    }
}
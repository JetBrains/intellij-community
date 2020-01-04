// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync.dotnet

import org.jetbrains.intellij.build.images.generateIconsClasses
import org.jetbrains.intellij.build.images.isImage
import org.jetbrains.intellij.build.images.sync.*
import java.io.File
import java.nio.file.Path

fun main() {
  val sync = listOf(
    Sync("rider", "Rider/rider/icons/resources/rider"),
    Sync("net", "Rider/rider/icons/resources/resharper")
  )
  val context = Context()
  echo("Transforming icons from Dotnet to Idea format..")
  sync.forEach {
    val path = context.iconsRepo.resolve(it.iconsPath).toPath()
    DotnetIconsTransformation.transformToIdeaFormat(path)
  }
  val (user, email) = if (isUnderTeamCity()) {
    triggeredBy()
  }
  else {
    context.iconsCommitsToSync.values.flatten().first().committer
  }
  val tmpCommit = context.iconsRepo.commit(
    "temporary commit, shouldn't be pushed",
    user, email
  ) ?: error("Unable to make a commit")
  try {
    sync.forEach {
      context.devRepoDir = context.devRepoRoot.resolve(it.devPath)
      context.iconsRepoDir = context.iconsRepo.resolve(it.iconsPath)
      echo("Syncing icons for ${it.devPath}..")
      checkIcons(context)
      echo("Generating classes for ${it.devPath}..")
      generateIconsClasses(context.devRepoDir.absolutePath) { module ->
        module.name == "rider-icons"
      }
    }
    context.devRepoRoot.commit(
      context.iconsCommitsToSync.mapValues {
        it.value.filterNot { it.hash == tmpCommit.hash }
      }.commitMessage(), user, email
    ) {
      it.fileName.toString().endsWith(".java")
    }
  }
  finally {
    resetToPreviousCommit(context.iconsRepo)
  }
}

private class Sync(val iconsPath: String, val devPath: String)

private fun echo(msg: String) = println("\n** $msg")
private fun File.commit(message: String, user: String, email: String, filter: (Path) -> Boolean = { false }) =
  gitStatus(this, includeUntracked = true).filter {
    val path = toPath().resolve(it)
    isImage(path) || filter(path)
  }.let {
    stageFiles(it, this)
    commit(this, message, user, email)
    commitInfo(this)
  }
// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

internal fun syncIconsRepo(context: Context) {
  if (context.doSyncIconsRepo) {
    log("Syncing ${context.iconsRepoName}:")
    syncIconsRepo(context, context.byDev)
  }
}

internal fun syncDevRepo(context: Context) {
  if (context.doSyncDevRepo) {
    log("Syncing ${context.devRepoName}:")
    syncAdded(context.devRepoDir, context.iconRepoDir, context.byDesigners.added)
    syncModified(context.devRepoDir.toFile(), context.iconRepoDir.toFile(), context.byDesigners.modified)
    if (context.doSyncRemovedIconsInDev) {
      syncRemoved(context.devRepoDir.toFile(), context.byDesigners.removed)
    }
  }
}

internal fun syncIconsRepo(context: Context, byDev: Changes) {
  syncAdded(context.iconRepoDir, context.devRepoDir, byDev.added)
  syncModified(context.iconRepoDir.toFile(), context.devRepoDir.toFile(), byDev.modified)
  syncRemoved(context.iconRepoDir.toFile(), byDev.removed)
}

private fun syncAdded(targetRoot: Path, sourceRoot: Path, added: MutableCollection<String>) {
  stageChanges(added) { change, skip, stage ->
    val source = sourceRoot.resolve(change)
    if (!Files.exists(source)) {
      log("Sync added: unable to find $change in source repo")
      return@stageChanges
    }
    val target = targetRoot.resolve(change)
    when {
      !Files.exists(target) -> {
        source.toFile().copyTo(target.toFile(), overwrite = true)
        val repo = findRepo(target)
        stage(repo.toFile(), repo.relativize(target).toString())
      }
      same(source.toFile(), target.toFile()) -> {
        log("Skipping $change")
        skip()
      }
      else -> source.toFile().copyTo(target.toFile(), overwrite = true)
    }
  }
}

private fun same(f1: File, f2: File) = f1.readBytes().contentEquals(f2.readBytes())

private fun syncModified(targetRoot: File, sourceRoot: File, modified: MutableCollection<String>) {
  stageChanges(modified) { change, skip, stage ->
    val source = sourceRoot.resolve(change)
    if (!source.exists()) {
      log("Sync modified: unable to find $change in source repo")
      return@stageChanges
    }
    val target = targetRoot.resolve(change)
    if (target.exists() && same(source, target)) {
      log("$change is not modified, skipping")
      skip()
      return@stageChanges
    }
    if (!target.exists()) log("$change should be modified but not exist, creating")
    source.copyTo(target, overwrite = true)
    val repo = findRepo(target.toPath())
    stage(repo.toFile(), target.toRelativeString(repo.toFile()))
  }
}

private fun syncRemoved(targetRoot: File, removed: MutableCollection<String>) {
  stageChanges(removed) { change, skip, stage ->
    val target = targetRoot.resolve(change)
    if (!target.exists()) {
      log("$change is already removed, skipping")
      skip()
      return@stageChanges
    }
    if (target.exists()) {
      if (target.delete()) {
        val repo = findRepo(target.toPath())
        stage(repo.toFile(), target.toRelativeString(repo.toFile()))
      }
      else log("Failed to delete ${target.absolutePath}")
    }
    cleanDir(target.parentFile)
  }
}

private fun cleanDir(dir: File?) {
  if (dir?.list()?.isEmpty() == true && dir.delete()) {
    cleanDir(dir.parentFile)
  }
}

private fun stageChanges(changes: MutableCollection<String>,
                         action: (String, () -> Unit, (File, String) -> Unit) -> Unit) {
  callSafely {
    val toStage = mutableMapOf<File, MutableList<String>>()
    val iterator = changes.iterator()
    while (iterator.hasNext()) {
      action(iterator.next(), iterator::remove) { repo, change ->
        if (!toStage.containsKey(repo)) toStage[repo] = mutableListOf()
        toStage.getValue(repo) += change
      }
    }
    toStage.forEach { (repo, change) ->
      stageFiles(change, repo.toPath())
    }
  }
}
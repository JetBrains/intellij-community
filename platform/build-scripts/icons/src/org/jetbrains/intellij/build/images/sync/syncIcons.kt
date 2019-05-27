// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File

internal fun syncIconsRepo(context: Context) {
  if (context.doSyncIconsRepo) {
    log("Syncing ${context.iconsRepoName}:")
    syncIconsRepo(context, context.byDev)
  }
}

internal fun syncDevRepo(context: Context) {
  if (context.doSyncDevRepo) {
    log("Syncing ${context.devRepoName}:")
    syncAdded(context.devRepoDir, context.iconsRepoDir, context.byDesigners.added)
    syncModified(context.devRepoDir, context.iconsRepoDir, context.byDesigners.modified)
    if (context.doSyncRemovedIconsInDev) {
      syncRemoved(context.devRepoDir, context.byDesigners.removed)
    }
  }
}

internal fun syncIconsRepo(context: Context, byDev: Changes) {
  syncAdded(context.iconsRepoDir, context.devRepoDir, byDev.added)
  syncModified(context.iconsRepoDir, context.devRepoDir, byDev.modified)
  syncRemoved(context.iconsRepoDir, byDev.removed)
}

private fun syncAdded(targetRoot: File, sourceRoot: File, added: MutableCollection<String>) {
  stageChanges(added) { change, skip, stage ->
    val source = sourceRoot.resolve(change)
    if (!source.exists()) {
      log("Sync added: unable to find $change in source repo")
      return@stageChanges
    }
    val target = targetRoot.resolve(change)
    when {
      !target.exists() -> {
        source.copyTo(target, overwrite = true)
        val repo = findRepo(target)
        stage(repo, target.relativeTo(repo).path)
      }
      same(source, target) -> {
        log("Skipping $change")
        skip()
      }
      else -> source.copyTo(target, overwrite = true)
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
    val repo = findRepo(target)
    stage(repo, target.toRelativeString(repo))
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
        val repo = findRepo(target)
        stage(repo, target.toRelativeString(repo))
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
      stageFiles(change, repo)
    }
  }
}
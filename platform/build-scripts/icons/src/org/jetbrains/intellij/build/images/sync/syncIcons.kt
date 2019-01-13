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
    syncAdded(context.byDesigners.added, context.icons, context.devRepoDir) { changesToReposMap(it) }
    syncModified(context.devRepoRoot, context.byDesigners.modified, context.devIcons, context.icons)
    if (context.doSyncRemovedIconsInDev) syncRemoved(context.byDesigners.removed, context.devIcons)
  }
}

internal fun syncIconsRepo(context: Context, byDev: Changes) {
  syncAdded(byDev.added, context.devIcons, context.iconsRepoDir) { context.iconsRepo }
  syncModified(context.iconsRepoDir, byDev.modified, context.icons, context.devIcons)
  syncRemoved(byDev.removed, context.icons)
}

private fun syncAdded(added: MutableCollection<String>,
                      sourceRepoMap: Map<String, GitObject>,
                      targetDir: File, targetRepo: (File) -> File) {
  stageFiles(added) { file, skip, stage ->
    val source = sourceRepoMap[file]?.file
    if (source == null) {
      log("Sync added: unable to find $file in source map repo")
      return@stageFiles
    }
    val target = targetDir.resolve(file)
    if (target.exists()) {
      if (source.readBytes().contentEquals(target.readBytes())) {
        log("Skipping $file")
        skip()
      }
      else {
        source.copyTo(target, overwrite = true)
      }
    }
    else {
      source.copyTo(target, overwrite = true)
      val repo = targetRepo(target)
      stage(repo, target.relativeTo(repo).path)
    }
  }
}

private fun syncModified(targetRoot: File,
                         modified: MutableCollection<String>,
                         targetRepoMap: Map<String, GitObject>,
                         sourceRepoMap: Map<String, GitObject>) {
  stageFiles(modified) { file, skip, stage ->
    val source = sourceRepoMap[file] ?: error("Sync modified: unable to find $file in source map repo")
    if (targetRepoMap.containsKey(file)) {
      val target = targetRepoMap[file] ?: error("Sync modified: unable to find $file in target map repo")
      if (target.hash == source.hash) {
        log("$file is not modified, skipping")
        skip()
      }
      else {
        source.file.copyTo(target.file, overwrite = true)
        stage(target.repo, target.path)
      }
    }
    else {
      log("$file should be modified but not exist, creating")
      val targetFile = targetRoot.resolve(file)
      val repo = changesToReposMap(targetFile)
      source.file.copyTo(targetFile)
      stage(repo, targetFile.toRelativeString(repo))
    }
  }
}

private fun syncRemoved(removed: MutableCollection<String>,
                        targetRepoMap: Map<String, GitObject>) {
  stageFiles(removed) { file, skip, stage ->
    if (!targetRepoMap.containsKey(file)) {
      log("$file is already removed, skipping")
      skip()
    }
    else {
      val gitObject = targetRepoMap[file] ?: error("Sync removed: unable to find $file in target map repo")
      val target = gitObject.file
      if (target.exists()) {
        if (target.delete()) {
          stage(gitObject.repo, gitObject.path)
        }
        else {
          log("Failed to delete ${target.absolutePath}")
        }
      }
      cleanDir(target.parentFile)
    }
  }
}

private fun cleanDir(dir: File?) {
  if (dir?.list()?.isEmpty() == true && dir.delete()) {
    cleanDir(dir.parentFile)
  }
}

private fun stageFiles(files: MutableCollection<String>,
                       action: (String, () -> Unit, (File, String) -> Unit) -> Unit) {
  callSafely {
    val toStage = mutableMapOf<File, MutableList<String>>()
    val iterator = files.iterator()
    while (iterator.hasNext()) {
      action(iterator.next(), { iterator.remove() }) { repo, path ->
        if (!toStage.containsKey(repo)) toStage[repo] = mutableListOf()
        toStage[repo]!! += path
      }
    }
    toStage.forEach { repo, file ->
      stageFiles(file, repo)
    }
  }
}
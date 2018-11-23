// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File

internal fun syncIcons(context: Context) {
  val devIcons = context.devIcons
  val icons = context.icons
  if (context.doSyncIconsRepo || context.doSyncIconsAndCreateReview) {
    log("Syncing ${context.iconsRepoName}:")
    syncAdded(context.addedByDev, devIcons, context.iconsRepoDir) { context.iconsRepo }
    syncModified(context.modifiedByDev, icons, devIcons)
    syncRemoved(context.removedByDev, icons)
  }
  if (context.doSyncDevRepo || context.doSyncDevIconsAndCreateReview) {
    log("Syncing ${context.devRepoName}:")
    syncAdded(context.addedByDesigners, icons, context.devRepoDir) { changesToReposMap(it) }
    syncModified(context.modifiedByDesigners, devIcons, icons)
    if (context.doSyncRemovedIconsInDev) syncRemoved(context.removedByDesigners, devIcons)
  }
}

internal fun syncAdded(added: MutableCollection<String>,
                       sourceRepoMap: Map<String, GitObject>,
                       targetDir: File, targetRepo: (File) -> File) {
  callSafely {
    stageFiles { add ->
      val iterator = added.iterator()
      while (iterator.hasNext()) {
        val file = iterator.next()
        val source = sourceRepoMap[file]!!.file
        val target = targetDir.resolve(file)
        if (target.exists()) {
          log("$file already exists in target repo!")
          if(source.readBytes().contentEquals(target.readBytes())) {
            log("Skipping $file")
            iterator.remove()
          } else {
            source.copyTo(target, overwrite = true)
          }
        }
        else {
          source.copyTo(target, overwrite = true)
          val repo = targetRepo(target)
          add(repo, target.relativeTo(repo).path)
        }
      }
    }
  }
}

internal fun syncModified(modified: MutableCollection<String>,
                          targetRepoMap: Map<String, GitObject>,
                          sourceRepoMap: Map<String, GitObject>) {
  callSafely {
    stageFiles { add ->
      val iterator = modified.iterator()
      while (iterator.hasNext()) {
        val it = iterator.next()
        val target = targetRepoMap[it]!!
        val source = sourceRepoMap[it]!!
        if (target.hash == source.hash) {
          log("$it is not modified, skipping")
          iterator.remove()
        }
        else {
          source.file.copyTo(target.file, overwrite = true)
          add(target.repo, target.path)
        }
      }
    }
  }
}

internal fun syncRemoved(removed: MutableCollection<String>,
                         targetRepoMap: Map<String, GitObject>) {
  callSafely {
    stageFiles { add ->
      val iterator = removed.iterator()
      while (iterator.hasNext()) {
        val file = iterator.next()
        if (!targetRepoMap.containsKey(file)) {
          log("$file is already removed, skipping")
          iterator.remove()
        }
        else {
          val gitObject = targetRepoMap[file]!!
          val target = gitObject.file
          if (!target.delete()) {
            log("Failed to delete ${target.absolutePath}")
          }
          else {
            add(gitObject.repo, gitObject.path)
            if (target.parentFile.list().isEmpty()) target.parentFile.delete()
          }
        }
      }
    }
  }
}

private fun stageFiles(action: ((File, String) -> Unit) -> Unit) {
  val map = mutableMapOf<File, MutableList<String>>()
  action { repo, path ->
    if (!map.containsKey(repo)) map[repo] = mutableListOf()
    map[repo]!! += path
  }
  map.forEach { repo, file ->
    stageFiles(file, repo)
  }
}
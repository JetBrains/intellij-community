// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File

internal fun doSync(added: Collection<String>,
                    modified: Collection<String>,
                    icons: Map<String, GitObject>,
                    devIcons: Map<String, GitObject>,
                    iconsDir: String) {
  try {
    doSyncAdded(added, devIcons, iconsDir)
    doSyncModified(modified, icons, devIcons)
  }
  catch (e: Exception) {
    e.printStackTrace()
    log(e.message ?: e.javaClass.canonicalName)
  }
}

private fun doSyncAdded(added: Collection<String>,
                        devIcons: Map<String, GitObject>,
                        iconsDir: String) {
  val iconsRepo = findGitRepoRoot(iconsDir)
  val iconsRoot = File(iconsRepo, iconsDir.removePrefix(iconsRepo.path))
  val unversioned = mutableListOf<String>()
  added.forEach {
    val target = File(iconsRoot, it)
    if (target.exists()) log("$it already exists in icons repo!")
    val source = devIcons[it]!!.getFile()
    source.copyTo(target, overwrite = true)
    unversioned += target.relativeTo(iconsRepo).path
  }
  addChangesToGit(unversioned, iconsRepo)
}

private fun doSyncModified(modified: Collection<String>,
                           icons: Map<String, GitObject>,
                           devIcons: Map<String, GitObject>) {
  modified.forEach {
    val dest = icons[it]!!.getFile()
    val source = devIcons[it]!!.getFile()
    source.copyTo(dest, overwrite = true)
  }
}
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import java.io.File

internal fun syncAdded(added: Collection<String>,
                       devIcons: Map<String, GitObject>,
                       iconsRepo: File, iconsDir: File) {
  val unversioned = mutableListOf<String>()
  added.forEach {
    val target = File(iconsDir, it)
    if (target.exists()) log("$it already exists in icons repo!")
    val source = devIcons[it]!!.getFile()
    source.copyTo(target, overwrite = true)
    unversioned += target.relativeTo(iconsRepo).path.let {
      if (it.contains(" ")) "\"$it\"" else it
    }
  }
  addChangesToGit(unversioned, iconsRepo)
}

internal fun syncModified(modified: Collection<String>,
                          icons: Map<String, GitObject>,
                          devIcons: Map<String, GitObject>) {
  modified.forEach {
    val target = icons[it]!!.getFile()
    val source = devIcons[it]!!.getFile()
    source.copyTo(target, overwrite = true)
  }
}

internal fun syncRemoved(removed: Collection<String>,
                         icons: Map<String, GitObject>) {
  removed.map { icons[it]!!.getFile() }.forEach {
    if (!it.delete()) log("Failed to delete ${it.absolutePath}")
  }
}
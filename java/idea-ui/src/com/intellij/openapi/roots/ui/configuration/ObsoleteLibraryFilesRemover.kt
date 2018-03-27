// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

/**
 * @author nik
 */
class ObsoleteLibraryFilesRemover(private val project: Project) {
  private val oldRoots = LinkedHashSet<VirtualFile>()

  fun registerObsoleteLibraryRoots(roots: Collection<VirtualFile>) {
    oldRoots += roots
  }

  fun deleteFiles() {
    val index = ProjectFileIndex.getInstance(project)
    //do not suggest to delete library files located outside project roots: they may be used in other projects or aren't stored in VCS
    val toDelete = oldRoots.filter { it.isValid && !index.isInLibrary(it) && index.isInContent(VfsUtil.getLocalFile(it)) }
    oldRoots.clear()

    if (toDelete.isNotEmpty()) {
      val many = toDelete.size > 1
      if (Messages.showYesNoDialog(project, "The following ${if (many) "files aren't" else "file isn't"} used anymore:\n" +
                                            "${toDelete.joinToString("\n") { it.presentableUrl }}\n" +
                                            "Do you want to delete ${if (many) "them" else "it"}?\n" +
                                            "You might not be able to fully undo this operation!",
                                   "Delete Unused Files", null) == Messages.YES) {
        runWriteAction {
          toDelete.forEach {
            VfsUtil.getLocalFile(it).delete(this)
          }
        }
      }
    }

  }
}
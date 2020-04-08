// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.ide.JavaUiBundle
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

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
      if (Messages.showYesNoDialog(project,
                                   JavaUiBundle.message("dialog.message.obsolete.library.files.remover.delete.files", toDelete.size,
                                                         toDelete.joinToString("\n") { it.presentableUrl }),
                                   JavaUiBundle.message("dialog.title.obsolete.library.files.remover.delete.files"), null)
        == Messages.YES) {
        runWriteAction {
          toDelete.forEach {
            VfsUtil.getLocalFile(it).delete(this)
          }
        }
      }
    }

  }
}
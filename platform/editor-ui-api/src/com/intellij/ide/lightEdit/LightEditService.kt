// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Experimental
interface LightEditService {
  companion object {
    val windowName: String
      get() = "LightEdit"

    @RequiresBlockingContext
    @JvmStatic
    fun getInstance(): LightEditService = service()
  }

  /**
   * Creates an empty document with the specified `preferredSavePath` and opens an editor tab.
   * 
   * @param preferredSavePath The preferred path to save the document by default. The path must contain at least a file name. If the path
   * is valid, it will be used to save the document without a file save dialog. If `preferredSavePath` is
   * `null`, the new document will have a default name "untitled_...".
   * @return Editor info for the newly created document.
   */
  fun createNewDocument(preferredSavePath: Path?): LightEditorInfo?

  fun saveToAnotherFile(file: VirtualFile)

  fun showEditorWindow()

  val project: Project?

  fun openFile(file: VirtualFile): Project

  fun openFile(path: Path, suggestSwitchToProject: Boolean): Project?

  var isAutosaveMode: Boolean

  fun closeEditorWindow(): Boolean

  val editorManager: LightEditorManager

  fun getSelectedFile(): VirtualFile?

  fun getSelectedFileEditor(): FileEditor?

  fun updateFileStatus(files: Collection<VirtualFile>)

  /**
   * Prompt a user to save all new documents that haven't been written to files yet.
   */
  fun saveNewDocuments()

  fun isTabNavigationAvailable(navigationAction: AnAction): Boolean

  fun navigateToTab(navigationAction: AnAction)

  /**
   * @return True if Project mode is preferred without a confirmation.
   */
  val isPreferProjectMode: Boolean

  fun isLightEditEnabled(): Boolean

  fun isLightEditProject(project: Project): Boolean

  fun isForceOpenInLightEditMode(): Boolean
}

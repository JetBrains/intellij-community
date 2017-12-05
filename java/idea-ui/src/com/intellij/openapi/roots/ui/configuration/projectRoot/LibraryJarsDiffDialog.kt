/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration.projectRoot

import com.intellij.diff.DiffManager
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.DiffRequestPanel
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.ide.diff.DirDiffSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import java.awt.event.ActionEvent
import javax.swing.Action

/**
 * @author nik
 */
class LibraryJarsDiffDialog(libraryFile: VirtualFile,
                            downloadedFile: VirtualFile,
                            private val mavenCoordinates: JpsMavenRepositoryLibraryDescriptor,
                            private val libraryName: String,
                            project: Project) : DialogWrapper(project) {
  companion object {
    val CHANGE_COORDINATES_CODE = 2;
  }

  private val panel: DiffRequestPanel

  init {
    title = "Replace Library"
    setOKButtonText("Replace")
    val request: ContentDiffRequest = DiffRequestFactory.getInstance().createFromFiles(project, libraryFile, downloadedFile)
    panel = DiffManager.getInstance().createRequestPanel(project, disposable, window)
    panel.putContextHints(DirDiffSettings.KEY, DirDiffSettings().apply {
      enableChoosers = false
      enableOperations = false
    })
    panel.setRequest(request)
    init()
  }

  override fun createNorthPanel() = JBLabel(XmlStringUtil.wrapInHtml("${mavenCoordinates.mavenId} JARs differ from '$libraryName' library JARs."))

  override fun createCenterPanel() = panel.component

  override fun getPreferredFocusedComponent() = panel.preferredFocusedComponent

  override fun createActions(): Array<Action> {
    return arrayOf(okAction, ChangeCoordinatesAction(), cancelAction)
  }

  private inner class ChangeCoordinatesAction : DialogWrapperAction("Change Coordinates...") {
    override fun doAction(e: ActionEvent?) {
      close(CHANGE_COORDINATES_CODE)
    }
  }
}
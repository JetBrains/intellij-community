// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import javax.swing.JComponent

/**
 * @author nik
 */
class LibraryJarsDiffDialog(libraryFile: VirtualFile,
                            downloadedFile: VirtualFile,
                            private val mavenCoordinates: JpsMavenRepositoryLibraryDescriptor,
                            private val libraryName: String,
                            project: Project) : DialogWrapper(project) {
  companion object {
    const val CHANGE_COORDINATES_CODE: Int = 2
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

  override fun createNorthPanel(): JBLabel = JBLabel(XmlStringUtil.wrapInHtml("${mavenCoordinates.mavenId} JARs differ from '$libraryName' library JARs."))

  override fun createCenterPanel(): JComponent = panel.component

  override fun getPreferredFocusedComponent(): JComponent? = panel.preferredFocusedComponent

  override fun createActions(): Array<Action> {
    return arrayOf(okAction, ChangeCoordinatesAction(), cancelAction)
  }

  private inner class ChangeCoordinatesAction : DialogWrapperAction("Change Coordinates...") {
    override fun doAction(e: ActionEvent?) {
      close(CHANGE_COORDINATES_CODE)
    }
  }
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl

import com.intellij.diff.DiffEditorTitleCustomizer
import com.intellij.diff.impl.ui.DiffFilePathLabelWrapper
import com.intellij.diff.impl.ui.FilePathDiffTitleCustomizer
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vcs.FilePath
import com.intellij.ui.components.JBLabel
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

class DiffEditorTitleDetails(val pathLabel: DetailsLabelProvider?, val revisionLabel: DetailsLabelProvider?) {
  private constructor(pathLabel: PathLabelProvider?, @NlsContexts.Label title: String?) : this(pathLabel, title?.let { RevisionLabelProvider(title, false) })

  fun getCustomizer(): DiffEditorTitleCustomizer = when {
    pathLabel != null -> FilePathDiffTitleCustomizer(pathLabel, revisionLabel)
    revisionLabel != null -> DiffEditorTitleCustomizer { revisionLabel.createComponent() }
    else -> DiffEditorTitleCustomizer.EMPTY
  }

  companion object {
    @JvmField
    val EMPTY: DiffEditorTitleDetails = DiffEditorTitleDetails(pathLabel = null, revisionLabel = null)

    @JvmStatic
    fun createFromPath(displayedPath: @NlsSafe String?): DiffEditorTitleDetails =
      DiffEditorTitleDetails(pathLabel = displayedPath?.let { PathLabelProvider(it) }, revisionLabel = null)

    @JvmStatic
    fun createFromTitle(title: @NlsContexts.Label String): DiffEditorTitleDetails =
      DiffEditorTitleDetails(pathLabel = null, title = title)

    @JvmStatic
    fun create(project: Project?, file: FilePath?, @NlsContexts.Label title: String?): DiffEditorTitleDetails =
      DiffEditorTitleDetails(file?.let { getFilePathLabel(project, it) }, title)

    @ApiStatus.Internal
    fun getFilePathLabel(project: Project?, file: FilePath): PathLabelProvider = PathLabelProvider(
      displayedPath = getRelativeOrFullPath(project, file),
      fullPath = FileUtil.getLocationRelativeToUserHome(FileUtil.toSystemDependentName(file.path))
    )

    private fun getRelativeOrFullPath(project: Project?, file: FilePath): @NlsSafe String {
      val fileNioPath = file.path.toNioPathOrNull() ?: return file.path

      val guessedProjectDir = project?.guessProjectDir()
      if (guessedProjectDir == null) return FileUtil.getLocationRelativeToUserHome(file.presentableUrl)

      val projectDirPath = guessedProjectDir.toNioPath()
      return if (fileNioPath.startsWith(projectDirPath)) projectDirPath.relativize(fileNioPath).toString()
      else FileUtil.getLocationRelativeToUserHome(file.presentableUrl)
    }
  }

  interface DetailsLabelProvider {
    fun createComponent(): JComponent
  }

  @ApiStatus.Internal
  class PathLabelProvider(
    private val displayedPath: @NlsContexts.Label String,
    private val fullPath: @NlsContexts.Label String = displayedPath,
  ) : DetailsLabelProvider {
    override fun createComponent(): JComponent = DiffFilePathLabelWrapper(displayedPath, fullPath)
  }

  @ApiStatus.Internal
  class RevisionLabelProvider(
    private val text: @NlsContexts.Label String,
    private val copiable: Boolean,
  ) : DetailsLabelProvider {
    override fun createComponent(): JComponent = JBLabel(XmlStringUtil.escapeString(text)).setCopyable(copiable)
  }
}

fun List<DiffEditorTitleDetails>.getCustomizers(): List<DiffEditorTitleCustomizer> = map { it.getCustomizer() }
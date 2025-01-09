// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.File
import javax.swing.JComponent
import kotlin.io.path.div

@ApiStatus.Experimental
open class FileSaverRunnable<T : JComponent>(
  val project: Project?,
  private val fileSaverDescriptor: FileSaverDescriptor,
  val textComponent: T,
  val accessor: TextComponentAccessor<in T>,
) : Runnable, ActionListener {

  override fun run() {
    chooseFile(fileSaverDescriptor)
  }

  protected fun chooseFile(descriptor: FileChooserDescriptor) {
    val saverDescriptor = FileSaverDescriptor(descriptor)
    val initialFile = getInitialFile()
    val virtualFileWrapper = FileChooserFactory.getInstance().createSaveFileDialog(saverDescriptor, textComponent).save(initialFile.first, initialFile.second)
    virtualFileWrapper?.file?.let { onFileChosen(it) }
  }

  protected fun getInitialFile(): Pair<VirtualFile?, String?> {
    val directoryName = accessor.getText(textComponent).trim()
    if (directoryName.isBlank()) return Pair(null, null)

    val projectPath = project?.basePath?.toNioPathOrNull()

    var path = NioFiles.toPath(directoryName)
    if (path == null) return Pair(null, null)

    if (!path.isAbsolute && projectPath != null)
      path = projectPath / path

    path = path.toAbsolutePath()

    val fileName = path!!.fileName.toString()
    while (path != null) {
      val result = LocalFileSystem.getInstance().findFileByNioFile(path)
      if (result != null) return Pair(result, fileName)
      path = path.parent
    }
    return Pair(null, null)
  }

  protected fun chosenFileToResultingText(chosenFile: VirtualFile): @NlsSafe String {
    return chosenFile.presentableUrl
  }

  protected val componentText: String
    get() = accessor.getText(textComponent).trim { it <= ' ' }

  open fun onFileChosen(chosenFile: File) {
    accessor.setText(textComponent, chosenFile.path.replace('/', File.separatorChar))
  }

  init {
    if (fileSaverDescriptor.isChooseMultiple) {
      logger.error("multiple selection not supported")
    }
  }

  override fun actionPerformed(e: ActionEvent?) {
    run()
  }
}

private val logger = logger<FileSaverRunnable<*>>()
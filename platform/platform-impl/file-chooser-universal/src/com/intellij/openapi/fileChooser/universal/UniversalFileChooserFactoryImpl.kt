// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDialog
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.fileChooser.NioFileChooserDialog
import com.intellij.openapi.fileChooser.PathChooserDialog
import com.intellij.openapi.fileChooser.impl.UniversalFileChooserFactory
import com.intellij.openapi.project.Project
import java.awt.Component
import java.nio.file.Path

internal class UniversalFileChooserFactoryImpl : UniversalFileChooserFactory {
  override fun canUseIn(project: Project?): Boolean = UniversalFileChooser.canUseIn(project)

  override fun createFileChooser(project: Project?, parent: Component?, descriptor: FileChooserDescriptor): FileChooserDialog {
    return UniversalFileChooser.create(project, parent, descriptor)
  }

  override fun createPathChooser(project: Project?, parent: Component?, descriptor: FileChooserDescriptor): PathChooserDialog {
    return UniversalFileChooser.create(project, parent, descriptor)
  }

  override fun createNioFileChooser(project: Project?, parent: Component?, descriptor: FileChooserDescriptor): NioFileChooserDialog {
    return UniversalFileChooser.create(project, parent, descriptor)
  }

  override fun createFileChooser(
    project: Project,
    parent: Component?,
    descriptor: FileChooserDescriptor,
    contributors: Collection<UniversalFileChooserContributor>,
    preselectPath: (suspend () -> Path?)?,
  ): FileChooserDialog {
    return UniversalFileChooser.Dialog(
      project = project,
      parent = parent,
      descriptor = descriptor,
      contributors = contributors,
      preselectPath = preselectPath,
    )
  }

  override fun createFileSaver(project: Project?, parent: Component?, descriptor: FileSaverDescriptor): FileSaverDialog {
    return UniversalFileSaver.create(project, parent, descriptor)
  }
}

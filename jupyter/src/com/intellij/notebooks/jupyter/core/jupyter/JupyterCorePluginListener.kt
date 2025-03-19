// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.jupyter.core.jupyter

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * When .ipynb files was opened before Jupyter plugin installed, we need to reopen them to run all required procedures in
 * [NotebookFileEditorService#onNotebookFileEditorCreated]
 */
class JupyterCorePluginListener : DynamicPluginListener {
  override fun beforePluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
    if (pluginDescriptor.pluginId.idString != JUPYTER_PLUGIN_ID)
      return

    ProjectManager.getInstance().openProjects.forEach { project ->
      val fileEditorManager = FileEditorManager.getInstance(project)
      fileEditorManager.openFiles.forEach { file ->
        file.extension == JupyterFileType.defaultExtension || return@forEach
        fileEditorManager.closeFile(file)
        filesToRestore.add(project to file)
      }
    }
  }

  companion object {
    val filesToRestore: MutableList<Pair<Project, VirtualFile>> = mutableListOf<Pair<Project, VirtualFile>>()

    const val JUPYTER_PLUGIN_ID: String = "intellij.jupyter"
  }
}

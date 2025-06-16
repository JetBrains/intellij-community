// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral")

package com.intellij.ide.actions

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import java.io.IOException
import javax.swing.JComponent

private class MoveFileToAnotherModuleAction : DumbAwareAction() {
  private val sourceRootTypes: List<JpsModuleSourceRootType<*>> = listOf(
    JavaSourceRootType.SOURCE,
    JavaSourceRootType.TEST_SOURCE,
    JavaResourceRootType.RESOURCE,
    JavaResourceRootType.TEST_RESOURCE
  )

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    e.presentation.isEnabled = project != null && file != null && file.isFile
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val module = ModuleUtilCore.findModuleForFile(file, project)
    if (module == null) {
      bail("Didn't find module for file ${file.name}")
      return
    }

    val sourceLocation = findSourceLocation(module, file)
    if (sourceLocation == null) {
      bail("Didn't detect source root for file ${file.name}")
      return
    }

    val chooserDialog = ModuleChooserDialog(project, file.name)
    if (!chooserDialog.showAndGet()) {
      return
    }
    val targetModule = chooserDialog.selectedModule ?: return

    val targetLocation = findTargetLocation(targetModule, sourceLocation)
    if (targetLocation == null) {
      bail("Couldn't detect target source root in module ${targetModule.name}")
      return
    }

    try {
      moveFile(project, file, targetLocation)
    }
    catch (e: IOException) {
      bail(e.message ?: "Unexpected error while moving the file")
    }
  }

  private fun findSourceLocation(sourceModule: Module, file: VirtualFile): SourceLocation? {
    val rootManager = sourceModule.rootManager
    var sourceRootType: JpsModuleSourceRootType<*>? = null
    var generatedSourceRoot = false
    var pathFromSourceRoot: List<String>? = null
    var pathFromContentRoot: List<String>? = null
    rootManager.contentEntries.forEach outer@{ contentEntry ->
      sourceRootTypes.forEach { rootType ->
        contentEntry.getSourceFolders(rootType).forEach { sourceFolder ->
          val root = sourceFolder.file
          if (root != null) {
            val path = collectPath(file, root)
            if (path != null) {
              sourceRootType = rootType
              pathFromSourceRoot = path
              generatedSourceRoot = isGenerated(sourceFolder)
              return@outer
            }
          }
        }
      }
    }
    rootManager.contentRoots.forEach { root ->
      val path = collectPath(file, root)
      if (path != null) {
        pathFromContentRoot = path
        return@forEach
      }
    }
    return if (pathFromSourceRoot == null && pathFromContentRoot == null) {
      null
    } else {
      SourceLocation(sourceRootType, generatedSourceRoot, pathFromSourceRoot, pathFromContentRoot)
    }
  }

  private fun collectPath(file: VirtualFile, root: VirtualFile): List<String>? {
    val pathToRoot = mutableListOf<String>()
    var f = file
    while (true) {
      val parent = f.parent
      if (parent == null) {
        return null
      }
      if (parent == root) {
        return pathToRoot.reversed()
      }
      pathToRoot.add(parent.name)
      f = parent
    }
  }

  private fun isGenerated(sourceFolder: SourceFolder) = when (val props = sourceFolder.jpsElement.properties) {
    is JavaSourceRootProperties -> props.isForGeneratedSources
    is JavaResourceRootProperties -> props.isForGeneratedSources
    else -> false
  }

  private fun findTargetLocation(targetModule: Module, sourceLocation: SourceLocation): TargetLocation? {
    val rootManager = targetModule.rootManager
    if (sourceLocation.sourceRootType != null && sourceLocation.pathFromSourceRoot != null) {
      rootManager.contentEntries.forEach { contentEntry ->
        contentEntry.getSourceFolders(sourceLocation.sourceRootType).forEach { sourceFolder ->
          val root = sourceFolder.file
          if (root != null && sourceLocation.generatedSourceRoot == isGenerated(sourceFolder)) {
            return TargetLocation(root, sourceLocation.pathFromSourceRoot)
          }
        }
      }
    }
    if (sourceLocation.pathFromContentRoot != null) {
      rootManager.contentRoots.firstOrNull()?.let {
        return TargetLocation(it, sourceLocation.pathFromContentRoot)
      }
    }
    return null
  }

  private fun moveFile(project: Project, file: VirtualFile, targetLocation: TargetLocation) {
    writeCommandAction(project).withName("Move File").run<Exception> {
      val requestor = this@MoveFileToAnotherModuleAction
      var target = targetLocation.root
      targetLocation.pathFromRoot.forEach {
        val child = target.findChild(it)
        target = child ?: target.createChildDirectory(requestor, it)
        if (target.isFile) {
          throw IOException("Existing file in target module prevents moving: ${target.path}")
        }
      }
      file.move(requestor, target)
    }
  }

  private fun bail(message: String) {
    Messages.showErrorDialog(message, "Error")
  }

  private data class SourceLocation(
    val sourceRootType: JpsModuleSourceRootType<*>?,
    val generatedSourceRoot: Boolean,
    val pathFromSourceRoot: List<String>?,
    val pathFromContentRoot: List<String>?
  )

  private data class TargetLocation(
    val root: VirtualFile,
    val pathFromRoot: List<String>
  )
}

private class ModuleChooserDialog(private val project: Project, fileName: String) : DialogWrapper(project, false) {
  companion object {
    private const val LAST_CHOSEN_MODULE_KEY = "MoveFileToAnotherModuleAction.lastChosen"
  }
  val modules = ModuleManager.getInstance(project).modules.sortedBy { it.name }
  var selectedModule = modules.find { it.name == PropertiesComponent.getInstance(project).getValue(LAST_CHOSEN_MODULE_KEY) }

  init {
    title = "Move $fileName to"
    init()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row("Module:") {
        comboBox(modules, textListCellRenderer { it?.name ?: "" }).applyToComponent {
          isSwingPopup = false // enable speed search
        }.focused().bindItem(::selectedModule)
      }
    }
  }

  override fun doOKAction() {
    super.doOKAction()
    PropertiesComponent.getInstance(project).setValue(LAST_CHOSEN_MODULE_KEY, selectedModule?.name)
  }
}
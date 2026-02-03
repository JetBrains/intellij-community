// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.performancePlugin

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.move.moveClassesOrPackages.MoveDirectoryWithClassesProcessor
import com.intellij.refactoring.rename.DirectoryAsPackageRenameHandler
import com.jetbrains.performancePlugin.PerformanceTestSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Rename the directory as a Java package.
 * Syntax: %renameDirectoryAsPackage relativePath newName whereToRename[DIRECTORY|MODULE|PROJECT]
 * Example: %renameDirectoryAsPackage community/platform/core-api/src/com/intellij/psi newPsi PROJECT
 */
class RenameDirectoryAsPackageCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {

  private enum class WhereToRename() { DIRECTORY, MODULE, PROJECT }

  companion object {
    const val PREFIX: String = "${CMD_PREFIX}renameDirectoryAsPackage"
    const val SPAN_NAME: String = "renameDirectoryAsPackage"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val commandArgs = extractCommandArgument(PREFIX)

    val args = commandArgs.split(" ")
    val directoryPath = args.getOrNull(0) ?: throw IllegalArgumentException("Directory path is missing")
    val newName = args.getOrNull(1) ?: throw IllegalArgumentException("New name is missing")
    val whereToRename = args.getOrNull(2)?.uppercase()?.let { mode -> WhereToRename.entries.find { it.name == mode } } ?: WhereToRename.DIRECTORY
    if (args.size > 3) throw IllegalArgumentException("Too many arguments provided")

    val directory = readAction {
      val projectDir = project.guessProjectDir() ?: throw IllegalArgumentException("Project directory not found")
      val directoryVirtualFile = projectDir.findFileByRelativePath(directoryPath)
                                 ?: throw IllegalArgumentException("Directory not found: $directoryPath")

      PsiManager.getInstance(project).findDirectory(directoryVirtualFile)
    } ?: throw IllegalArgumentException("Directory not found: $directoryPath")

    val span = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME).startSpan()

    val processor = readAction {
      MyDirectoryAsPackageRenameHandler().createProcessor(newName, whereToRename, project, directory, false, false)
    }
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        processor.run()
        span.end()
      }
    }
  }

  private class MyDirectoryAsPackageRenameHandler: DirectoryAsPackageRenameHandler() {

    fun createProcessor(
      newName: String,
      whereToRename: WhereToRename,
      project: Project,
      directory: PsiDirectory,
      searchInComments: Boolean,
      searchInNonJavaFiles: Boolean
    ): BaseRefactoringProcessor {

      val module = ModuleUtilCore.findModuleForFile(directory.getVirtualFile(), project)!!
      val aPackage = this.getPackage(directory) ?: throw IllegalArgumentException("Couldn't get a package for the directory: ${directory.getVirtualFile().getPath()}")
      val newQName = StringUtil.getQualifiedName(StringUtil.getPackageName(getQualifiedName(aPackage)), newName)

      val dirsToRename = when (whereToRename) {
        WhereToRename.DIRECTORY -> arrayOf(directory)
        WhereToRename.MODULE -> aPackage.getDirectories(GlobalSearchScope.moduleScope(module))
        WhereToRename.PROJECT -> aPackage.getDirectories(GlobalSearchScope.projectScope(project))
      }

      return object : MoveDirectoryWithClassesProcessor(project, dirsToRename, null, searchInComments, searchInNonJavaFiles, false, null) {
        override fun getTargetDirectory(dir: PsiDirectory): TargetDirectoryWrapper {
          return TargetDirectoryWrapper(dir.getParentDirectory(), StringUtil.getShortName(newQName))
        }

        override fun getTargetName(): String { return newQName }

        override fun getCommandName(): String {
          return RefactoringBundle.message(if (dirsToRename.size == 1) "rename.directory.command.name" else "rename.directories.command.name")
        }
      }
    }
  }
}

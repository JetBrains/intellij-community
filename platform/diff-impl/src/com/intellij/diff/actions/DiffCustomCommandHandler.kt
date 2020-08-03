// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.actions

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.terminal.TerminalExecutorAction
import com.intellij.terminal.TerminalShellCommandHandler
import com.intellij.util.execution.ParametersListUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class DiffCustomCommandHandler : TerminalShellCommandHandler {
  private val LOG = Logger.getInstance(DiffCustomCommandHandler::class.java)
  override fun execute(project: Project, workingDirectory: String?, localSession: Boolean, command: String, executorAction: TerminalExecutorAction): Boolean {
    val parameters = parse(workingDirectory, localSession, command)

    if (parameters == null) {
      LOG.warn("Command $command should be matched and properly parsed")
      return false
    }

    val file1 = LocalFileSystem.getInstance().findFileByIoFile(parameters.first.toFile())
    val file2 = LocalFileSystem.getInstance().findFileByIoFile(parameters.second.toFile())

    if (file1 == null || file2 == null) {
      LOG.warn("Cannot find virtual file for one of the paths: $file1, $file2")
      return false
    }

    DiffManager.getInstance().showDiff(project,
                                       BaseShowDiffAction.createMutableChainFromFiles(project, file1, file2),
                                       DiffDialogHints.DEFAULT)

    return true
  }

  override fun matches(project: Project, workingDirectory: String?, localSession: Boolean, command: String): Boolean {
    return parse(workingDirectory, localSession, command) != null
  }

  private fun parse(workingDirectory: String?, localSession: Boolean, command: String): Pair<Path, Path>? {
    if (!command.startsWith("diff ") || !localSession) {
      return null
    }

    if (workingDirectory == null) {
      return null
    }

    val commands = ParametersListUtil.parse(command)
    if (commands.size < 3) {
      return null
    }

    val path1 = Paths.get(workingDirectory, commands[1])
    val path2 = Paths.get(workingDirectory, commands[2])
    val file1Exists = Files.exists(path1)
    val file2Exists = Files.exists(path2)
    if (!file1Exists || !file2Exists) {
      return null
    }

    return Pair(path1, path2)
  }
}
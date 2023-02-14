// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.project.DumbService

open class JBProtocolNavigateCommand : JBProtocolCommand(NAVIGATE_COMMAND) {
  /**
   * The handler parses the following "navigate" command parameters:
   *
   * \\?project=(?<project>[\\w]+)
   *   (&fqn[\\d]*=(?<fqn>[\\w.\\-#]+))*
   *   (&path[\\d]*=(?<path>[\\w-_/\\\\.]+)
   *     (:(?<lineNumber>[\\d]+))?
   *     (:(?<columnNumber>[\\d]+))?)*
   *   (&selection[\\d]*=
   *     (?<line1>[\\d]+):(?<column1>[\\d]+)
   *    -(?<line2>[\\d]+):(?<column2>[\\d]+))*
   */
  override suspend fun execute(target: String?, parameters: Map<String, String>, fragment: String?): String? {
    if (target != REFERENCE_TARGET) {
      return IdeBundle.message("jb.protocol.navigate.target", target)
    }

    val openProjectResult = openProject(parameters)
    val project = when(openProjectResult) {
      is ProtocolOpenProjectResult.Success -> openProjectResult.project
      is ProtocolOpenProjectResult.Error -> return openProjectResult.message
    }

    DumbService.getInstance(project).runWhenSmart {
      NavigatorWithinProject(project, parameters, ::locationToOffset).navigate(listOf(
        NavigatorWithinProject.NavigationKeyPrefix.FQN,
        NavigatorWithinProject.NavigationKeyPrefix.PATH
      ))
    }

    return null
  }

  private fun locationToOffset(locationInFile: LocationInFile, editor: Editor): Int {
    return editor.logicalPositionToOffset(LogicalPosition(locationInFile.line, locationInFile.column))
  }
}

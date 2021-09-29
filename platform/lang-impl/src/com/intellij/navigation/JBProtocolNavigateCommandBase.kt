// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation

import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ComparatorUtil

@Deprecated(message = "implement JBProtocolCommand instead. For navigation you can use functions from JBprotocolNavigation")
abstract class JBProtocolNavigateCommandBase(command: String): JBProtocolCommand(command) {

  fun openProject(parameters: Map<String, String>, action: (Project) -> Unit) = openProjectWithAction(parameters, action)

  fun findAndNavigateToReference(project: Project, parameters: Map<String, String>) {
    fun locationToOffset(locationInFile: LocationInFile, editor: Editor): Int {
      val offsetOfLine = editor.logicalPositionToOffset(LogicalPosition(ComparatorUtil.max(locationInFile.line - 1, 0), 0))
      val offsetInLine = locationInFile.column - 1
      return ComparatorUtil.max(offsetOfLine + offsetInLine, 0)
    }

    parameters.filterKeys { it.startsWith(PATH_KEY) }.values.forEach {
      navigateByPath(project, parameters, it, ::locationToOffset)
    }
  }
}

fun parseNavigatePath(pathText: String) = parseNavigationPath(pathText)
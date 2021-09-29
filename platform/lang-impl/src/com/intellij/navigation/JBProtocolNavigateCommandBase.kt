// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation

import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.project.Project



abstract class JBProtocolNavigateCommandBase(command: String): JBProtocolCommand(command) {
  /**
   * The handler parses the following command parameters:
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

  fun openProject(parameters: Map<String, String>, action: (Project) -> Unit) = openProjectWithAction(parameters, action)

  fun findAndNavigateToReference(project: Project, parameters: Map<String, String>) {
    fun locationToOffset(locationInFile: LocationInFile, editor: Editor) =
      editor.logicalPositionToOffset(LogicalPosition(locationInFile.line, locationInFile.column))

    val keyPrefixToNavigator = mapOf(
      (FQN_KEY to ::navigateByFqn),
      (PATH_KEY to ::navigateByPath)
    )
    keyPrefixToNavigator.forEach { (keyPrefix, navigator) ->
      parameters.filterKeys { it.startsWith(keyPrefix) }.values.forEach { navigator.invoke(project, parameters, it, ::locationToOffset) }
    }
  }
}


fun parseNavigatePath(pathText: String) = parseNavigationPath(pathText)
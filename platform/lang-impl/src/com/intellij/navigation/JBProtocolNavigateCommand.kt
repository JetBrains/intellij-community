// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation

import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.project.DumbService

private val LOG = logger<JBProtocolNavigateCommand>()

open class JBProtocolNavigateCommand: JBProtocolCommand(NAVIGATE_COMMAND) {
  companion object {
    const val NAVIGATE_COMMAND = "navigate"
  }
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

  override fun perform(target: String, parameters: Map<String, String>) {
    if (target != REFERENCE_TARGET) {
      LOG.warn("JB navigate action supports only reference target, got $target")
      return
    }
    openProjectWithAction(parameters) {
      DumbService.getInstance(it).runWhenSmart {
        NavigatorWithinProject(it, parameters, ::locationToOffset)
          .navigate(listOf(
            NavigatorWithinProject.NavigationKeyPrefix.FQN,
            NavigatorWithinProject.NavigationKeyPrefix.PATH
          ))
      }
    }
  }
}

private fun locationToOffset(locationInFile: LocationInFile, editor: Editor) =
  editor.logicalPositionToOffset(LogicalPosition(locationInFile.line, locationInFile.column))

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.project.DumbService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

open class JBProtocolNavigateCommand: JBProtocolCommand(NAVIGATE_COMMAND) {
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
  override fun perform(target: String?, parameters: MutableMap<String, String>, fragment: String?): Future<String?> =
    if (target != REFERENCE_TARGET) {
      CompletableFuture.completedFuture(IdeBundle.message("jb.protocol.navigate.target", target))
    }
    else {
      openProject(parameters).handle { project, t ->
        when {
          t != null -> "${t.javaClass.name}: ${t.message}"
          project == null -> IdeBundle.message("jb.protocol.navigate.no.project")
          else -> {
            DumbService.getInstance(project).runWhenSmart {
              NavigatorWithinProject(project, parameters, ::locationToOffset)
                .navigate(listOf(
                  NavigatorWithinProject.NavigationKeyPrefix.FQN,
                  NavigatorWithinProject.NavigationKeyPrefix.PATH
                ))
            }
            null
          }
        }
      }
    }

  private fun locationToOffset(locationInFile: LocationInFile, editor: Editor) =
    editor.logicalPositionToOffset(LogicalPosition(locationInFile.line, locationInFile.column))
}

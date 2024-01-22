// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation

import com.intellij.ide.IdeBundle
import com.intellij.navigation.target.DiffWithinProject
import com.intellij.navigation.target.ReferenceWithinProject
import com.intellij.openapi.application.JBProtocolCommand

const val NAVIGATE_COMMAND: String = "navigate"

const val REFERENCE_TARGET: String = "reference"
const val DIFF_TARGET: String = "diff"

private enum class NavigateCommandTargets(val urlPart: String) {
  REFERENCE(REFERENCE_TARGET),
  DIFF(DIFF_TARGET)
}

/**
 * The command that process the following URLs.
 *
 * Open file in an IDE could be performed by `{IDE_NAME}/navigate/reference` command. This command
 * is accepted the following URL search parameters:
 * - either `project` or `origin` for locating, which project should be open:
 *         - `project` is name of an IDE project.
 *         - `origin` is VCS url that accosted with the project.
 * - either `path` or `fqn` for select, which file should be opened:
 *     - `path` is the path from the project root to the file. May contains number of line and
 *       column that should be navigated. Example: `path=/src/index.ts:1:2`.
 *     - `fqn` is Fully Qualified Name whta works only for a Java/Kotlin project.
 * - optional `revision` with name of a branch or commit version of file.
 * - optional `selection1`, `selection2`, `selection3`, etc. with hyphen-separated number of line
 *   and column of start and end of selection. Example: `selection1=1:0-10:12`.
 *
 * Open diff of two files by `{IDE_NAME}/navigate/diff` command. This command is accepted the
 * following URL search parameters:
 * - either `project` or `origin` for locating, which project should be open:
 *         - `project` is name of an IDE project.
 *         - `origin` is VCS url that accosted with the project.
 * - `path_left` which file should be opened in the left diff window.
 * - `path_right` which file should be opened in the right diff window.
 * - `revision_left` with name of a branch or commit version of the file that should be opened in
 *   the left diff window.
 * - `revision_right` with name of a branch or commit version of the file that should be opened in
 *   the right diff window.
 */
open class JBProtocolNavigateCommand : JBProtocolCommand(NAVIGATE_COMMAND) {
  override suspend fun execute(
      target: String?,
      parameters: Map<String, String>,
      fragment: String?
  ): String? {
    val allNavigateCommandTargets = NavigateCommandTargets.entries
    val navigateCommandTarget =
        allNavigateCommandTargets.find { it.urlPart == target }
            ?: return IdeBundle.message(
                "jb.protocol.navigate.target",
                allNavigateCommandTargets.joinToString(", ") { it.urlPart },
                target)

    return withProject(parameters) { project ->
      when (navigateCommandTarget) {
        NavigateCommandTargets.REFERENCE -> ReferenceWithinProject(project, parameters).navigate()
        NavigateCommandTargets.DIFF -> DiffWithinProject(project, parameters).navigate()
      }
    }
  }
}

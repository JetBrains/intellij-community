package org.jetbrains.jps.listeners

import org.jetbrains.jps.Project

/**
 * @author nik
 */
class DefaultBuildInfoPrinter implements BuildInfoPrinter {
  def printProgressMessage(Project project, String message) {
    project.info(message)
  }

  def printCompilationErrors(Project project, String compilerName, String messages) {
    project.error(messages)
  }

}

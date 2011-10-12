package org.jetbrains.jps.listeners

import org.jetbrains.jps.ProjectBuilder

/**
 * @author nik
 */
class DefaultBuildInfoPrinter implements BuildInfoPrinter {
  def printProgressMessage(ProjectBuilder projectBuilder, String message) {
    projectBuilder.info(message)
  }

  def printCompilationErrors(ProjectBuilder projectBuilder, String compilerName, String messages) {
    projectBuilder.error(messages)
  }

}

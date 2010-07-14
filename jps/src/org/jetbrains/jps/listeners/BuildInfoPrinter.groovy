package org.jetbrains.jps.listeners

import org.jetbrains.jps.Project

/**
 * @author nik
 */
public interface BuildInfoPrinter {

  def printProgressMessage(Project project, String message)

  def printCompilationErrors(Project project, String compilerName, String messages)

}
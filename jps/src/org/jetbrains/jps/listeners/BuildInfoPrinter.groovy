package org.jetbrains.jps.listeners

import org.jetbrains.jps.ProjectBuilder

/**
 * @author nik
 */
public interface BuildInfoPrinter {

  def printProgressMessage(ProjectBuilder projectBuilder, String message)

  def printCompilationErrors(ProjectBuilder projectBuilder, String compilerName, String messages)

}
package org.jetbrains.jps.teamcity

import org.jetbrains.jps.listeners.BuildInfoPrinter
import org.jetbrains.jps.Project

/**
 * @author nik
 */
class TeamcityBuildInfoPrinter implements BuildInfoPrinter {
  def printProgressMessage(Project project, String message) {
    println "##teamcity[progressMessage '$message']"
  }

  def printCompilationErrors(Project project, String compilerName, String messages) {
    println "##teamcity[compilationStarted compiler='$compilerName']"
    messages.split("\n").each {
      println "##teamcity[message text='$it' status='ERROR']"
    }
    println "##teamcity[compilationFinished compiler='$compilerName']"
  }

}

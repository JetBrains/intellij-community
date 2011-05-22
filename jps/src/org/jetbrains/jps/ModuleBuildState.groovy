package org.jetbrains.jps

import org.jetbrains.ether.ProjectWrapper
import org.jetbrains.ether.dependencyView.Callbacks.Backend

/**
 * @author max
 */
class ModuleBuildState {
  boolean tests
  Backend callback
  ProjectWrapper projectWrapper
  List<File> sourceFiles
  List<String> sourceRoots
  List<String> excludes
  List<String> classpath
  List<String> tempRootsToDelete = []
  List<String> moduleDependenciesSourceRoots
  String targetFolder
  boolean incremental = false

  def print() {
    println "Sources: $sourceRoots"
    println "Excludes: $excludes"
    println "Classpath: $classpath"
  }
}

package org.jetbrains.jps

/**
 * @author max
 */
class ModuleBuildState {
  List<String> sourceRoots
  List<String> excludes
  List<String> classpath
  List<String> tempRootsToDelete
  List<String> moduleDependenciesSourceRoots
  String targetFolder

  def print() {
    println "Sources: $sourceRoots"
    println "Excludes: $excludes"
    println "Classpath: $classpath"
  }
}

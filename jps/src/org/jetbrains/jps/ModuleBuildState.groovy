package org.jetbrains.jps

/**
 * @author max
 */
class ModuleBuildState {
  List<String> sourceRoots
  List<String> classpath
  List<String> tempRootsToDelete
  String targetFolder
}

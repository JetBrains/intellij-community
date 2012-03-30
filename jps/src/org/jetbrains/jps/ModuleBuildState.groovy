package org.jetbrains.jps

import com.intellij.ant.InstrumentationUtil.FormInstrumenter
import com.intellij.compiler.instrumentation.InstrumentationClassFinder
import org.jetbrains.ether.ProjectWrapper
import org.jetbrains.ether.dependencyView.Callbacks.Backend

/**
 * @author max
 */
class ModuleBuildState {
  boolean iterated
  boolean tests
  Backend callback
  FormInstrumenter formInstrumenter
  InstrumentationClassFinder loader
  ProjectWrapper projectWrapper
  List<File> sourceFiles
  List<String> sourceRoots
  List<String> excludes
  List<String> classpath
  List<String> tempRootsToDelete = []
  List<String> sourceRootsFromModuleWithDependencies
  String targetFolder
  boolean incremental = false

  def print() {
    println "Sources: $sourceRoots"
    println "Excludes: $excludes"
    println "Classpath: $classpath"
  }
}

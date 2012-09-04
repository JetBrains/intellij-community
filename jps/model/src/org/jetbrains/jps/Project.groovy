package org.jetbrains.jps
/**
 * @author max
 */
class Project {
  final CompilerConfiguration compilerConfiguration = new CompilerConfiguration()
  final IgnoredFilePatterns ignoredFilePatterns = new IgnoredFilePatterns()

  def Project() {
  }

  def String toString() {
    return "Project"
  }
}

package org.jetbrains.jps
import org.jetbrains.jps.artifacts.Artifact
/**
 * @author max
 */
class Project {
  String projectName
  int locationHash

  final Map<String, Artifact> artifacts = [:]
  final Map<String, RunConfiguration> runConfigurations = [:]
  final CompilerConfiguration compilerConfiguration = new CompilerConfiguration()
  final UiDesignerConfiguration uiDesignerConfiguration = new UiDesignerConfiguration()
  final IgnoredFilePatterns ignoredFilePatterns = new IgnoredFilePatterns()

  String projectCharset; // contains project charset, if not specified default charset will be used (used by compilers)
  Map<String, String> filePathToCharset = [:];

  def Project() {
  }

  def String toString() {
    return "Project '$projectName'"
  }
}

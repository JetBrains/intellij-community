package org.jetbrains.jps.artifacts

/**
 * @author nik
 */
class Artifact {
  String name
  LayoutElement rootElement
  String outputPath
  Map<String, ArtifactProperties> properties;

  def String toString() {
    return "artifact '$name'"
  }
}

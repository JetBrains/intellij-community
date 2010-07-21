package org.jetbrains.jps.artifacts

/**
 * @author nik
 */
class Artifact {
  String name
  LayoutElement rootElement
  String outputPath

  def String toString() {
    return "artifact '$name'"
  }
}

package org.jetbrains.jps.artifacts

/**
 * @author nik
 */
class Artifact {
  String name;
  LayoutElement rootElement;

  def String toString() {
    return "artifact '$name'";
  }
}

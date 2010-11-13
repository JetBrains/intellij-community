package org.jetbrains.jps.artifacts

/**
 * @author nik
 */
class Artifact {
  String name
  LayoutElement rootElement
  String outputPath
  Map<String, Options> options;

  def String toString() {
    return "artifact '$name'"
  }
}

abstract class Options {
  abstract Map<String, String> getAll();
}
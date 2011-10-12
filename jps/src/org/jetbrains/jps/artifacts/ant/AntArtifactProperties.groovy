package org.jetbrains.jps.artifacts.ant

import org.jetbrains.jps.artifacts.ArtifactProperties

/**
 * @author nik
 */
class AntArtifactProperties implements ArtifactProperties {
  boolean enabled
  String filePath
  String target
  List<List<String>> buildProperties
}

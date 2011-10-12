package org.jetbrains.jps.artifacts

import org.jetbrains.jps.MacroExpander

/**
 * @author nik
 */
abstract class ArtifactPropertiesProviderService<P extends ArtifactProperties> {
  final String id

  ArtifactPropertiesProviderService(String id) {
    this.id = id
  }

  abstract P loadProperties(Node node, MacroExpander macroExpander)
}

package org.jetbrains.jps.artifacts.ant

import org.jetbrains.jps.MacroExpander
import org.jetbrains.jps.artifacts.ArtifactPropertiesProviderService
import org.jetbrains.jps.idea.IdeaProjectLoadingUtil

/**
 * @author nik
 */
class AntArtifactPropertiesProvider extends ArtifactPropertiesProviderService<AntArtifactProperties> {
  AntArtifactPropertiesProvider(String id) {
    super(id)
  }

  @Override
  AntArtifactProperties loadProperties(Node node, MacroExpander macroExpander) {
    def filePathNode = node."file"[0]
    def targetNode = node."target"[0]

    if (filePathNode == null) throw new IllegalArgumentException("Path to build.xml is not specified");

    String file = macroExpander.expandMacros(IdeaProjectLoadingUtil.pathFromUrl(filePathNode.text()))
    String target = targetNode.text()
    boolean enabled = node."@enabled" == "true"
    List<List<String>> properties = []
    node."build-properties"[0]?."build-property"?.each {Node property ->
      properties << [property."@name", property."@value"]
    }
    return new AntArtifactProperties(enabled: enabled, filePath: file, target: target, buildProperties: properties)
  }

}

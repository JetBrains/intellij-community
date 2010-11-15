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
    String file = macroExpander.expandMacros(IdeaProjectLoadingUtil.pathFromUrl(node."file"[0].text()))
    String target = node."target"[0].text()
    boolean enabled = node."@enabled" == "true"
    return new AntArtifactProperties(enabled: enabled, filePath: file, target: target)
  }

}

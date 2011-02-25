package org.jetbrains.jps.artifacts.ant

import org.jetbrains.jps.Project
import org.jetbrains.jps.artifacts.Artifact
import org.jetbrains.jps.artifacts.ArtifactBuildTask
import org.jetbrains.jps.artifacts.ArtifactProperties

class CallAntBuildTask implements ArtifactBuildTask {
  private final Project project
  private final String propertiesId

  CallAntBuildTask(Project project, String propertiesId) {
    this.project = project
    this.propertiesId = propertiesId
  }

  def perform(Artifact artifact, String outputFolder) {
    def ArtifactProperties properties = artifact.properties[propertiesId]
    if (!(properties instanceof AntArtifactProperties)) return null
    AntArtifactProperties antProperties = (AntArtifactProperties) properties
    if (!antProperties.enabled) return;

    String filePath = antProperties.filePath
    def attrs = [:];
    attrs['antfile'] = filePath;
    if (antProperties.target != null && antProperties.target.length() > 0) {
      attrs['target'] = antProperties.target
    };
    attrs['dir'] = new File(filePath).parent;

    def buildProperties = [["artifact.output.path", artifact.outputPath]]
    buildProperties.addAll(antProperties.buildProperties)
    project.binding.ant.ant(attrs) {
      buildProperties.each {List<String> nameValue ->
        property(name: nameValue[0], value: nameValue[1])
      }
    };
  }
}

package org.jetbrains.jps.artifacts

import org.jetbrains.jps.Project
import org.jetbrains.jps.dag.DagBuilder
import org.jetbrains.jps.dag.DagNode

/**
 * @author nik
 */
class ArtifactBuilder {
  private final Project project
  final Map<Artifact, String> artifactOutputs = [:]
  private List<Artifact> sortedArtifacts

  def ArtifactBuilder(Project project) {
    this.project = project;
  }

  def getSortedArtifacts() {
    if (sortedArtifacts == null) {
      def depsIterator = {Artifact artifact, Closure processor ->

      }
      def dagBuilder = new DagBuilder<Artifact>({new DagNode<Artifact>()}, depsIterator)
      def nodes = dagBuilder.build(project, project.artifacts.values())
      sortedArtifacts = nodes.collect {
        if (it.elements.size() > 1) {
          project.error("Circular inclusion of artifacts: ${it.elements}")
        }
        it.elements.iterator().next()
      }
    }
    return sortedArtifacts
  }

  def buildArtifacts() {
    getSortedArtifacts().each {
      buildArtifact it
    }
  }

  private def buildArtifact(Artifact artifact) {
    project.stage("Building '${artifact.name}' artifact")
    def output = artifactOutputs[artifact]
    if (output != null) return output
    artifactOutputs[artifact] = output = new File(getBaseArtifactsOutput(), suggestFileName(artifact.name)).absolutePath
    project.binding.layout.call([output, {
      artifact.rootElement.build(project)
    }])
    return output
  }

  private static String suggestFileName(String text) {
    return text.replaceAll(/(;|:|\s)/, "_");
  }

  private String getBaseArtifactsOutput() {
    return new File(project.targetFolder, "artifacts").absolutePath
  }
}


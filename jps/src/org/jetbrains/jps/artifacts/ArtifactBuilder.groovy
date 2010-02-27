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
        processIncludedArtifacts(artifact, processor)
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

  private def processIncludedArtifacts(Artifact artifact, Closure processor) {
    artifact.rootElement.process(project) {LayoutElement element ->
      if (element instanceof ArtifactLayoutElement) {
        def included = ((ArtifactLayoutElement) element).findArtifact(project)
        if (included != null) {
          processor(included)
        }
        return false
      }
      return true
    }
  }

  def buildArtifacts() {
    getSortedArtifacts().each {
      doBuildArtifact it
    }
  }

  def buildArtifact(Artifact artifact) {
    buildArtifactWithDependencies(artifact, [] as Set)
  }

  private def buildArtifactWithDependencies(Artifact artifact, Set<Artifact> parents) {
    if (artifact in parents) {
      project.error("Circular inclusion of artifacts: $parents")
    }
    Set<Artifact> included = new LinkedHashSet<Artifact>()
    processIncludedArtifacts(artifact) {
      included << it
    }

    Set<Artifact> newParents = new HashSet<Artifact>(parents)
    newParents << artifact
    included.each {
      buildArtifactWithDependencies(it, newParents)
    }
    doBuildArtifact(artifact)
  }

  private def doBuildArtifact(Artifact artifact) {
    def output = artifactOutputs[artifact]
    if (output != null) return output

    project.stage("Building '${artifact.name}' artifact")
    def outputDir = new File(getBaseArtifactsOutput(), suggestFileName(artifact.name))
    artifactOutputs[artifact] = output = outputDir.absolutePath
    project.binding.layout.call([output, {
      artifact.rootElement.build(project)
    }])
    return output
  }

  private static String suggestFileName(String text) {
    return text.replaceAll(/(;|:|\s)/, "_");
  }

  private String getBaseArtifactsOutput() {
    return new File(project.targetFolder, "artifacts")
  }
}


package org.jetbrains.jps.artifacts

import org.jetbrains.jps.Project
import org.jetbrains.jps.dag.DagBuilder
import org.jetbrains.jps.dag.DagNode

/**
 * @author nik
 */
class ArtifactBuilder {
  private final Project project
  final List<ArtifactBuildTask> preBuildTasks = []
  final List<ArtifactBuildTask> postBuildTasks = []
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
    output = getArtifactOutputFolder(artifact)
    if (output == null) {
      project.error("Output path for artifact '$artifact.name' is not specified")
    }
    artifactOutputs[artifact] = output
    preBuildTasks*.perform(artifact, output)
    project.binding.layout.call([output, {
      artifact.rootElement.build(project)
    }])
    postBuildTasks*.perform(artifact, output)
    return output
  }

  String getArtifactOutputFolder(Artifact artifact) {
    String targetFolder = project.targetFolder
    if (targetFolder == null) {
      return artifact.outputPath
    }
    return new File(new File(targetFolder, "artifacts"), suggestFileName(artifact.name)).absolutePath
  }

  def preBuildTask(String artifactName, Closure task) {
    registerBuildTask(artifactName, preBuildTasks, task)
  }

  def postBuildTask(String artifactName, Closure task) {
    registerBuildTask(artifactName, postBuildTasks, task)
  }

  private static def registerBuildTask(String artifactName, List<ArtifactBuildTask> tasks, Closure task) {
    tasks << ({Artifact artifact, String output ->
      if (artifact.name == artifactName) {
        task(artifact, output)
      }
    } as ArtifactBuildTask)
  }

  private static String suggestFileName(String text) {
    return text.replaceAll(/(;|:|\s)/, "_");
  }
}


package org.jetbrains.jps.artifacts

import org.jetbrains.jps.Module
import org.jetbrains.jps.Project
import org.jetbrains.jps.artifacts.ant.CallAntBuildTask
import org.jetbrains.jps.builders.BuildUtil
import org.jetbrains.jps.dag.DagBuilder
import org.jetbrains.jps.dag.DagNode
import org.jetbrains.jps.artifacts.ant.PreprocessingAntArtifactPropertiesProvider
import org.jetbrains.jps.artifacts.ant.PostprocessingAntArtifactPropertiesProvider
import org.jetbrains.jps.ProjectBuilder

/**
 * @author nik
 */
class ArtifactBuilder {
  private final ProjectBuilder projectBuilder
  private final Project project
  final List<ArtifactBuildTask> preBuildTasks = []
  final List<ArtifactBuildTask> postBuildTasks = []
  final Map<Artifact, String> artifactOutputs = [:]
  private List<Artifact> sortedArtifacts

  def ArtifactBuilder(ProjectBuilder projectBuilder) {
    this.projectBuilder = projectBuilder
    this.project = projectBuilder.project
    preBuildTasks.add(new CallAntBuildTask(projectBuilder, PreprocessingAntArtifactPropertiesProvider.ID))
    postBuildTasks.add(new CallAntBuildTask(projectBuilder, PostprocessingAntArtifactPropertiesProvider.ID))
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
          projectBuilder.error("Circular inclusion of artifacts: ${it.elements}")
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
    compileModulesAndBuildArtifacts(getSortedArtifacts())
  }

  def buildArtifact(Artifact artifact) {
    Set<Artifact> included = new LinkedHashSet<Artifact>()
    collectIncludedArtifacts(artifact, [] as Set, included)
    compileModulesAndBuildArtifacts(included as List)
  }

  private def collectIncludedArtifacts(Artifact artifact, Set<Artifact> parents, Set<Artifact> result) {
    if (artifact in parents) {
      projectBuilder.error("Circular inclusion of artifacts: $parents")
    }
    if (result.contains(artifact)) {
      return
    }

    Set<Artifact> included = new LinkedHashSet<Artifact>()
    processIncludedArtifacts(artifact) {
      included << it
    }

    Set<Artifact> newParents = new HashSet<Artifact>(parents)
    newParents << artifact
    included.each {
      collectIncludedArtifacts(it, newParents, result)
    }
    result << artifact
  }

  private def compileModulesAndBuildArtifacts(List<Artifact> artifacts) {
    Set<Module> modules = [] as Set
    artifacts*.rootElement*.process(project) {LayoutElement element ->
      if (element instanceof ModuleOutputElement) {
        Module module = project.modules[element.moduleName]
        if (module != null) {
          modules << module
        }
      }
      return true
    }
    modules.each { projectBuilder.makeModule(it) }

    artifacts.each {
      doBuildArtifact(it)
    }
  }

  private def doBuildArtifact(Artifact artifact) {
    def output = artifactOutputs[artifact]
    if (output != null) return output

    projectBuilder.stage("Building '${artifact.name}' artifact")
    output = getArtifactOutputFolder(artifact)
    if (output == null) {
      output = projectBuilder.getTempDirectoryPath(artifact.name)
      projectBuilder.info("Output path for artifact '$artifact.name' is not specified so it will be built to $output")
    }
    artifactOutputs[artifact] = output
    preBuildTasks*.perform(artifact, output)
    projectBuilder.binding.layout.call([output, {
      artifact.rootElement.build(projectBuilder)
    }])
    postBuildTasks*.perform(artifact, output)
    return output
  }

  String getArtifactOutputFolder(Artifact artifact) {
    String targetFolder = projectBuilder.targetFolder
    if (targetFolder == null) {
      return artifact.outputPath
    }
    return new File(new File(targetFolder, "artifacts"), BuildUtil.suggestFileName(artifact.name)).absolutePath
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

  def cleanOutput(Artifact artifact) {
    String outputPath = artifact.outputPath
    if (outputPath == null) return

    def ant = projectBuilder.binding.ant
    LayoutElement root = artifact.rootElement
    if (root instanceof ArchiveElement) {
      ant.delete(file: "$outputPath/${root.name}")
    }
    else {
      checkCanCleanDirectory(outputPath)
      BuildUtil.deleteDir(projectBuilder, outputPath)
    }
  }

  def checkCanCleanDirectory(String path) {
    project.modules.values().each {Module module ->
      (module.sourceRoots + module.testRoots).each {
        if (isAncestor(path, it)) {
          projectBuilder.error("Cannot clean directory $path: it contains source root $it")
        }
      }
    }
  }

  boolean isAncestor(String ancestorPath, String path) {
    File ancestor = getCanonicalFile(ancestorPath)
    File file = getCanonicalFile(path)
    while (file != null) {
      if (file == ancestor) {
        return true;
      }
      file = file.parentFile
    }
    return false;
  }

  def getCanonicalFile(String path) {
    File file = new File(path)
    try {
      return file.getCanonicalFile()
    } catch (IOException e) {
      return file.getAbsoluteFile()
    }
  }
}


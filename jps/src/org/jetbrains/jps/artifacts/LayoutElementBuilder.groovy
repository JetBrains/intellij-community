package org.jetbrains.jps.artifacts

import org.jetbrains.jps.ProjectBuilder

/**
 * @author nik
 */
class LayoutElementBuilder {

  def build(LayoutElement element, ProjectBuilder projectBuilder) {
    if (element instanceof ArchiveElement) {
      buildArchive(element, projectBuilder)
    }
    else if (element instanceof DirectoryElement) {
      buildDirectory(element, projectBuilder)
    }
    else if (element instanceof RootElement) {
      buildRoot(element, projectBuilder)
    }
    else if (element instanceof FileCopyElement) {
      buildFileCopy(element, projectBuilder)
    }
    else if (element instanceof DirectoryCopyElement) {
      buildDirectoryCopy(element, projectBuilder)
    }
    else if (element instanceof ExtractedDirectoryElement) {
      buildExtractedDir(element, projectBuilder)
    }
    else if (element instanceof ModuleOutputElement) {
      buildModuleOutput(element, projectBuilder)
    }
    else if (element instanceof ModuleTestOutputElement) {
      buildModuleTestsOutput(element, projectBuilder)
    }
    else {
      assert element instanceof ComplexLayoutElement : element
      buildComplexElement(element, projectBuilder)
    }
  }

  def buildDirectoryCopy(DirectoryCopyElement element, ProjectBuilder projectBuilder) {
    if (new File(element.dirPath).isDirectory()) {
      projectBuilder.binding.ant.fileset(dir:element.dirPath)
    }
  }

  def buildExtractedDir(ExtractedDirectoryElement element, ProjectBuilder projectBuilder) {
    if (new File(element.jarPath).isFile()) {
      projectBuilder.binding.ant.extractedDir(jarPath:element.jarPath, pathInJar: element.pathInJar)
    }
  }

  def buildModuleOutput(ModuleOutputElement element, ProjectBuilder projectBuilder) {
    projectBuilder.binding.module.call(element.moduleName)
  }

  def buildModuleTestsOutput(ModuleTestOutputElement element, ProjectBuilder projectBuilder) {
    projectBuilder.binding.moduleTests.call(element.moduleName)
  }

  def buildFileCopy(FileCopyElement element, ProjectBuilder projectBuilder) {
    if (!new File(element.filePath).isFile()) return

    if (element.outputFileName == null) {
      projectBuilder.binding.ant.fileset(file: element.filePath)
    }
    else {
      projectBuilder.binding.renamedFile.call([element.filePath, element.outputFileName].toArray())
    }
  }

  def buildArchive(ArchiveElement element, ProjectBuilder projectBuilder) {
    if (element.name.endsWith(".jar")) {
      projectBuilder.binding.ant.jar(name: element.name, filesetmanifest: "mergewithoutmain", duplicate: "preserve",
                              compress: projectBuilder.compressJars, {
            buildChildren(element, projectBuilder)
      })
    }
    else {
      projectBuilder.binding.ant.zip(name: element.name, duplicate: "preserve", {
        buildChildren(element, projectBuilder)
      })
    }
  }

  def buildDirectory(DirectoryElement element, ProjectBuilder projectBuilder) {
    projectBuilder.binding.dir.call([element.name, {
      buildChildren(element, projectBuilder)
    }].toArray())
  }

  def buildRoot(RootElement element, ProjectBuilder projectBuilder) {
    buildChildren(element, projectBuilder)
  }

  def buildChildren(CompositeLayoutElement element, ProjectBuilder projectBuilder) {
    element.children.each {
      build(it, projectBuilder)
    }
  }

  def buildArtifact(ArtifactLayoutElement element, ProjectBuilder projectBuilder) {
    def artifact = element.findArtifact(projectBuilder.project)
    if (artifact == null) {
      projectBuilder.error("unknown artifact: ${element.artifactName}")
    }
    def output = projectBuilder.artifactBuilder.artifactOutputs[artifact]
    if (output != null) {
      LayoutElement root = artifact.rootElement
      if (root instanceof ArchiveElement) {
        projectBuilder.binding.ant.fileset(file: "$output/$root.name")
      }
      else {
        projectBuilder.binding.ant.fileset(dir: output)
      }
    }
    else {
      projectBuilder.error("Required artifact ${element.artifactName} is not build")
    }
  }

  def buildComplexElement(ComplexLayoutElement element, ProjectBuilder projectBuilder) {
    element.getSubstitution(projectBuilder.project).each {
      build(it, projectBuilder)
    }
  }

}

package org.jetbrains.jps.artifacts

import org.jetbrains.jps.Project
import org.jetbrains.jps.ProjectBuilder

/**
 * @author nik
 */
abstract class LayoutElement {
  def build(ProjectBuilder projectBuilder) {}

  boolean process(Project project, Closure processor) {
    return processor(this)
  }
}

class FileCopyElement extends LayoutElement {
  String filePath
  String outputFileName

  def build(ProjectBuilder projectBuilder) {
    if (!new File(filePath).isFile()) return

    if (outputFileName == null) {
      projectBuilder.binding.ant.fileset(file: filePath)
    }
    else {
      projectBuilder.binding.renamedFile.call([filePath, outputFileName].toArray())
    }
  }
}

class DirectoryCopyElement extends LayoutElement {
  String dirPath

  def build(ProjectBuilder projectBuilder) {
    if (new File(dirPath).isDirectory()) {
      projectBuilder.binding.ant.fileset(dir:dirPath)
    }
  }
}

class ExtractedDirectoryElement extends LayoutElement {
  String jarPath
  String pathInJar

  def build(ProjectBuilder projectBuilder) {
    if (new File(jarPath).isFile()) {
      projectBuilder.binding.ant.extractedDir(jarPath:jarPath, pathInJar: pathInJar)
    }
  }
}

class ModuleOutputElement extends LayoutElement {
  String moduleName

  def build(ProjectBuilder projectBuilder) {
    projectBuilder.binding.module.call(moduleName)
  }
}

class ModuleTestOutputElement extends LayoutElement {
  String moduleName

  def build(ProjectBuilder projectBuilder) {
    projectBuilder.binding.moduleTests.call(moduleName)
  }
}

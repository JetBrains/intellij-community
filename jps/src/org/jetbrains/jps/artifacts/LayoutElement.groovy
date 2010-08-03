package org.jetbrains.jps.artifacts

import org.jetbrains.jps.Project
import org.jetbrains.jps.Library

/**
 * @author nik
 */
abstract class LayoutElement {
  def build(Project project) {}

  boolean process(Project project, Closure processor) {
    return processor(this)
  }
}

class FileCopyElement extends LayoutElement {
  String filePath
  String outputFileName

  def build(Project project) {
    if (!new File(filePath).isFile()) return

    if (outputFileName == null) {
      project.binding.ant.fileset(file: filePath)
    }
    else {
      project.binding.renamedFile.call([filePath, outputFileName].toArray())
    }
  }
}

class DirectoryCopyElement extends LayoutElement {
  String dirPath

  def build(Project project) {
    if (new File(dirPath).isDirectory()) {
      project.binding.ant.fileset(dir:dirPath)
    }
  }
}

class ExtractedDirectoryElement extends LayoutElement {
  String jarPath
  String pathInJar

  def build(Project project) {
    if (new File(jarPath).isFile()) {
      project.binding.ant.extractedDir(jarPath:jarPath, pathInJar: pathInJar)
    }
  }
}

class ModuleOutputElement extends LayoutElement {
  String moduleName

  def build(Project project) {
    project.binding.module.call(moduleName)
  }
}

package org.jetbrains.jps.artifacts

import org.jetbrains.jps.Project
import org.jetbrains.jps.Library

/**
 * @author nik
 */
abstract class LayoutElement {
  def build(Project project) {}
}

class FileCopyElement extends LayoutElement {
  String filePath
  String outputFileName

  def build(Project project) {
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
    project.binding.ant.fileset(dir:dirPath)
  }
}

class ModuleOutputElement extends LayoutElement {
  String moduleName

  def build(Project project) {
    project.binding.module.call(moduleName)
  }
}

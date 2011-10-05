package org.jetbrains.jps.artifacts

import org.jetbrains.jps.Project
import org.jetbrains.jps.ProjectBuilder

/**
 * @author nik
 */
abstract class CompositeLayoutElement extends LayoutElement {
  String name
  protected final List<LayoutElement> children = []

  def buildChildren(ProjectBuilder projectBuilder) {
    children*.build(projectBuilder)
  }

  List<LayoutElement> getChildren() {
    return children
  }

  LayoutElement leftShift(LayoutElement child) {
    children << child
    return child
  }

  boolean process(Project project, Closure processor) {
    if (processor(this)) {
      children*.process(project, processor)
    }
  }
}

class RootElement extends CompositeLayoutElement {
  def RootElement(List<LayoutElement> children) {
    this.children.addAll(children)
  }

  def build(ProjectBuilder projectBuilder) {
    buildChildren(projectBuilder)
  }
}

class DirectoryElement extends CompositeLayoutElement {
  def DirectoryElement(String name, List<LayoutElement> children) {
    this.name = name
    this.children.addAll(children)
  }

  def build(ProjectBuilder projectBuilder) {
    projectBuilder.binding.dir.call([name, {
      buildChildren(projectBuilder)
    }].toArray())
  }
}

class ArchiveElement extends CompositeLayoutElement {
  def ArchiveElement(String name, List<LayoutElement> children) {
    this.name = name
    this.children.addAll(children)
  }

  def build(ProjectBuilder projectBuilder) {
    if (name.endsWith(".jar")) {
      projectBuilder.binding.ant.jar(name: name, filesetmanifest: "mergewithoutmain", duplicate: "preserve",
                              compress: projectBuilder.compressJars, {
            buildChildren(projectBuilder)
      })
    }
    else {
      projectBuilder.binding.ant.zip(name: name, duplicate: "preserve", {
        buildChildren(projectBuilder)
      })
    }
  }
}

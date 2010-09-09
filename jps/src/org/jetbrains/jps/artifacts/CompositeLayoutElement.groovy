package org.jetbrains.jps.artifacts

import org.jetbrains.jps.Project

 /**
 * @author nik
 */
abstract class CompositeLayoutElement extends LayoutElement {
  String name
  protected final List<LayoutElement> children = []

  def buildChildren(Project project) {
    children*.build(project)
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

  def build(Project project) {
    buildChildren(project)
  }
}

class DirectoryElement extends CompositeLayoutElement {
  def DirectoryElement(String name, List<LayoutElement> children) {
    this.name = name
    this.children.addAll(children)
  }

  def build(Project project) {
    project.binding.dir.call([name, {
      buildChildren(project)
    }].toArray())
  }
}

class ArchiveElement extends CompositeLayoutElement {
  def ArchiveElement(String name, List<LayoutElement> children) {
    this.name = name
    this.children.addAll(children)
  }

  def build(Project project) {
    if (name.endsWith(".jar")) {
      project.binding.ant.jar(name: name, filesetmanifest: "mergewithoutmain", duplicate: "preserve",
                              compress: project.builder.compressJars, {
            buildChildren(project)
      })
    }
    else {
      project.binding.ant.zip(name: name, duplicate: "preserve", {
        buildChildren(project)
      })
    }
  }
}

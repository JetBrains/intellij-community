package org.jetbrains.jps.artifacts

import org.jetbrains.jps.Project
import org.jetbrains.jps.model.JpsModel
/**
 * @author nik
 */
abstract class CompositeLayoutElement extends LayoutElement {
  String name
  protected final List<LayoutElement> children = []

  List<LayoutElement> getChildren() {
    return children
  }

  LayoutElement leftShift(LayoutElement child) {
    children << child
    return child
  }

  boolean process(Project project, JpsModel model, Closure processor) {
    if (processor(this)) {
      children*.process(project, model, processor)
    }
  }
}

class RootElement extends CompositeLayoutElement {
  def RootElement(List<LayoutElement> children) {
    this.children.addAll(children)
  }
}

class DirectoryElement extends CompositeLayoutElement {
  def DirectoryElement(String name, List<LayoutElement> children) {
    this.name = name
    this.children.addAll(children)
  }
}

class ArchiveElement extends CompositeLayoutElement {
  def ArchiveElement(String name, List<LayoutElement> children) {
    this.name = name
    this.children.addAll(children)
  }
}

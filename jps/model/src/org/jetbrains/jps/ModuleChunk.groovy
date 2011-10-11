package org.jetbrains.jps

import org.jetbrains.jps.dag.DagNode

/**
 * @author max
 */
class ModuleChunk extends DagNode<Module> {

  ModuleChunk() {
  }

  ModuleChunk(Module module) {
    elements << module
  }

  ModuleChunk(Set<Module> modules) {
    elements = modules
  }

  def String getName() {
    if (elements.size() == 1) return elements.iterator().next().getName();

    StringBuilder b = new StringBuilder()

    b << "ModuleChunk("
    elements.eachWithIndex {Module it, int index ->
      if (index > 0) b << ","
      b << it.name
    }
    b << ")"

    b.toString()
  }

  Set<Module> getModules() {
    return elements
  }

  def String toString() {
    return getName();
  }

  def List<String> getSourceRoots() {
    map {it.sourceRoots}
  }

  def List<String> getTestRoots() {
    map {it.testRoots}
  }

  def List<ClasspathItem> getClasspath(ClasspathKind kind) {
    map {it.getClasspath(kind)}
  }

  def List<String> getExcludes() {
    map {it.excludes}
  }

  private <T> List<T> map(Closure c) {
    LinkedHashSet answer = new LinkedHashSet()
    elements.each {
      answer.addAll(c(it))
    }
    answer.asList()
  }

  def Project getProject() {
    return representativeModule().project
  }

  public Module representativeModule() {
    return elements.iterator().next()
  }

  def getSdk() {
    Module m = representativeModule()

    if (m.sdk == null)
      return m.project.projectSdk

    return m.sdk
  }

  def getAt(String key) {
    representativeModule().getAt(key)
  }
}

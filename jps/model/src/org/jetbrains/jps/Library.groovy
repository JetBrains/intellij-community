package org.jetbrains.jps

import com.intellij.openapi.util.io.FileUtil

/**
 * @author max
 */
class Library implements ClasspathItem {
  Project project;
  String name;

  List classpath = []
  List sourceRoots = []

  private Map<String, Object> props = [:]

  def Library(project, name) {
    this.project = project;
    this.name = name;
  }

  void addClasspath(String arg) {
    classpath << FileUtil.toCanonicalPath(arg)
  }

  void addClasspath(GString arg) {
    new Throwable().printStackTrace()
    addClasspath(arg.toString())
  }

  void src(Object[] arg) {
    arg.each { sourceRoots << FileUtil.toCanonicalPath(it.toString()) }
  }

  def String toString() {
    return "library ${name}. classpath: ${classpath}"
  }

  def List<String> getClasspathRoots(ClasspathKind kind) {
    classpath
  }
  
  def getAt(String key) {
    if (props[key] != null) return props[key]
    project[key]
  }

  def setAt(String key, Object value) {
    props[key] = value
  }
}

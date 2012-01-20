package org.jetbrains.jps

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.idea.Facet

/**
 * @author max
 */
class Module extends LazyInitializeableObject implements ClasspathItem {//}, Comparable {
  Project project;
  String name;
  Sdk sdk;

  private List<ModuleDependency> dependencies = []
  List<String> contentRoots = []
  List<String> sourceRoots = []
  List<String> testRoots = []
  List<String> excludes = []

  String basePath
  String outputPath
  String testOutputPath
  Map<String, Facet> facets = [:]
  Map<String, Object> props = [:]
  Map<String, String> sourceRootPrefixes = [:]
  Map<String, Library> libraries = [:]

  String languageLevel

  /*int compareTo(Object o) {
      if (o instanceof Module) {
          ((Module) o).name.compareTo(name);
      }

      return -1
  }*/

  def Module(project, name, initializer) {
    this.project = project;
    this.name = name;

    setInitializer({
      def meta = new InitializingExpando()

      meta.dependency = {Object item, DependencyScope scope, boolean exported ->
        dependencies << new ModuleDependency(project.resolve(item), scope, exported)
      }

      meta.classpath = {Object[] arg ->
        arg.each { dependencies << new ModuleDependency(project.resolve(it), PredefinedDependencyScopes.COMPILE, false) }
      }

      meta.testclasspath = {Object[] arg ->
        arg.each { dependencies << new ModuleDependency(project.resolve(it), PredefinedDependencyScopes.TEST, false) }
      }

      meta.moduleSource = {
        dependencies << new ModuleDependency(new ModuleSourceEntry(module: this), PredefinedDependencyScopes.COMPILE, true)
      }

      meta.content = {Object[] arg ->
        arg.each { contentRoots << FileUtil.toCanonicalPath(it) }
      }

      meta.src = {Object[] arg ->
        arg.each { sourceRoots << FileUtil.toCanonicalPath(it) }
      }

      meta.testSrc = {Object[] arg ->
        arg.each { testRoots << FileUtil.toCanonicalPath(it) }
      }

      meta.exclude = {Object[] arg ->
        arg.each { excludes << FileUtil.toCanonicalPath(it) }
      }

      initializer.delegate = meta
      initializer.setResolveStrategy Closure.DELEGATE_FIRST
      initializer.call()

      def wrongProperties = ["dependency", "classpath", "testclasspath", "src", "testSrc", "exclude"] as Set
      meta.getProperties().each {String key, Object value ->
        if (!wrongProperties.contains(key)) {
          props[key] = value
        }
      }
    })
  }

  def String toString() {
    return "module ${name}"
  }

  def getAt(String key) {
    if (props[key] != null) return props[key]
    project[key]
  }

  def putAt(String key, Object value) {
    props[key] = value
  }

  def List<String> getClasspathRoots(ClasspathKind kind) {
    return []
  }

  def List<ClasspathItem> getClasspath(ClasspathKind kind) {
    return getClasspath(kind, false)
  }

  def List<ClasspathItem> getClasspath(ClasspathKind kind, boolean exportedOnly) {
    forceInit()
    return dependencies.findAll({it.scope.isIncludedIn(kind) && (!exportedOnly || it.exported)})*.item;
  }

  private static class ModuleDependency {
    ClasspathItem item
    DependencyScope scope
    boolean exported

    def ClasspathItem getItem() {
      return item;
    }

    ModuleDependency(ClasspathItem item, DependencyScope scope, boolean exported) {
      this.item = item
      this.scope = scope
      this.exported = exported
    }
  }

  boolean equals(o) {
    if (this.is(o)) return true;
    if (getClass() != o.class) return false;

    Module module = (Module) o;

    if (name != module.name) return false;

    return true;
  }

  int hashCode() {
    return (name != null ? name.hashCode() : 0);
  }

}

class ModuleSourceEntry implements ClasspathItem {
  Module module

  List<String> getClasspathRoots(ClasspathKind kind) {
    return []
  }
}


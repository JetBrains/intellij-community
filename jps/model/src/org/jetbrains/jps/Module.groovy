package org.jetbrains.jps

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.idea.Facet
import org.jetbrains.annotations.TestOnly

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
  private List<String> excludes
  private List<String> ownExcludes = []

  Set<String> generatedSourceRoots = [];

  String basePath
  String outputPath
  String testOutputPath
  Map<String, Facet> facets = [:]
  Map<String, Object> props = [:]
  Map<String, String> sourceRootPrefixes = [:]
  Map<String, Library> libraries = [:]

  String languageLevel

  def Module(project, name, initializer) {
    this.project = project;
    this.name = name;
  }

  void dependency(Object item, DependencyScope scope, boolean exported) {
    dependencies << new ModuleDependency(project.resolve(item), scope, exported)
  }

  void classpath(Object[] arg) {
    arg.each { dependencies << new ModuleDependency(project.resolve(it), PredefinedDependencyScopes.COMPILE, false) }
  }

  void moduleSource() {
    dependencies << new ModuleDependency(new ModuleSourceEntry(module: this), PredefinedDependencyScopes.COMPILE, true)
  }

  void content(Object[] arg) {
    arg.each { contentRoots << FileUtil.toCanonicalPath(it) }
  }

  void src(Object[] arg) {
    arg.each { sourceRoots << FileUtil.toCanonicalPath(it) }
  }

  void testSrc(Object[] arg) {
    arg.each { testRoots << FileUtil.toCanonicalPath(it) }
  }

  void exclude(Object[] arg) {
    arg.each { addExcludedRoot(FileUtil.toCanonicalPath(it)) }
  }

  List<String> getOwnExcludes() {
    return ownExcludes
  }

  List<String> getExcludes() {
    if (excludes == null) {
      excludes = new ArrayList<String>(ownExcludes)
      Set<String> allContentRoots = project.modules.values().collect { it.contentRoots }.flatten() as Set
      Set<File> myRoots = contentRoots.collect { new File(it) } as Set
      for (root in allContentRoots) {
        if (!(root in contentRoots) && PathUtil.isUnder(myRoots, new File(root))) {
          excludes << FileUtil.toCanonicalPath(root)
        }
      }
    }
    return excludes
  }

  void addExcludedRoot(String path) {
    ownExcludes.add(path)
    excludes = null
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

  @TestOnly
  public void addDependency(ClasspathItem dependency, DependencyScope scope, boolean exported) {
    dependencies << new ModuleDependency(dependency, scope, exported)
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


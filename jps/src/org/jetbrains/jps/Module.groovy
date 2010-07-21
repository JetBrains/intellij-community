package org.jetbrains.jps

import org.jetbrains.jps.idea.Facet

/**
 * @author max
 */
class Module extends LazyInitializeableObject implements ClasspathItem {
  Project project;
  String name;
  Sdk sdk;

  List<ClasspathItem> classpath = []
  List<ClasspathItem> testclasspath = []
  List<ClasspathItem> providedClasspath = []
  List sourceRoots = []
  List testRoots = []
  List excludes = []

  String outputPath
  String testOutputPath
  Map<String, Facet> facets = [:]
  Map<String, Object> props = [:]
  Map<String, String> sourceRootPrefixes = [:]
  Map<String, Library> libraries = [:]

  def Module(project, name, initializer) {
    this.project = project;
    this.name = name;

    setInitializer({
      def meta = new InitializingExpando()
      meta.classpath = {Object[] arg ->
        arg.each { classpath << project.resolve(it) }
      }

      meta.testclasspath = {Object[] arg ->
        arg.each { testclasspath << project.resolve(it) }
      }

      meta.providedClasspath = {Object[] arg ->
        arg.each { providedClasspath << project.resolve(it) }
      }

      meta.src = {Object[] arg ->
        arg.each { sourceRoots << it }
      }

      meta.testSrc = {Object[] arg ->
        arg.each { testRoots << it }
      }

      meta.exclude = {Object[] arg ->
        arg.each { excludes << it }
      }

      initializer.delegate = meta
      initializer.setResolveStrategy Closure.DELEGATE_FIRST
      initializer.call()

      def wrongProperties = ["classpath", "src", "testSrc"] as Set
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
  
  def make() {
    project.builder.makeModule(this)
  }

  def getOutput() {
    make()
  }

  List<String> runtimeClasspath() {
    project.builder.moduleRuntimeClasspath(this, false)
  }

  List<String> testRuntimeClasspath() {
    project.builder.moduleRuntimeClasspath(this, true)
  }

  def makeTests() {
    project.builder.makeModuleTests(this)
  }

  def List<String> getClasspathRoots(boolean test) {
    if (test) {
      return [project.builder.moduleTestsOutput(this), project.builder.moduleOutput(this)]
    }
    else {
      return [project.builder.moduleOutput(this)]
    }
  }

  def List<String> getClasspath(boolean tests, boolean provided) {
    List answer = classpath;

    if (tests) {
      answer = [answer, testclasspath].flatten()
    }

    if (provided) {
      answer = [answer, providedClasspath].flatten()
    }

    return answer
  }
}

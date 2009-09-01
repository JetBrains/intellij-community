package org.jetbrains.jps

/**
 * @author max
 */
class Module extends LazyInitializeableObject implements ClasspathItem {
  Project project;
  String name;

  List<ClasspathItem> classpath = []
  List sourceRoots = []
  List testRoots = []
  List excludes = []
  
  Map<String, Object> props = [:]
  Map<String, String> sourceRootPrefixes = [:]

  def Module(project, name, initializer) {
    this.project = project;
    this.name = name;

    setInitializer({
      def meta = new InitializingExpando()
      meta.classpath = {Object[] arg ->
        arg.each { classpath << project.resolve(it) }
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
    return "module ${name}, classpath: ${classpath}, sources: ${sourceRoots}, excludes: $excludes"
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
}

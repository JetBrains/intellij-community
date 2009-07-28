package org.jetbrains.jps

/**
 * @author max
 */
class Library extends LazyInitializeableObject implements ClasspathItem {
  Project project;
  String name;

  List classpath = []
  List sourceRoots = []

  private Map<String, Object> props = [:]

  def Library(project, name, initializer) {
    this.project = project;
    this.name = name;

    setInitializer({
      def meta = new InitializingExpando()
      meta.classpath = {Object[] arg ->
        arg.each { classpath << it }
      }

      meta.src = {Object[] arg ->
        arg.each { sourceRoots << it }
      }

      initializer.delegate = meta
      initializer.setResolveStrategy Closure.DELEGATE_FIRST
      initializer.call()

      def wrongProperties = ["classpath", "src"] as Set
      meta.getProperties().each {String key, Object value ->
        if (!wrongProperties.contains(key)) {
          props[key] = value
        }
      }
    })
  }

  def String toString() {
    return "library ${name}. classpath: ${classpath}"
  }

  def List<String> getClasspathRoots(boolean test) {
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

package org.jetbrains.jps

import org.apache.tools.ant.BuildException
import org.codehaus.gant.GantBinding
import org.jetbrains.jps.resolvers.LibraryResolver
import org.jetbrains.jps.resolvers.ModuleResolver
import org.jetbrains.jps.resolvers.PathEntry
import org.jetbrains.jps.resolvers.Resolver

/**
 * @author max
 */
class Project {
  final ProjectBuilder builder;
  final GantBinding binding;
  final List<ModuleBuilder> sourceGeneratingBuilders = []
  final List<ModuleBuilder> sourceModifyingBuilders = []
  final List<ModuleBuilder> translatingBuilders = []
  final List<ModuleBuilder> weavingBuilders = []
  final List<Resolver> resolvers = []
  final Map<String, Object> props = [:]

  String targetFolder = "."

  def Project(GantBinding binding) {
    builder = new ProjectBuilder(binding, this)
    this.binding = binding;

    sourceGeneratingBuilders << new GroovyStubGenerator(this)
    translatingBuilders << new JavacBuilder()
    translatingBuilders << new GroovycBuilder(this)
    translatingBuilders << new ResourceCopier()

    resolvers << new ModuleResolver(project: this)
    resolvers << new LibraryResolver(project: this)

    List exts = "properties,xml,gif,png,jpeg,jpg,jtml,dtd,tld,ftl".split(",")
    
    binding.ant.patternset(id : "default.compiler.resources") {
      exts.each {ext -> include (name : "**/?*.${ext}")}
    }

    props["compiler.resources.id"] = "default.compiler.resources"
  }

  def List<ModuleBuilder> builders() {
    [sourceGeneratingBuilders, sourceModifyingBuilders, translatingBuilders, weavingBuilders].flatten()
  }

  def Module createModule(String name, Closure initializer) {
    Module existingModule = modules[name]
    if (existingModule != null) error("Module ${name} already exists")

    def module = new Module(this, name, initializer)
    modules.put(name, module)
    binding.setVariable(name, module)
    module
  }

  def Library createLibrary(String name, Closure initializer) {
    Library lib = libraries[name]
    if (lib != null) error("Library ${name} already defined")

    lib = new Library(this, name, initializer)
    libraries.put(name, lib)
    binding.setVariable(name, lib)
    lib
  }

  def Map<String, Module> modules = [:]
  def Map<String, Library> libraries = [:]

  def String toString() {
    return modules.toString()
  }

  def List<Module> sortedModules() {
    def result = new LinkedHashSet()
    modules.values().each {
      collectModulesOnClasspath(it, result)
    }
    return result.asList()
  }

  private def collectModulesOnClasspath(Module module, Set<Module> result) {
    module.classpath.each {
      if (it instanceof String) {
        def resolved = modules[it]

        if (resolved instanceof Module) {
          collectModulesOnClasspath(resolved, result)
        }
      }
    }

    result.add(module)
  }

  def error(String message) {
    throw new BuildException(message)
  }

  def makeAll() {
    sortedModules().each { it.make(); it.makeTests() }
  }

  def clean() {
    binding.ant.delete(dir: targetFolder)
  }

  def ClasspathItem resolve(Object dep) {
    if (dep instanceof ClasspathItem) {
      return dep
    }

    if (dep instanceof String) {
      String path = dep
      List<ClasspathItem> results = []
      resolvers.each {
        def resolved = it.resolve(path)
        if (resolved != null) results.add(resolved)
      }

      if (results.isEmpty()) {
        if (new File(path).exists()) return new PathEntry(path: path)

        error("Cannot resolve $path")
      }
      else if (results.size() > 1) {
        error("Ambigous resolve for $path. All of $results match")
      }

      return results[0]
    }

    error("cannot resolve $dep")
    return null
  }

  def getAt(String key) {
    if (props[key] != null) return props[key]
    try {
      return binding[key]
    }
    catch (MissingPropertyException e) {
    }
    return null
  }

  def putAt(String key, Object value) {
    props[key] = value
  }
}

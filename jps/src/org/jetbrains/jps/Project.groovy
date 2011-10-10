package org.jetbrains.jps

import org.codehaus.gant.GantBinding
import org.jetbrains.jps.artifacts.Artifact
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
  final List<Resolver> resolvers = []
  final Map<String, Object> props = [:]

  final Map<String, Library> globalLibraries = [:]
  final Map<String, Sdk> sdks = [:]

  Sdk projectSdk;
  final Map<String, Module> modules = [:]
  final Map<String, Library> libraries = [:]
  final Map<String, Artifact> artifacts = [:]
  final Map<String, RunConfiguration> runConfigurations = [:]
  final CompilerConfiguration compilerConfiguration = new CompilerConfiguration()

  String projectCharset; // contains project charset, if not specified default charset will be used (used by compilers)

  def Project(GantBinding binding) {
    this.binding = binding
    builder = new ProjectBuilder(binding, this)

    resolvers << new ModuleResolver(project: this)
    resolvers << new LibraryResolver(project: this)
  }

  def ProjectBuilder getBuilder () {
    return builder;
  }

  def Module createModule(String name, Closure initializer) {
    Module existingModule = modules[name]
    if (existingModule != null) error("Module ${name} already exists")

    def module = new Module(this, name, initializer)
    modules.put(name, module)

    try {
      binding.getVariable(name);
      debug("Variable '$name' is already defined in context. Equally named module will not be accessible. Use project.modules['$name'] instead")
    }
    catch (MissingPropertyException mpe) {
      binding.setVariable(name, module)
    }

    module
  }

  def Library createLibrary(String name, Closure initializer) {
    createLibrary(name, initializer, libraries, "project.library")
  }

  def Library createGlobalLibrary(String name, Closure initializer) {
    createLibrary(name, initializer, globalLibraries, "project.globalLibrary")
  }

  private def Library createLibrary(String name, Closure initializer, Map<String, Library> libraries, String accessor) {
    Library lib = libraries[name]
    if (lib != null) error("Library ${name} already defined")

    lib = new Library(this, name, initializer)
    libraries.put(name, lib)

    try {
      binding.getVariable(name)
      debug("Variable '$name' is already defined in context. Equally named library will not be accessible. Use $accessor['$name'] instead")
    }
    catch (MissingPropertyException mpe) {
      binding.setVariable(name, lib)
    }

    lib
  }

  def JavaSdk createJavaSdk(String name, String path, Closure initializer) {
    if (sdks[name] != null) error("SDK '$name' already defined")

    def sdk = new JavaSdk(this, name, path, initializer)
    sdks[name] = sdk
    return sdk
  }

  def String toString() {
    return "Project with ${modules.size()} modules and ${libraries.size()} libraries"
  }

  def error(String message) {
    builder.error(message)
  }

  def info(String message) {
    builder.info(message)
  }

  def debug(String message) {
    builder.debug(message)
  }

  def ClasspathItem resolve(Object dep) {
    if (dep instanceof ClasspathItem) {
      return dep
    }

    String path = dep.toString()

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

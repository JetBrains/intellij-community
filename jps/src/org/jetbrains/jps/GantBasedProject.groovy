package org.jetbrains.jps

import org.codehaus.gant.GantBinding
import org.jetbrains.jps.resolvers.LibraryResolver
import org.jetbrains.jps.resolvers.ModuleResolver
import org.jetbrains.jps.resolvers.PathEntry
import org.jetbrains.jps.resolvers.Resolver

/**
 * @author nik
 */
class GantBasedProject extends Project {
  final Map<String, Object> props = [:]
  final GantBinding binding;
  final List<Resolver> resolvers = []
  final ProjectBuilder builder;

  GantBasedProject(GantBinding binding) {
    this.binding = binding
    builder = new ProjectBuilder(binding, this)
    resolvers << new ModuleResolver(project: this)
    resolvers << new LibraryResolver(project: this)
  }

  ProjectBuilder getBuilder() {
    return builder
  }

  @Override
  Module createModule(String name, Closure initializer) {
    if (modules[name] != null) error("Module ${name} already exists")

    Module module = doCreateModule(name, initializer)
    try {
      binding.getVariable(name);
      debug("Variable '$name' is already defined in context. Equally named module will not be accessible. Use project.modules['$name'] instead")
    }
    catch (MissingPropertyException mpe) {
      binding.setVariable(name, module)
    }
    return module
  }

  private Module doCreateModule(String name, Closure initializer) {
    return super.createModule(name, initializer)
  }

  @Override
  protected Library createLibrary(String name, Closure initializer, Map<String, Library> libraries, String accessor) {
    if (libraries[name] != null) error("Library ${name} already defined")
    Library lib = super.createLibrary(name, initializer, libraries, accessor)
    try {
      binding.getVariable(name)
      debug("Variable '$name' is already defined in context. Equally named library will not be accessible. Use $accessor['$name'] instead")
    }
    catch (MissingPropertyException mpe) {
      binding.setVariable(name, lib)
    }
    return lib
  }

  @Override
  JavaSdk createJavaSdk(String name, String path, Closure initializer) {
    if (sdks[name] != null) error("SDK '$name' already defined")
    return super.createJavaSdk(name, path, initializer)
  }

  def error(String message) {
    builder.error(message)
  }

  def debug(String message) {
    builder.debug(message)
  }

  def info(String message) {
    builder.info(message)
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

  @Override
  ClasspathItem resolve(Object dep) {
    if (dep instanceof ClasspathItem) {
      return super.resolve(dep)
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
}

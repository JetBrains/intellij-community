package org.jetbrains.jps

import org.apache.tools.ant.BuildException
import org.codehaus.gant.GantBinding
import org.jetbrains.jps.artifacts.Artifact
import org.jetbrains.jps.artifacts.ArtifactBuilder
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
  final ArtifactBuilder artifactBuilder
  final Map<String, Object> props = [:]

  final Map<String, Library> globalLibraries = [:]
  final Map<String, Sdk> sdks = [:]

  Sdk projectSdk;
  final Map<String, Module> modules = [:]
  final Map<String, Library> libraries = [:]
  final Map<String, Artifact> artifacts = [:]

  String targetFolder = null

  boolean dryRun = false

  def Project(GantBinding binding) {
    this.binding = binding
    builder = new ProjectBuilder(binding, this)
    artifactBuilder = new ArtifactBuilder(this)

    resolvers << new ModuleResolver(project: this)
    resolvers << new LibraryResolver(project: this)

    def defaultResourceExtensions = "properties,xml,gif,png,jpeg,jpg,jtml,dtd,tld,ftl"
    List exts = defaultResourceExtensions.split(",")
    
    binding.ant.patternset(id : "default.compiler.resources") {
      exts.each {ext -> include (name : "**/?*.${ext}")}
    }

    props["compiler.resources.id"] = "default.compiler.resources"
  }

  def Module createModule(String name, Closure initializer) {
    Module existingModule = modules[name]
    if (existingModule != null) error("Module ${name} already exists")

    def module = new Module(this, name, initializer)
    modules.put(name, module)

    try {
      binding.getVariable(name);
      warning("Variable '$name' is already defined in context. Equally named module will not be accessible. Use project.modules['$name'] instead")
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
      warning("Variable '$name' is already defined in context. Equally named library will not be accessible. Use $accessor['$name'] instead")
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
    throw new BuildException(message)
  }

  def warning(String message) {
    binding.ant.project.log(message, org.apache.tools.ant.Project.MSG_WARN)
  }

  def stage(String message) {
    builder.buildInfoPrinter.printProgressMessage(this, message)
  }

  def info(String message) {
    binding.ant.project.log(message, org.apache.tools.ant.Project.MSG_INFO)
  }

  def makeAll() {
    builder.buildAll()
  }

  def makeProduction() {
    builder.buildProduction()
  }

  def buildArtifacts() {
    artifactBuilder.buildArtifacts()
  }

  def buildArtifact(String artifactName) {
    def artifact = artifacts[artifactName]
    if (artifact == null) {
      error("Artifact '$artifactName' not found")
    }
    artifactBuilder.buildArtifact(artifact)
  }

  def clean() {
    if (!dryRun) {
      if (targetFolder != null) {
        stage("Cleaning $targetFolder")
        binding.ant.delete(dir: targetFolder)
      }
      else {
        stage("Cleaning output folders for ${modules.size()} modules")
        modules.values().each {
          binding.ant.delete(dir: it.outputPath)
          binding.ant.delete(dir: it.testOutputPath)
        }
        stage("Cleaning output folders for ${artifacts.size()} artifacts")
        artifacts.values().each {
          binding.ant.delete(dir: it.outputPath)
        }
      }
    }
    else {
      stage("Cleaning skipped as we're running dry")
    }
    builder.clean()
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

  String getPropertyIfDefined(String name) {
    try {
      binding[name]
    }
    catch (MissingPropertyException mpe) {
      return null
    }
  }

  boolean isDefined(String prop) {
    try {
      binding[prop]
      return true
    }
    catch (MissingPropertyException mpe) {
      return false
    }
  }

  def exportProperty(String name, String value) {
    binding.ant.project.setProperty(name, value)
  }

  def taskdef(Map args) {
    binding.ant.taskdef(name: args.name, classname: args.classname) {
      String additionalClasspathId = getPropertyIfDefined("additional.classpath.id")
      if (additionalClasspathId != null) {
        classpath (refid: additionalClasspathId)
      }
    }
  }
}

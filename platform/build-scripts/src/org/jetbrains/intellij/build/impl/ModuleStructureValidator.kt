// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.application.ArchivedCompilationContextUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.impl.JpsJavaDependencyExtensionRole
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.walk

private const val includeName = "include"
private const val fallbackName = "fallback"

private val pathAttributes = hashSetOf(
  "interface", "implementation", "class", "topic", "instance", "provider",
  "implements", "headlessImplementation", "serviceInterface", "serviceImplementation",
  "interfaceClass", "implementationClass", "beanClass", "schemeClass", "factoryClass", "handlerClass", "hostElementClass", "targetClass",
  "forClass", "className", "predicateClassName", "displayNameSupplierClassName", "preloaderClassName",
  "treeRenderer"
)

private val nonPathAttributes = hashSetOf(
  "id", "value", "key", "testServiceImplementation", "defaultExtensionNs", "qualifiedName", "childrenEPName"
)

private val pathElements = hashSetOf("interface-class", "implementation-class")
private val predefinedTypes = hashSetOf("java.lang.Object")
private val ignoreModules = hashSetOf("intellij.java.testFramework", "intellij.platform.uast.testFramework")

class ModuleStructureValidator(private val context: BuildContext, private val allProductModules: Collection<ModuleItem>) {

  private val errors = ArrayList<AssertionError>()
  private val libraryFiles = HashMap<JpsLibrary, Set<String>>()
  private val libraryClasses = HashMap<JpsLibrary, Set<String>>()

  private fun getLibraryFiles(library: JpsLibrary): Set<String> {
    @Suppress("NAME_SHADOWING")
    return libraryFiles.computeIfAbsent(library) { library ->
      val result = HashSet<String>()
      for (libraryRootUrl in library.getRootUrls(JpsOrderRootType.COMPILED)) {
        val path = Path.of(JpsPathUtil.urlToPath(libraryRootUrl))
        FileSystems.newFileSystem(path).use {
          it.rootDirectories.forEach { rootDirectory ->
            rootDirectory.walk().forEach { file ->
              result.add(file.invariantSeparatorsPathString.removePrefix("/"))
            }
          }
        }
      }
      result
    }
  }

  private fun getLibraryClasses(library: JpsLibrary): Set<String> {
    return libraryClasses.computeIfAbsent(library) { library ->
      getLibraryFiles(library)
        .filter { file ->
          file.endsWith(".class") && !file.endsWith("Kt.class")
        }
        .mapTo(HashSet()) { file ->
          removeSuffixStrict(file, ".class").replace("/", ".")
        }
    }
  }

  fun validate(): List<AssertionError> {
    errors.clear()

    val messages = context.messages
    messages.info("Validating jars...")
    validateJarModules()

    messages.info("Validating modules...")
    val visitedModules = HashSet<JpsModule>()
    for (moduleName in allProductModules.map { it.moduleName }.distinct()) {
      if (ignoreModules.contains(moduleName)) {
        continue
      }
      validateModuleDependencies(visitedModules, context.findRequiredModule(moduleName))
    }

    messages.info("Validating xml descriptors...")
    validateXmlDescriptors()

    if (errors.isEmpty()) {
      messages.info("Validation finished successfully")
    }

    return errors
  }

  private fun validateJarModules() {
    val modulesInJars = MultiMap<String, String>()
    for (item in allProductModules) {
      modulesInJars.putValue(item.moduleName, item.relativeOutputFile)
    }

    for (module in modulesInJars.keySet()) {
      val jars = modulesInJars.get(module)
      if (jars.size > 1) {
        context.messages.warning("Module '$module' contains in several JARs: ${jars.joinToString(separator = "; ")}")
      }
    }
  }

  private fun validateModuleDependencies(visitedModules: MutableSet<JpsModule>, module: JpsModule) {
    if (visitedModules.contains(module)) {
      return
    }
    visitedModules.add(module)

    for (dependency in module.dependenciesList.dependencies) {
      if (dependency is JpsModuleDependency) {
        // skip test dependencies
        val role = dependency.container.getChild(JpsJavaDependencyExtensionRole.INSTANCE)
        if (role != null && role.scope.name == "TEST") {
          continue
        }
        if (role != null && role.scope.name == "RUNTIME") {
          continue
        }
        if (role != null && role.scope.name == "PROVIDED") { // https://jetbrains.slack.com/archives/C0XLQPQGP/p1733558147426029?thread_ts=1733392446.551349&cid=C0XLQPQGP
          continue
        }

        // skip localization modules
        val dependantModule = dependency.module!!
        if (dependantModule.name.endsWith("resources.en")) {
          continue
        }

        if (allProductModules.none { it.moduleName == dependantModule.name }) {
          errors.add(AssertionError("Missing dependency found: ${module.name} -> ${dependantModule.name} [${role.scope.name}]", null))
          continue
        }

        validateModuleDependencies(visitedModules, dependantModule)
      }
    }
  }

  private fun validateXmlDescriptors() {
    val roots = ArrayList<Path>()
    val libraries = HashSet<JpsLibrary>()
    for (moduleName in allProductModules.map { it.moduleName }.distinct()) {
      val module = context.findRequiredModule(moduleName)
      for (root in module.sourceRoots) {
        roots.add(root.path)
      }

      for (dependencyElement in module.dependenciesList.dependencies) {
        if (dependencyElement is JpsLibraryDependency) {
          libraries.add(dependencyElement.library!!)
        }
      }
    }

    // start validating from product xml descriptor
    var productDescriptorName = ""
    var productDescriptorFile: Path? = null
    for (c in listOf("META-INF/plugin.xml", "META-INF/${context.productProperties.platformPrefix}Plugin.xml")) {
      productDescriptorName = c
      productDescriptorFile = findDescriptorFile(productDescriptorName, roots) ?: continue
    }
    if (productDescriptorFile == null) {
      errors.add(AssertionError("Can not find product descriptor $productDescriptorName"))
      return
    }

    val allDescriptors = HashSet<Path>()
    validateXmlDescriptorsRec(productDescriptorFile, roots, libraries, allDescriptors)
    validateXmlRegistrations(allDescriptors)
  }

  private fun findLibraryWithFile(name: String, libraries: Set<JpsLibrary>): JpsLibrary? {
    return libraries.firstOrNull { getLibraryFiles(it).contains(name) }
  }

  private fun validateXmlDescriptorsRec(descriptor: Path, roots: List<Path>, libraries: Set<JpsLibrary>, allDescriptors: MutableSet<Path>) {
    allDescriptors.add(descriptor)

    val descriptorFiles = ArrayList<Path>()
    val xml = Files.newInputStream(descriptor).use(::readXmlAsModel)

    for (includeNode in xml.children(includeName)) {
      val ref = includeNode.getAttributeValue("href") ?: continue
      val descriptorFile = findDescriptorFile(ref, roots + listOf(descriptor.parent))
      if (descriptorFile == null) {
        val library1 = findLibraryWithFile(ref.removePrefix("/"), libraries)
        if (library1 != null) {
          context.messages.warning("Descriptor '$ref' came from library '${library1.name}', referenced in '${descriptor.name}'")
        }
        else {
          val isOptional = includeNode.children.any { it.name == fallbackName }
          if (isOptional) {
            context.messages.info("Ignore optional missing xml descriptor '$ref' referenced in '${descriptor.name}'")
          }
          else {
            errors.add(AssertionError("Can not find xml descriptor '$ref' referenced in '${descriptor.name}'"))
          }
        }
      }
      else {
        descriptorFiles.add(descriptorFile)
      }
    }

    for (descriptorFile in descriptorFiles) {
      validateXmlDescriptorsRec(descriptorFile, roots, libraries, allDescriptors)
    }
  }

  private fun validateXmlRegistrations(descriptors: HashSet<Path>) {
    val classes = HashSet<String>(predefinedTypes)
    val visitedLibraries = HashSet<String>()
    for (moduleName in allProductModules.map { it.moduleName }.distinct()) {
      val jpsModule = context.findRequiredModule(moduleName)

      if (jpsModule.sourceRoots.isEmpty()) {
        // no source roots -> no classes
        continue
      }

      var hasOutput = false
      jpsModule.processProductionOutput { outputRoot ->
        hasOutput = outputRoot.exists()
        outputRoot
          .walk()
          .filter { path ->
            path.isRegularFile() && path.name.endsWith(".class") && !path.name.endsWith("Kt.class")
          }.forEach { path ->
            val normalizedPath = outputRoot.relativize(path).invariantSeparatorsPathString
            val className = removeSuffixStrict(normalizedPath, ".class").replace('/', '.')
            classes.add(className)
          }
      }
      if (!hasOutput) {
        throw IllegalStateException("Module '$moduleName' output is missing while module has source roots")
      }

      for (dependencyElement in jpsModule.dependenciesList.dependencies) {
        if (dependencyElement is JpsLibraryDependency) {
          val jpsLibrary = dependencyElement.library
          val library = jpsLibrary ?: continue
          if (!visitedLibraries.add(library.name)) {
            continue
          }

          classes.addAll(getLibraryClasses(jpsLibrary))
        }
      }
    }

    context.messages.info("Found ${classes.size} classes in ${allProductModules.map { it.moduleName }.distinct().size} modules and ${visitedLibraries.size} libraries")

    for (descriptor in descriptors) {
      val xml = Files.newInputStream(descriptor).use(::readXmlAsModel)
      validateXmlRegistrationsRec(descriptor.name, xml, classes)
    }
  }

  private fun validateXmlRegistrationsRec(source: String, xml: XmlElement, classes: HashSet<String>) {
    for ((name, value) in xml.attributes) {
      if (pathAttributes.contains(name)) {
        checkRegistration(source, value, classes)
        continue
      }
      if (nonPathAttributes.contains(name)) {
        continue
      }

      if (value.startsWith("com.") || value.startsWith("org.")) {
        context.messages.warning(
          "Attribute '$name' contains qualified path '$value'. " +
          "Add attribute into 'ModuleStructureValidator.pathAttributes' or 'ModuleStructureValidator.nonPathAttributes' collection."
        )
      }
    }

    for (child in xml.children) {
      for (pathElement in pathElements) {
        if (child.name == pathElement) {
          checkRegistration(source, child.content, classes)
        }
      }

      validateXmlRegistrationsRec(source, child, classes)
    }
  }

  private fun checkRegistration(source: String, value: String?, classes: Set<String>) {
    if (value.isNullOrEmpty() || classes.contains(value)) {
      return
    }
    if (value.startsWith("com.intellij.testFramework.fixtures.")) {
      //todo test-only services should be registered in test resources, see IJPL-206480
      return
    }
    errors.add(AssertionError("Unresolved registration '$value' in $source"))
  }
}

/**
 * Calls [processor] for the path containing the production output of [this@processModuleProductionOutput].
 * Works both when module output is located in a directory and when it's packed in a JAR.
 */
private fun <T> JpsModule.processProductionOutput(processor: (outputRoot: Path) -> T): T {
  val archivedCompiledClassesMapping = ArchivedCompilationContextUtil.archivedCompiledClassesMapping
  val outputJarPath = archivedCompiledClassesMapping?.get("production/$name")
  if (outputJarPath == null) {
    val outputDirectoryPath = JpsJavaExtensionService.getInstance().getOutputDirectoryPath(this, false)
                              ?: error("Output directory is not specified for '$name'")
    return processor(outputDirectoryPath)
  }
  else {
    return FileSystems.newFileSystem(Path(outputJarPath)).use {
      processor(it.rootDirectories.single())
    }
  }
}


private fun removeSuffixStrict(string: String, @Suppress("SameParameterValue") suffix: String?): String {
  if (suffix.isNullOrEmpty()) {
    throw IllegalArgumentException("'suffix' is null or empty")
  }

  if (!string.endsWith(suffix)) {
    throw IllegalStateException("String must end with '$suffix': $string")
  }

  return string.dropLast(suffix.length)
}

private fun findDescriptorFile(name: String, roots: List<Path>): Path? {
  for (root in roots) {
    val descriptorFile = root.resolve(name.removePrefix("/"))
    if (Files.isRegularFile(descriptorFile)) {
      return descriptorFile
    }
  }
  return null
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

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
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.BiPredicate
import java.util.jar.JarInputStream
import kotlin.io.path.name

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

  private fun getLibraryFiles(library: JpsLibrary): Set<String> {
    @Suppress("NAME_SHADOWING")
    return libraryFiles.computeIfAbsent(library) { library ->
      val result = HashSet<String>()
      for (libraryRootUrl in library.getRootUrls(JpsOrderRootType.COMPILED)) {
        val path = Path.of(JpsPathUtil.urlToPath(libraryRootUrl))
        JarInputStream(FileInputStream(path.toFile())).use { jarStream ->
          while (true) {
            result.add((jarStream.nextJarEntry ?: break).name)
          }
        }
      }
      result
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

      val outputDirectory = JpsJavaExtensionService.getInstance().getOutputDirectory(jpsModule, false)!!.toPath()
      val outputDirectoryPrefix = outputDirectory.toString().replace('\\', '/') + "/"
      if (!Files.isDirectory(outputDirectory)) {
        if (jpsModule.contentRootsList.urls.isEmpty()) {
          // no content roots -> no classes
          continue
        }

        throw IllegalStateException("Module output directory '$outputDirectory' is missing")
      }

      Files.find(outputDirectory, Int.MAX_VALUE, BiPredicate { path, attributes ->
        if (attributes.isRegularFile) {
          val fileName = path.fileName.toString()
          fileName.endsWith(".class") && !fileName.endsWith("Kt.class")
        }
        else {
          false
        }
      }).use { stream ->
        stream.forEach {
          val normalizedPath = it.toString().replace('\\', '/')
          val className = removeSuffixStrict(removePrefixStrict(normalizedPath, outputDirectoryPrefix), ".class").replace('/', '.')
          classes.add(className)
        }
      }

      for (dependencyElement in jpsModule.dependenciesList.dependencies) {
        if (dependencyElement is JpsLibraryDependency) {
          val jpsLibrary = dependencyElement.library
          val library = jpsLibrary ?: continue
          if (!visitedLibraries.add(library.name)) {
            continue
          }

          val libraryFiles = getLibraryFiles(jpsLibrary)
          for (fileName in libraryFiles) {
            if (!fileName.endsWith(".class") || fileName.endsWith("Kt.class")) {
              return
            }

            classes.add(removeSuffixStrict(fileName, ".class").replace("/", "."))
          }
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
    errors.add(AssertionError("Unresolved registration '$value' in $source"))
  }
}

private fun removeSuffixStrict(string: String, @Suppress("SameParameterValue") suffix: String?): String {
  if (suffix.isNullOrEmpty()) {
    throw IllegalArgumentException("'suffix' is null or empty")
  }

  if (!string.endsWith(suffix)) {
    throw IllegalStateException("String must end with '$suffix': $string")
  }

  return string.substring(0, string.length - suffix.length)
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

private fun removePrefixStrict(string: String, prefix: String?): String {
  if (prefix.isNullOrEmpty()) {
    throw IllegalArgumentException("'prefix' is null or empty")
  }

  if (!string.startsWith(prefix)) {
    throw IllegalStateException("String must start with '$prefix': $string")
  }

  return string.substring(prefix.length)
}
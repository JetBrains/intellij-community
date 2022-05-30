// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.containers.MultiMap
import groovy.io.FileType
import groovy.transform.CompileStatic
import groovy.xml.QName
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.impl.JpsJavaDependencyExtensionRole
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsDependencyElement
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.util.JpsPathUtil

import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarInputStream

@CompileStatic
final class ModuleStructureValidator {
  private static QName includeName = new QName("http://www.w3.org/2001/XInclude", "include")
  private static QName fallbackName = new QName("http://www.w3.org/2001/XInclude", "fallback")

  private static Set<String> pathAttributes = Set.of(
    "interface", "implementation", "class", "topic", "instance", "provider",
    "implements", "headlessImplementation", "serviceInterface", "serviceImplementation",
    "interfaceClass", "implementationClass", "beanClass", "schemeClass", "factoryClass", "handlerClass", "hostElementClass", "targetClass",
    "forClass", "className", "predicateClassName", "displayNameSupplierClassName", "preloaderClassName",
    "treeRenderer"
  )

  private static Set<String> nonPathAttributes = Set.of(
    "id", "value", "key", "testServiceImplementation", "defaultExtensionNs", "qualifiedName", "childrenEPName"
  )

  private static Set<String> pathElements = Set.of("interface-class", "implementation-class")
  private static Set<String> predefinedTypes = Set.of("java.lang.Object")
  private static Set<String> ignoreModules = Set.of("intellij.java.testFramework", "intellij.platform.uast.tests")

  private BuildContext buildContext
  private MultiMap<String, String> moduleJars = new MultiMap<String, String>()
  private Set<String> moduleNames = new HashSet<String>()
  private List<AssertionError> errors = new ArrayList<>()
  private Map<JpsLibrary, Set<String>> libraryFiles = new HashMap<>()

  ModuleStructureValidator(BuildContext buildContext, MultiMap<String, String> moduleJars) {
    this.buildContext = buildContext
    for (moduleJar in moduleJars.entrySet()) {
      // Filter out jars with relative paths in name
      if (moduleJar.key.contains("\\") || moduleJar.key.contains("/"))
        continue

      this.moduleJars.put(moduleJar.key, moduleJar.value)
      this.moduleNames.addAll(moduleJar.value)
    }
  }

  private Set<String> getLibraryFiles(JpsLibrary library) {
    libraryFiles.computeIfAbsent(library, {
      Set<String> result = new HashSet<>()
      for (String libraryRootUrl in library.getRootUrls(JpsOrderRootType.COMPILED)) {
        def path = Path.of(JpsPathUtil.urlToPath(libraryRootUrl))

        try (JarInputStream jarStream = new JarInputStream(new FileInputStream(path.toFile()))) {
          JarEntry je
          while ((je = jarStream.getNextJarEntry()) != null) {
            result.add(je.name)
          }
        }
      }
      return result
    })
  }

  List<AssertionError> validate() {
    errors.clear()

    buildContext.messages.info("Validating jars...")
    validateJarModules()

    buildContext.messages.info("Validating modules...")
    Set<JpsModule> visitedModules = new HashSet<JpsModule>()
    for (moduleName in moduleNames) {
      if (ignoreModules.contains(moduleName)) {
        continue
      }
      validateModuleDependencies(visitedModules, buildContext.findModule(moduleName))
    }

    buildContext.messages.info("Validating xml descriptors...")
    validateXmlDescriptors()

    if (errors.isEmpty()) {
      buildContext.messages.info("Validation finished successfully")
    }

    return errors
  }

  private void validateJarModules() {
    def modulesInJars = new MultiMap<String, String>()
    for (jar in moduleJars.keySet()) {
      for (module in moduleJars.get(jar)) {
        modulesInJars.putValue(module, jar)
      }
    }

    for (module in modulesInJars.keySet()) {
      def jars = modulesInJars.get(module)
      if (jars.size() > 1) {
        buildContext.messages.warning("Module '$module' contains in several JARs: " + jars.join("; "))
      }
    }
  }

  private void validateModuleDependencies(HashSet<JpsModule> visitedModules, JpsModule module) {
    if (visitedModules.contains(module)) {
      return
    }
    visitedModules.add(module)

    for (dependency in module.dependenciesList.dependencies) {
      if (dependency instanceof JpsModuleDependency) {
        // Skip test dependencies
        def role = dependency.container.getChild(JpsJavaDependencyExtensionRole.INSTANCE)
        if (role != null && role.scope.name() == "TEST") continue
        if (role != null && role.scope.name() == "RUNTIME") continue

        // Skip localization modules
        def dependantModule = ((JpsModuleDependency)dependency).module
        if (dependantModule.name.endsWith("resources.en")) {
          continue
        }

        if (!moduleNames.contains(dependantModule.name)) {
          errors.add(new AssertionError("Missing dependency found: ${module.name} -> ${dependantModule.name} [${role.scope.name()}]".toString(), null))
          continue
        }

        validateModuleDependencies(visitedModules, dependantModule)
      }
    }
  }

  private void validateXmlDescriptors() {
    List<File> roots = new ArrayList<File>()
    Set<JpsLibrary> libraries = new HashSet<>()
    for (moduleName in moduleNames) {
      def module = buildContext.findModule(moduleName)
      for (root in module.sourceRoots) {
        roots.add(root.file)
      }

      for (JpsDependencyElement dependencyElement in module.dependenciesList.getDependencies()) {
        if (dependencyElement instanceof JpsLibraryDependency) {
          libraries.add(dependencyElement.library)
        }
      }
    }

    // Start validating from product xml descriptor
    String productDescriptorName = "META-INF/${buildContext.productProperties.platformPrefix}Plugin.xml"
    File productDescriptorFile = findDescriptorFile(productDescriptorName, roots)
    if (productDescriptorFile == null) {
      errors.add(new AssertionError("Can not find product descriptor $productDescriptorName".toString(), null))
      return
    }

    HashSet<File> allDescriptors = new HashSet<File>()
    validateXmlDescriptorsRec(productDescriptorFile, roots, libraries, allDescriptors)
    validateXmlRegistrations(allDescriptors)
  }

  @Nullable
  private JpsLibrary findLibraryWithFile(String name, Set<JpsLibrary> libraries) {
    for (library in libraries) {
      if (getLibraryFiles(library).contains(name)) {
        return library
      }
    }
    return null
  }

  private void validateXmlDescriptorsRec(File descriptor, List<File> roots, Set<JpsLibrary> libraries, HashSet<File> allDescriptors) {

    allDescriptors.add(descriptor)

    def descriptorFiles = new ArrayList<File>()
    def xml = new XmlParser().parse(descriptor)

    def includeNodes = xml.depthFirst().findAll { it instanceof Node && ((Node)it).name() == includeName }
    for (includeNode in includeNodes) {
      def ref = ((Node)includeNode).attribute("href").toString()
      if (ref == null) continue

      def descriptorFile = findDescriptorFile(ref, roots + descriptor.parentFile)
      if (descriptorFile == null) {
        JpsLibrary library1 = findLibraryWithFile(removePrefixStrict(ref, "/"), libraries)
        if (library1 != null) {
          buildContext.messages.warning("Descriptor '$ref' came from library '${library1.name}', referenced in '${descriptor.name}'")
        }
        else {
          def isOptional = (((Node)includeNode).children().any { it instanceof Node && ((Node)it).name() == fallbackName })
          if (isOptional) {
            buildContext.messages.info("Ignore optional missing xml descriptor '$ref' referenced in '${descriptor.name}'")
          }
          else {
            errors.add(new AssertionError("Can not find xml descriptor '$ref' referenced in '${descriptor.name}'".toString(), null))
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

  private static File findDescriptorFile(String name, List<File> roots) {
    for (root in roots) {
      def descriptorFile = new File(root, name)
      if (descriptorFile.exists() && descriptorFile.file) {
        return descriptorFile
      }
    }

    return null
  }

  private void validateXmlRegistrations(HashSet<File> descriptors) {
    def classes = new HashSet<String>(predefinedTypes)
    Set<String> visitedLibraries = new HashSet<>()
    for (moduleName in moduleNames) {
      JpsModule jpsModule = buildContext.findModule(moduleName)

      def outputDirectory = JpsJavaExtensionService.instance.getOutputDirectory(jpsModule, false)
      def outputDirectoryPrefix = outputDirectory.path.replace("\\", "/") + "/"
      if (!outputDirectory.isDirectory()) {
        if (jpsModule.contentRootsList.urls.isEmpty()) {
          // no content roots -> no classes
          continue
        }

        throw new IllegalStateException("Module output directory '$outputDirectory' is missing")
      }

      outputDirectory.eachFileRecurse(FileType.FILES) {
        if (!it.name.endsWith('.class') || it.name.endsWith("Kt.class")) {
          return
        }

        def normalizedPath = it.path.replace("\\", "/")
        def className = removeSuffixStrict(removePrefixStrict(normalizedPath, outputDirectoryPrefix), ".class").replace("/", ".")
        classes.add(className)
      }

      for (JpsDependencyElement dependencyElement in jpsModule.dependenciesList.getDependencies()) {
        if (dependencyElement instanceof JpsLibraryDependency) {
          if (!visitedLibraries.add(dependencyElement.library.name)) {
            continue
          }

          Set<String> libraryFiles = getLibraryFiles(dependencyElement.library)
          for (String fileName in libraryFiles) {
            if (!fileName.endsWith('.class') || fileName.endsWith("Kt.class")) {
              return
            }

            classes.add(removeSuffixStrict(fileName, ".class").replace("/", "."))
          }
        }
      }
    }

    buildContext.messages.info("Found ${classes.size()} classes in ${moduleNames.size()} modules and ${visitedLibraries.size()} libraries")

    for (descriptor in descriptors) {
      def xml = new XmlParser().parse(descriptor)
      validateXmlRegistrationsRec(descriptor.name, xml, classes)
    }
  }

  private static String removePrefixStrict(String string, String prefix) {
    if (prefix == null || prefix.isEmpty()) {
      throw new IllegalArgumentException("'prefix' is null or empty")
    }

    if (!string.startsWith(prefix)) {
      throw new IllegalStateException("String must start with '$prefix': $string")
    }

    return string.substring(prefix.length())
  }

  private static String removeSuffixStrict(String string, String suffix) {
    if (suffix == null || suffix.isEmpty()) {
      throw new IllegalArgumentException("'suffix' is null or empty")
    }

    if (!string.endsWith(suffix)) {
      throw new IllegalStateException("String must end with '$suffix': $string")
    }

    return string.substring(0, string.length() - suffix.length())
  }

  private void validateXmlRegistrationsRec(String source, Node xml, HashSet<String> classes) {
    for (attribute in xml.attributes()) {
      def name = attribute.key.toString()
      def value = attribute.value.toString()

      if (pathAttributes.contains(name)) {
        checkRegistration(source, value, classes)
        continue
      }
      if (nonPathAttributes.contains(name)) {
        continue
      }

      if (value.startsWith("com.") || value.startsWith("org.")) {
        buildContext.messages.warning(
          "Attribute '$name' contains qualified path '$value'. Add attribute into 'ModuleStructureValidator.pathAttributes' or 'ModuleStructureValidator.nonPathAttributes' collection.")
      }
    }

    for (child in xml.children()) {
      if (child instanceof Node) {
        Node node = (Node)child
        for (String pathElement in pathElements) {
          if (node.name() == pathElement) {
            checkRegistration(source, node.text(), classes)
          }
        }

        validateXmlRegistrationsRec(source, node, classes)
      }
    }
  }

  private void checkRegistration(String source, String value, HashSet<String> classes) {
    if (value.isEmpty() || classes.contains(value)) {
      return
    }
    errors.add(new AssertionError("Unresolved registration '$value' in $source".toString(), null))
  }
}

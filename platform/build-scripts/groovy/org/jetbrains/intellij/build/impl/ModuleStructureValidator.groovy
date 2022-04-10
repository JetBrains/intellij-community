// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.util.containers.MultiMap
import groovy.io.FileType
import groovy.transform.CompileStatic
import groovy.xml.QName
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.impl.JpsJavaDependencyExtensionRole
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency

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
  private List<String> errors = new ArrayList<>()

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

  List<String> validate() {
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
          errors.add("Missing dependency found: ${module.name} -> ${dependantModule.name} [${role.scope.name()}]".toString())
          continue
        }

        validateModuleDependencies(visitedModules, dependantModule)
      }
    }
  }

  private void validateXmlDescriptors() {
    List<File> roots = new ArrayList<File>()
    for (moduleName in moduleNames) {
      def module = buildContext.findModule(moduleName)
      for (root in module.sourceRoots) {
        roots.add(root.file)
      }
    }

    // Start validating from product xml descriptor
    String productDescriptorName = "META-INF/${buildContext.productProperties.platformPrefix}Plugin.xml"
    File productDescriptorFile = findDescriptorFile(productDescriptorName, roots)
    if (productDescriptorFile == null) {
      errors.add("Can not find product descriptor $productDescriptorName".toString())
      return
    }

    HashSet<File> allDescriptors = new HashSet<File>()
    validateXmlDescriptorsRec(productDescriptorFile, roots, allDescriptors)
    validateXmlRegistrations(allDescriptors)
  }

  private void validateXmlDescriptorsRec(File descriptor, List<File> roots, HashSet<File> allDescriptors) {

    allDescriptors.add(descriptor)

    def descriptorFiles = new ArrayList<File>()
    def xml = new XmlParser().parse(descriptor)

    def includeNodes = xml.depthFirst().findAll { it instanceof Node && ((Node)it).name() == includeName }
    for (includeNode in includeNodes) {
      def ref = ((Node)includeNode).attribute("href").toString()
      if (ref == null) continue

      def descriptorFile = findDescriptorFile(ref, roots + descriptor.parentFile)
      if (descriptorFile == null) {
        def isOptional = (((Node)includeNode).children().any { it instanceof Node && ((Node)it).name() == fallbackName })
        if (isOptional) {
          buildContext.messages.info("Ignore optional missing xml descriptor '$ref' referenced in '${descriptor.name}'")
        }
        else {
          errors.add("Can not find xml descriptor '$ref' referenced in '${descriptor.name}'".toString())
        }
      }
      else {
        descriptorFiles.add(descriptorFile)
      }
    }

    for (descriptorFile in descriptorFiles) {
      validateXmlDescriptorsRec(descriptorFile, roots, allDescriptors)
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
    for (moduleName in moduleNames) {
      def outputDirectory = JpsJavaExtensionService.instance.getOutputDirectory(buildContext.findModule(moduleName), false)
      def outputDirectoryPrefix = outputDirectory.path.replace("\\", "/") + "/"
      outputDirectory.eachFileRecurse(FileType.FILES) {
        if (!it.name.endsWith('.class') || it.name.endsWith("Kt.class")) {
          return
        }

        def normalizedPath = it.path.replace("\\", "/")
        def className = removeSuffixStrict(removePrefixStrict(normalizedPath, outputDirectoryPrefix), ".class").replace("/", ".")
        classes.add(className)
      }
    }

    buildContext.messages.info("Found ${classes.size()} classes in ${moduleNames.size()} modules")
    for (descriptor in descriptors) {
      def xml = new XmlParser().parse(descriptor)
      validateXmlRegistrationsRec(descriptor.name, xml, classes)
    }
  }

  private String removePrefixStrict(String string, String prefix) {
    if (prefix == null || prefix.isEmpty()) {
      throw new IllegalArgumentException("'prefix' is null or empty")
    }

    if (!string.startsWith(prefix)) {
      throw new IllegalStateException("String must start with '$prefix': $string")
    }

    return string.substring(prefix.length())
  }

  private String removeSuffixStrict(String string, String suffix) {
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
    errors.add("Unresolved registration '$value' in $source".toString())
  }
}

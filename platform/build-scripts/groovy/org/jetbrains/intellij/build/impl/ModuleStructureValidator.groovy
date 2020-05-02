// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.io.FileType
import groovy.transform.CompileStatic
import groovy.xml.QName
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.impl.JpsJavaDependencyExtensionRole
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency

@CompileStatic
class ModuleStructureValidator {

  private static QName includeName = new QName("http://www.w3.org/2001/XInclude", "include")
  private static QName fallbackName = new QName("http://www.w3.org/2001/XInclude", "fallback")

  private static HashSet<String> pathAttributes = new HashSet<String>([
    "interface", "implementation", "class", "topic", "instance", "provider",
    "implements", "headlessImplementation", "serviceInterface", "serviceImplementation",
    "implementationClass", "beanClass", "schemeClass", "factoryClass", "handlerClass",
    "forClass", "className", "predicateClassName", "displayNameSupplierClassName", "preloaderClassName",
    "treeRenderer"])

  private static HashSet<String> nonPathAttributes = new HashSet<String>([
    "id", "value", "key", "testServiceImplementation", "defaultExtensionNs", "qualifiedName", "childrenEPName"])

  private static HashSet<String> pathElements = new HashSet<String>(["interface-class", "implementation-class"])
  private static HashSet<String> predefinedTypes = new HashSet<String>(["java.lang.Object"])

  private BuildContext buildContext
  private HashSet<String> moduleNames
  private ArrayList<GString> errors
  private ArrayList<GString> warnings

  ModuleStructureValidator(BuildContext buildContext, HashSet<String> moduleNames) {
    this.buildContext = buildContext
    this.moduleNames = moduleNames
    this.errors = new ArrayList<>()
    this.warnings = new ArrayList<>()
  }

  void validate() {
    buildContext.messages.info("Validating modules...")
    def visitedModules = new HashSet<JpsModule>()
    for (moduleName in moduleNames) {
      validateModuleDependencies(visitedModules, buildContext.findModule(moduleName))
    }

    buildContext.messages.info("Validating xml descriptors...")
    validateXmlDescriptors()

    if (warnings.isEmpty() && errors.isEmpty()) {
      buildContext.messages.info("Validation finished successfully")
    }
    else {
      if (warnings.any()) {
        buildContext.messages.warning("Validation warnings: \n" + warnings.join("\n"))
      }
      if (errors.any()) {
        buildContext.messages.warning("Validation errors: \n" + errors.join("\n"))
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

        // Skip localization modules
        def dependantModule = ((JpsModuleDependency)dependency).module
        if (dependantModule.name.endsWith("resources.en")) {
          continue
        }

        if (!moduleNames.contains(dependantModule.name)) {
          errors.add("Missing dependency found: ${module.name} -> ${dependantModule.name} [${role.scope.name()}]")
          continue
        }

        validateModuleDependencies(visitedModules, dependantModule)
      }
    }
  }

  private void validateXmlDescriptors() {
    def roots = new ArrayList<File>()
    for (moduleName in moduleNames) {
      def module = buildContext.findModule(moduleName)
      for (root in module.sourceRoots) {
        roots.add(root.file)
      }
    }

    // Start validating from product xml descriptor
    def productDescriptorName = "META-INF\\${buildContext.productProperties.platformPrefix}Plugin.xml"
    def productDescriptorFile = findDescriptorFile(productDescriptorName, roots)
    if (productDescriptorFile == null) {
      errors.add("Can not find product descriptor $productDescriptorName")
      return
    }

    def allDescriptors = new HashSet<File>()
    validateXmlDescriptorsRec(productDescriptorFile, roots, allDescriptors)
    validateXmlRegistrations(allDescriptors)
  }

  private void validateXmlDescriptorsRec(File descriptor, ArrayList<File> roots, HashSet<File> allDescriptors) {

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
          warnings.add("Can not find optional xml descriptor '$ref' referenced in '${descriptor.name}'")
        }
        else {
          errors.add("Can not find xml descriptor '$ref' referenced in '${descriptor.name}'")
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
    def classes = new HashSet<String>(predefinedTypes.collect())
    for (moduleName in moduleNames) {
      def outputDirectory = JpsJavaExtensionService.instance.getOutputDirectory(buildContext.findModule(moduleName), false)
      outputDirectory.eachFileRecurse(FileType.FILES) {
        if (!it.name.endsWith('.class') || it.name.endsWith("Kt.class")) {
          return
        }

        def className = it.path
          .replace(outputDirectory.path + "\\", "")
          .replace(".class", "")
          .replace("\\", ".")
        classes.add(className)
      }
    }

    buildContext.messages.info("Found ${classes.size()} classes in ${moduleNames.size()} modules")
    for (descriptor in descriptors) {
      def xml = new XmlParser().parse(descriptor)
      validateXmlRegistrationsRec(descriptor.name, xml, classes)
    }
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
        warnings.add("Attribute '$name' contains qualified path '$value'. Add attribute into 'ModuleStructureValidator.pathAttributes' or 'ModuleStructureValidator.nonPathAttributes' collection.")
      }
    }

    for (child in xml.children()) {
      if (child instanceof Node) {
        for (pathElement in pathElements) {
          if (!(child.name() == pathElement)) continue
          checkRegistration(source, child.text(), classes)
        }

        validateXmlRegistrationsRec(source, (Node)child, classes)
      }
    }
  }

  private void checkRegistration(String source, String value, HashSet<String> classes) {
    if (value.isEmpty()) return
    if (classes.contains(value)) return
    errors.add("Unresolved registration '$value' in $source")
  }
}

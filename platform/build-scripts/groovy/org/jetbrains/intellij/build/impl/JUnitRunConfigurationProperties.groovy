/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessages

@CompileStatic
class JUnitRunConfigurationProperties {
  final String name
  final String moduleName
  final List<String> testClassPatterns
  final List<String> vmParameters
  final List<String> requiredArtifacts

  static JUnitRunConfigurationProperties findRunConfiguration(String projectHome, String name, BuildMessages messages) {
    def file = new File(projectHome, ".idea/runConfigurations/${FileUtil.sanitizeFileName(name)}.xml")
    if (!file.exists()) {
      messages.error("Cannot find run configurations: $file doesn't exist")
    }

    loadRunConfiguration(file, messages)
  }

  @SuppressWarnings(["GrUnresolvedAccess", "GroovyAssignabilityCheck"])
  @CompileDynamic
  static JUnitRunConfigurationProperties loadRunConfiguration(File file, BuildMessages messages) {
    def root = new XmlParser().parse(file)
    def configuration = first(root.configuration)
    if (configuration == null) {
      messages.error("Cannot load configuration from '$file.name': 'configuration' tag is not found")
    }
    if (configuration.@type != "JUnit") {
      messages.error("Cannot load configuration from '$file.name': only JUnit run configuration are supported")
    }

    String moduleName = first(configuration.module)?.@name
    if (moduleName == null) {
      messages.error("Cannot run configuration from '$file.name': module name is not specified")
    }
    Map<String, String> options = configuration.option?.collectEntries { [it.@name, it.@value] }
    def testKind = options["TEST_OBJECT"]
    List<String> testClassPatterns
    if (testKind == "class") {
      testClassPatterns = [options["MAIN_CLASS_NAME"]]
    }
    else if (testKind == "package") {
      testClassPatterns = [options["PACKAGE_NAME"] + ".*"]
    }
    else if (testKind == "pattern") {
      testClassPatterns = first(configuration?.patterns)?.pattern?.collect { it.@testClass }
    }
    else {
      messages.error("Cannot run $file.name configuration: '${testKind}' test kind is not supported")
      testClassPatterns = []
    }

    List<String> requiredArtifacts =
      first(configuration.method)?.option?.
            find { it.@name == "BuildArtifacts" && it.@enabled == "true" }?.
            artifact?.collect { it.@name } ?: []

    def vmParameters = options["VM_PARAMETERS"].tokenize()
    return new JUnitRunConfigurationProperties(configuration.@name, moduleName, testClassPatterns, vmParameters, requiredArtifacts)
  }

  private static <T> T first(Collection<T> collection) {
    collection == null || collection.isEmpty() ? null : collection.first()
  }

  JUnitRunConfigurationProperties(String name, String moduleName, List<String> testClassPatterns, List<String> vmParameters, List<String> requiredArtifacts) {
    this.name = name
    this.moduleName = moduleName
    this.testClassPatterns = testClassPatterns
    this.requiredArtifacts = requiredArtifacts
    this.vmParameters = vmParameters
  }
}

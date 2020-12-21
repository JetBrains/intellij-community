// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessages

@CompileStatic
final class JUnitRunConfigurationProperties extends RunConfigurationProperties {
  final List<String> testClassPatterns
  final List<String> requiredArtifacts

  @SuppressWarnings(["GrUnresolvedAccess", "GroovyAssignabilityCheck"])
  @CompileDynamic
  static JUnitRunConfigurationProperties loadRunConfiguration(File file, BuildMessages messages) {
    def configuration = getConfiguration(file, messages)

    if (configuration.@type != "JUnit") {
      messages.error("Cannot load configuration from '$file.name': only JUnit run configuration are supported")
    }

    String moduleName = first(configuration.module)?.@name
    if (moduleName == null) {
      messages.error("Cannot run configuration from '$file.name': module name is not specified")
    }
    Map<String, String> options = configuration.option?.collectEntries { [it.@name, it.@value] }
    def testKind = options["TEST_OBJECT"] ?: "class"
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

    String forkMode = first(configuration.fork_mode)?.@value
    if (forkMode != null && forkMode != "none") {
      messages.error("Cannot run $file.name configuration: fork mode '$forkMode' is not supported")
    }

    List<String> requiredArtifacts =
      first(configuration.method)?.option?.
            find { it.@name == "BuildArtifacts" && it.@enabled == "true" }?.
            artifact?.collect { it.@name } ?: []

    def vmParameters = (options["VM_PARAMETERS"] ?: "-ea").tokenize() +
                       // Pattern is a regex already, we don't need to escape it in com.intellij.TestClassesFilter
                       ("pattern" == testKind ? ["-Dintellij.build.test.patterns.escaped=true"] : [])
    def envVariables = first(configuration.envs)?.env?.collectEntries { [it.@name, it.@value] } ?: [:]
    return new JUnitRunConfigurationProperties(configuration.@name, moduleName, testClassPatterns, vmParameters, requiredArtifacts, envVariables)
  }

  JUnitRunConfigurationProperties(String name,
                                  String moduleName,
                                  List<String> testClassPatterns,
                                  List<String> vmParameters,
                                  List<String> requiredArtifacts,
                                  Map<String, String> envVariables) {
    super(name, moduleName, vmParameters, envVariables)
    this.testClassPatterns = testClassPatterns
    this.requiredArtifacts = requiredArtifacts
  }
}

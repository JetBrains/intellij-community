// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessages


/**
 * @author Aleksey.Rostovskiy
 */
@CompileStatic
class KotlinRunConfigurationProperties extends RunConfigurationProperties {
  final String mainClassName

  KotlinRunConfigurationProperties(String name,
                                   String moduleName,
                                   String mainClassName,
                                   List<String> vmParameters,
                                   Map<String, String> envVariables) {
    super(name, moduleName, vmParameters, envVariables)
    this.mainClassName = mainClassName
  }

  @SuppressWarnings(["GrUnresolvedAccess", "GroovyAssignabilityCheck"])
  @CompileDynamic
  static KotlinRunConfigurationProperties loadRunConfiguration(File file, BuildMessages messages) {
    def configuration = getConfiguration(file, messages)

    if (configuration.@type != "JetRunConfigurationType" && configuration.@factoryName != "Kotlin") {
      messages.error("Cannot load configuration from '$file.name': only Kotlin run configuration are supported")
    }

    String moduleName = first(configuration.module)?.@name
    if (moduleName == null) {
      messages.error("Cannot run configuration from '$file.name': module name is not specified")
    }

    Map<String, String> options = configuration.option?.collectEntries { [it.@name, it.@value] }
    String mainClassName = options["MAIN_CLASS_NAME"]
    List<String> vmParameters = options["VM_PARAMETERS"].tokenize()
    def envVariables = first(configuration.envs)?.env?.collectEntries { [it.@name, it.@value] } ?: [:]

    return new KotlinRunConfigurationProperties(configuration.@name, moduleName, mainClassName, vmParameters, envVariables)
  }

}

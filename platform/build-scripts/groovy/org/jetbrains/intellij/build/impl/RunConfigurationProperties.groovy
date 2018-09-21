// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessages


/**
 * @author Aleksey.Rostovskiy
 */
@CompileStatic
abstract class RunConfigurationProperties {
  final String name
  final String moduleName
  final List<String> vmParameters
  final Map<String, String> envVariables

  RunConfigurationProperties(String name,
                             String moduleName,
                             List<String> vmParameters,
                             Map<String, String> envVariables) {
    this.name = name
    this.moduleName = moduleName
    this.vmParameters = vmParameters
    this.envVariables = envVariables
  }

  static File findRunConfiguration(String projectHome, String name, BuildMessages messages) {
    def file = new File(projectHome, ".idea/runConfigurations/${FileUtil.sanitizeFileName(name)}.xml")
    if (!file.exists()) {
      messages.error("Cannot find run configurations: $file doesn't exist")
    }

    return file
  }

  @SuppressWarnings(["GrUnresolvedAccess", "GroovyAssignabilityCheck"])
  @CompileDynamic
  protected static getConfiguration(File file, BuildMessages messages) {
    Node root = new XmlParser().parse(file)
    def configuration = first(root.configuration)
    if (configuration == null) {
      messages.error("Cannot load configuration from '$file.name': 'configuration' tag is not found")
    }

    return configuration
  }

  protected static <T> T first(Collection<T> collection) {
    collection == null || collection.isEmpty() ? null : collection.first()
  }
}

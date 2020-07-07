// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildContext

@CompileStatic
class DependenciesProperties {
  private final BuildContext context
  private final File directory
  private final File propertiesFile

  DependenciesProperties(BuildContext context) {
    this.context = context
    this.directory = new File(context.paths.communityHome, 'build/dependencies')
    this.propertiesFile = new File(directory, 'build/dependencies.properties')
  }

  @Lazy
  File file = {
    if (props.isEmpty()) throw new IllegalStateException('Dependencies properties are empty')
    propertiesFile
  }()

  @Lazy
  private Properties props = {
    synchronized (DependenciesProperties.class) {
      context.gradle.run('Preparing dependencies file', 'dependenciesFile')
      propertiesFile.newInputStream().withStream {
        Properties properties = new Properties()
        properties.load(it)
        properties
      }
    }
  }()

  String property(String name) {
    def value = props.get(name)
    if (value == null) {
      context.messages.error("`$name` is not defined in `$directory/gradle.properties`")
    }
    return value
  }
}

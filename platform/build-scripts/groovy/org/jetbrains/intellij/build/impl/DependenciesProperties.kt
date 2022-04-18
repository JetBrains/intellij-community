// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.CompilationContext

import java.nio.file.Files
import java.nio.file.Path

@CompileStatic
final class DependenciesProperties {
  private final CompilationContext context
  private final Path directory
  private final Path propertiesFile

  DependenciesProperties(CompilationContext context) {
    this.context = context
    this.directory = context.paths.communityHomeDir.resolve("build/dependencies")
    this.propertiesFile = directory.resolve("gradle.properties")
  }

  @Lazy
  Path file = {
    if (props.isEmpty()) {
      throw new IllegalStateException("Dependencies properties are empty")
    }
    propertiesFile
  }()

  @Lazy
  private Properties props = {
    Files.newInputStream(propertiesFile).withCloseable {
      Properties properties = new Properties()
      properties.load(it)
      properties
    }
  }()

  String property(String name) {
    def value = props.get(name)
    if (value == null) {
      context.messages.error("`$name` is not defined in `$propertiesFile`")
    }
    return value
  }

  String propertyOrNull(String name) {
    def value = props.get(name)
    if (value == null) {
      context.messages.warning("`$name` is not defined in `$propertiesFile`")
    }
    return value
  }
}

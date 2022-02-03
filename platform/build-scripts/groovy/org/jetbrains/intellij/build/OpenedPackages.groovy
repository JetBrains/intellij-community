// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic

@CompileStatic
final class OpenedPackages {

  /**
   * @return List of JVM args for opened packages (JBR17+) in a format `--add-opens=PACKAGE=ALL-UNNAMED`
   * */
  static final List<String> getCommandLineArguments(CompilationContext compilationContext) {
    return compilationContext.paths.communityHomeDir
      .resolve("platform/build-scripts/resources/OpenedPackages.txt")
      .readLines()
      .stream()
      .collect { "--add-opens=${it.toString()}".toString() }
  }
}

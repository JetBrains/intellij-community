// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import java.nio.file.Files

object OpenedPackages {
  /**
   * @return List of JVM args for opened packages (JBR17+) in a format `--add-opens=PACKAGE=ALL-UNNAMED`
   */
  @JvmStatic
  fun getCommandLineArguments(context: CompilationContext): List<String> {
    return Files.readAllLines(context.paths.communityHomeDir.resolve("plugins/devkit/devkit-core/src/run/OpenedPackages.txt"))
  }
}

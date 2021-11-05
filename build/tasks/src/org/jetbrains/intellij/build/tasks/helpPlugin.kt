// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.tasks

import org.jetbrains.intellij.build.io.ZipArchiver
import org.jetbrains.intellij.build.io.compressDir
import org.jetbrains.intellij.build.io.runJava
import org.jetbrains.intellij.build.io.writeNewZip
import java.lang.System.Logger
import java.nio.file.Path
import java.util.*

fun buildResourcesForHelpPlugin(resourceRoot: Path, classPath: List<String>, assetJar: Path, logger: Logger) {
  tracer.spanBuilder("index help topics").startSpan().useWithScope {
    runJava(mainClass = "com.jetbrains.builtInHelp.indexer.HelpIndexer",
            args = listOf(resourceRoot.resolve("search").toString(), resourceRoot.resolve("topics").toString()),
            jvmArgs = Collections.emptyList(),
            classPath = classPath,
            logger = logger)

    writeNewZip(assetJar, compress = true) { zipCreator ->
      val archiver = ZipArchiver(zipCreator)
      archiver.setRootDir(resourceRoot)
      compressDir(resourceRoot.resolve("topics"), archiver)
      compressDir(resourceRoot.resolve("images"), archiver)
      compressDir(resourceRoot.resolve("search"), archiver)
    }
  }
}
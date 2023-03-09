// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import org.jetbrains.jps.model.serialization.PathMacroUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal fun searchTestRoots(project: String): Set<Path> {
  return try {
    jpsProject(project)
      .modules.flatMap {
        it.getSourceRoots(JavaSourceRootType.TEST_SOURCE) +
        it.getSourceRoots(JavaResourceRootType.TEST_RESOURCE)
      }.mapTo(mutableSetOf()) { it.file.toPath() }
  }
  catch (e: IOException) {
    System.err.println(e.message)
    emptySet()
  }
}

internal fun jpsProject(path: String): JpsProject {
  val file = Paths.get(FileUtil.toCanonicalPath(path))
  return when {
    path.endsWith(".ipr") ||
    Files.isDirectory(file.resolve(PathMacroUtil.DIRECTORY_STORE_NAME)) ||
    Files.isDirectory(file) && file.endsWith(PathMacroUtil.DIRECTORY_STORE_NAME) -> {
      JpsSerializationManager.getInstance().loadModel(path, null).project
    }
    file.parent == null -> error("Jps project isn't found at $file")
    else -> jpsProject(file.parent.toString())
  }
}
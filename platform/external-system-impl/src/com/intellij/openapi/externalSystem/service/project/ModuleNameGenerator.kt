// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project

import com.intellij.openapi.externalSystem.model.project.ModuleData
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.Path
import kotlin.io.path.pathString

@ApiStatus.Internal
object ModuleNameGenerator {

  @JvmStatic
  fun generate(moduleData: ModuleData, delimiter: String): Iterable<String> {
    var modulePath = File(moduleData.linkedExternalProjectPath)
    if (modulePath.isFile) {
      modulePath = modulePath.parentFile
    }
    return generate(moduleData.group, moduleData.internalName, modulePath.toPath(), delimiter)
  }

  @JvmStatic
  fun generate(group: String?, name: String, path: Path, delimiter: String): Iterable<String> {
    val simpleNameGenerator = SimpleNameGenerator(group, name, delimiter)
    val pathNameGenerator = PathNameGenerator(name, path, delimiter)
    val numericNameGenerator = NumericNameGenerator(name)
    return simpleNameGenerator.generate() + pathNameGenerator.generate() + numericNameGenerator.generate()
  }
}

private class SimpleNameGenerator(
  private val group: String?,
  private val name: String,
  private val delimiter: String
) {

  fun generate(): Iterable<String> {
    val names = ArrayList<String>()
    names.add(name)
    if (group != null && !name.startsWith(group)) {
      names.add(group + delimiter + name)
    }
    return names
  }
}

private class PathNameGenerator(
  private val name: String,
  private val path: Path,
  private val delimiter: String
) {

  fun generate(): Iterable<String> {
    val names = ArrayList<String>()
    val pathParts = path.map { it.pathString }
    val nameBuilder = StringBuilder()
    var duplicateCandidate = name
    var i = pathParts.size - 1
    var j = 0
    while (i >= 0 && j < MAX_FILE_DEPTH) {
      val part = pathParts[i]

      // do not add prefix which was already included into the name (e.g. as a result of deduplication on the external system side)
      var isAlreadyIncluded = false
      if (duplicateCandidate.isNotEmpty()) {
        if (duplicateCandidate == part ||
            duplicateCandidate.endsWith(delimiter + part) ||
            duplicateCandidate.endsWith("_$part")
        ) {
          j--
          duplicateCandidate = duplicateCandidate.removeSuffix(part).removeSuffix(delimiter).removeSuffix("_")
          isAlreadyIncluded = true
        }
        else if (name.startsWith(part) || (i > 1 && name.startsWith(pathParts[i - 1] + delimiter + part))) {
          j--
          isAlreadyIncluded = true
        }
        else {
          duplicateCandidate = ""
        }
      }
      if (!isAlreadyIncluded) {
        nameBuilder.insert(0, part + delimiter)
        names.add(nameBuilder.toString() + name)
      }
      i--
      j++
    }
    return names
  }

  companion object {
    private const val MAX_FILE_DEPTH = 3
  }
}

private class NumericNameGenerator(
  private val name: String
) {

  fun generate(): Iterable<String> {
    return Iterable {
      object : Iterator<String> {

        private var current: Int = 0

        override fun hasNext(): Boolean {
          return current < MAX_NUMBER_SEQ
        }

        override fun next(): String {
          current++
          return "$name~$current"
        }
      }
    }
  }

  companion object {
    private const val MAX_NUMBER_SEQ = 2
  }
}

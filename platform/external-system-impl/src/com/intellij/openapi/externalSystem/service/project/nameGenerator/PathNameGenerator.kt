// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.nameGenerator

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.pathString

@ApiStatus.Internal
class PathNameGenerator private constructor(
  private val name: String,
  private val path: Path,
  private val delimiter: String,
  private val maxFileDepth: Int
) {

  fun generate(): Iterable<String> {
    val names = ArrayList<String>()
    val pathParts = path.map { it.pathString }
    val nameBuilder = StringBuilder()
    var duplicateCandidate = name
    var i = pathParts.size - 1
    var j = 0
    while (i >= 0 && j < maxFileDepth) {
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

    @JvmStatic
    fun generate(name: String, path: Path, delimiter: String, maxFileDepth: Int = MAX_FILE_DEPTH): Iterable<String> {
      return PathNameGenerator(name, path, delimiter, maxFileDepth).generate()
    }
  }
}
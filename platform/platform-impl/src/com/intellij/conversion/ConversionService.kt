// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.conversion

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import java.nio.file.Path

abstract class ConversionService {
  abstract fun convertSilently(projectPath: Path, conversionListener: ConversionListener): ConversionResult

  @Throws(CannotConvertException::class)
  abstract suspend fun convert(projectPath: Path): ConversionResult

  abstract fun convertModule(project: Project, moduleFile: Path): ConversionResult

  abstract fun saveConversionResult(projectPath: Path)

  companion object {
    @JvmStatic
    fun getInstance(): ConversionService? = serviceOrNull<ConversionService>()
  }
}

interface ConversionResult {
  fun conversionNotNeeded(): Boolean

  fun openingIsCanceled(): Boolean

  fun postStartupActivity(project: Project)
}


class CannotConvertException : RuntimeException {
  @JvmOverloads
  constructor(message: String, cause: Throwable? = null) : super(message, cause)
  constructor(cause: Throwable) : super(cause)
}

interface ConversionListener {
  fun conversionNeeded()

  fun successfullyConverted(backupDir: Path) {
  }

  fun error(message: String)

  fun cannotWriteToFiles(readonlyFiles: List<Path>) {
  }
}

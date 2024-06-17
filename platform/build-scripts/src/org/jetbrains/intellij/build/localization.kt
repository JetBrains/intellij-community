// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path

@ApiStatus.Internal
fun getLocalizationDir(context: BuildContext): Path? {
  val localizationDir = context.paths.communityHomeDir.parent.resolve("localization")
  if (Files.notExists(localizationDir)) {
    Span.current().addEvent("unable to find 'localization' directory, skip localization bundling")
    return null
  }
  return localizationDir
}
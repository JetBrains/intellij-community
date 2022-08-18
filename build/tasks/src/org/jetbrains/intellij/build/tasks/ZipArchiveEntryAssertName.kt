// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.tasks

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry

class ZipArchiveEntryAssertName(name: String): ZipArchiveEntry(name) {
  init {
    assertRelativePathIsCorrectForPackaging(name)
  }
}
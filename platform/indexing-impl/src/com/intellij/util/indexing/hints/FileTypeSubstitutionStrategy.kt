// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.hints

import com.intellij.util.indexing.SubstitutedFileType

/**
 * [SubstitutedFileType] is an error-prone way to use filetype. Effectively it means that some file has two file types:
 * before substitution ([SubstitutedFileType.getOriginalFileType]) and after substitution ([SubstitutedFileType.getFileType]).
 *
 * Client code often does not expect that it needs to deal with some artificial [SubstitutedFileType] that is not equal to
 * any real filetype (e.g. JavaFileType). To simplify API, some classes (e.g. [BaseFileTypeInputFilter]) resolve
 * [SubstitutedFileType] to real filetype. [FileTypeSubstitutionStrategy] defines the resolution strategy.
 */
enum class FileTypeSubstitutionStrategy {
  BEFORE_SUBSTITUTION,
  AFTER_SUBSTITUTION
}
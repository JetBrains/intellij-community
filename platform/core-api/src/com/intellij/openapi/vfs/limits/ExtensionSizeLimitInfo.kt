// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.limits

import org.jetbrains.annotations.ApiStatus

/** @see FileSizeLimit */
@ApiStatus.Internal
data class ExtensionSizeLimitInfo(
  /**
   * Don't load the file content if larger.
   * Assumed to be the largest/least restrictive of all 3 limits.
   */
  val content: Int? = null,
  /**
   * Don't parse/analyze the file content, if larger.
   * Also used by the indexing engine, to limit (with some tweaks) file size to index.
   */
  val intellijSense: Int? = null,
  /**
   * Don't show the full file content in the editor if the content is larger.
   * (editor may show a truncated read-only view of the file)
   */
  val preview: Int? = null,
)
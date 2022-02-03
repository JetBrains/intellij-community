// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation

import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
data class DocumentationData internal constructor(
  val html: @Nls String,
  val anchor: String?,
  val externalUrl: String?,
  val linkUrls: List<String>,
  val imageResolver: DocumentationImageResolver?
) : DocumentationResult

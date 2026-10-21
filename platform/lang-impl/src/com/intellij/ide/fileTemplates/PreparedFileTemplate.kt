// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates

import com.intellij.psi.PsiDirectory
import org.jetbrains.annotations.ApiStatus

/**
 * File template merged with its properties by [FileTemplateUtil.prepareFromTemplate].
 */
@ApiStatus.Experimental
class PreparedFileTemplate internal constructor(
  val template: FileTemplate,
  val handler: CreateFromTemplateHandler,
  val directory: PsiDirectory,
  /**
   * Name of the file without the template extension; `null` when the handler does not require one and none was given.
   */
  val fileName: String?,
  /**
   * Template text merged with [properties], with line separators converted to `\n`.
   */
  val text: String,
  /**
   * Properties the template was merged with, including the ones the providers added.
   */
  val properties: Map<String, Any>,
)

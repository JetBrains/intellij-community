// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.codeStyle

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * The `PostFormatPopupCustomization` extension allows customizing
 * the appearance of the popup displayed after code formatting.
 */
@ApiStatus.Internal
interface PostFormatPopupCustomization {
  companion object {
    @JvmStatic
    val EP_NAME: ExtensionPointName<PostFormatPopupCustomization> =
      create<PostFormatPopupCustomization>("com.intellij.postFormatPopupCustomization")
  }

  /**
   * Should return an HTML string containing the footer message.
   */
  @Nls
  fun getPopupFooterMessage(file: PsiFile, project: Project): String

  /**
   * Called when a hyperlink in the popup footer is clicked.
   */
  fun handleFooterHyperlinkClick(file: PsiFile, project: Project)

  /**
   * Determines whether a formatter is suitable for the specific file.
   */
  fun isApplicableFor(file: PsiFile, project: Project): Boolean = false
}

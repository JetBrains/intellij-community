// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.completion

import com.intellij.openapi.util.NlsSafe
import javax.swing.JComponent

/**
 * Text completion contributor for swing text components like [com.intellij.ui.components.JBTextField].
 *
 * Note: If you want to add code completion for an editor then use [com.intellij.codeInsight.completion.CompletionContributor],
 * if file path completion for a text field then use [com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl.installFileCompletion]
 * @see TextCompletionPopup
 */
interface TextCompletionContributor<C : JComponent> {

  fun getTextToComplete(owner: C): @NlsSafe String

  fun getCompletionVariants(owner: C, textToComplete: String): Iterable<TextCompletionInfo>

  fun whenVariantChosen(action: (C, TextCompletionInfo) -> Unit)

  fun fireVariantChosen(owner: C, variant: TextCompletionInfo)
}
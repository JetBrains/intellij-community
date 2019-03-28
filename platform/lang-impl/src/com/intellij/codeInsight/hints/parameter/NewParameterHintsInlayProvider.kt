// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.parameter

import com.intellij.codeInsight.hints.*
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile

class NewParameterHintsInlayProvider<T: Any>(val provider: NewParameterHintsProvider<T>) : InlayHintsProvider<ParameterHintsSettings<T>> {
  override val name: String
    get() = "Parameter hints"

  override fun getCollectorFor(file: PsiFile, editor: Editor, settings: ParameterHintsSettings<T>): InlayHintsCollector<ParameterHintsSettings<T>>? {
    if (DiffUtil.isDiffEditor(editor)) return null
    return NewParameterHintsCollector(key, provider, settings, editor, file, false)
  }

  override fun createSettings(): ParameterHintsSettings<T> {
    // TODO merge with base lists?
    return ParameterHintsSettings(provider.blackList?.defaultBlackList ?: emptySet(), provider.createSettings())
  }

  override val key: SettingsKey<ParameterHintsSettings<T>>
    get() = provider.settingsKey
  override val previewText: String?
    get() = provider.preview

  override fun createConfigurable(settings: ParameterHintsSettings<T>): ImmediateConfigurable = provider.createConfigurable(settings.providerSettings)
}

class ParameterHintsSettings<T>(val blackList: Set<String>, val providerSettings: T)


// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ConfigurableEP
import com.intellij.openapi.options.UnnamedConfigurable

/**
 * Allow to provide additional 'Editor | General' options
 */
internal class GeneralEditorOptionsProviderEP : ConfigurableEP<UnnamedConfigurable>(ApplicationManager.getApplication()) {
}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor

import com.intellij.openapi.options.ConfigurableEP
import com.intellij.openapi.options.UnnamedConfigurable
import org.jetbrains.annotations.ApiStatus

/**
 * Follow similar intention as EditorAppearanceConfigurable extension point, however this one allow to put settings closer to the beginning
 * of the section.
 * @see EditorAppearanceConfigurableEP
 */
@ApiStatus.Internal
class EditorAppearancePrioritizedConfigurableEP: ConfigurableEP<UnnamedConfigurable>()

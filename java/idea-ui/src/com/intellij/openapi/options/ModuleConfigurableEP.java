// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.openapi.module.Module;

/**
 * @deprecated use {@link com.intellij.openapi.roots.ui.configuration.ModuleConfigurationEditorProvider} instead
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated(forRemoval = true)
public final class ModuleConfigurableEP extends ConfigurableEP<Configurable> {
  public ModuleConfigurableEP(Module module) {
    super(module);
  }
}

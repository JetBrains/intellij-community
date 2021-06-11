// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.ApiStatus;

/**
 * @deprecated use {@link com.intellij.openapi.roots.ui.configuration.ModuleConfigurationEditorProvider} instead
 */
@ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
public final class ModuleConfigurableEP extends ConfigurableEP<Configurable> {
  public ModuleConfigurableEP(Module module) {
    super(module);
  }
}

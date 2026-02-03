// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author gregsh
 */
@Internal
public abstract class InternalModuleType<T extends ModuleBuilder> extends ModuleType<T> {
  protected InternalModuleType(@NotNull @NonNls String id) {
    super(id);
  }
}

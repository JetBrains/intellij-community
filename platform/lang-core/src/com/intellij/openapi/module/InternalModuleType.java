// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author gregsh
 */
public abstract class InternalModuleType<T extends ModuleBuilder> extends ModuleType<T> {
  protected InternalModuleType(@NotNull @NonNls String id) {
    super(id);
  }
}

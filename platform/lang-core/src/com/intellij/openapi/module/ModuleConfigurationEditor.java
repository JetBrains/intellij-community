// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.openapi.options.Configurable;

public interface ModuleConfigurationEditor extends Configurable {
  default void saveData() {
  }

  default void moduleStateChanged() {
  }

  ModuleConfigurationEditor[] EMPTY = new ModuleConfigurationEditor[0];
}

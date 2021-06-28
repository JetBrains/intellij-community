// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Consumer;

/**
 * Provides random name generation button to New Project wizards. Implemented by external plugins.
 */
public interface ModuleNameGenerator {
  ExtensionPointName<ModuleNameGenerator> EP_NAME = ExtensionPointName.create("com.intellij.moduleNameGenerator");

  @NotNull JComponent getUi(@NotNull Consumer<String> nameSetter);
}

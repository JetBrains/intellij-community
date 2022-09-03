// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Consumer;

/**
 * Provides random name generation button to New Project wizards. Implemented by external plugins.
 */
@ApiStatus.Experimental
public interface ModuleNameGenerator {
  ExtensionPointName<ModuleNameGenerator> EP_NAME = ExtensionPointName.create("com.intellij.moduleNameGenerator");

  /**
   * @param place      logical place such as module builder ID
   * @param nameSetter function that sets module name
   * @return UI component that will be shown near to module name field
   */
  @Nullable JComponent getUi(@Nullable String place, @NotNull Consumer<? super String> nameSetter);
}

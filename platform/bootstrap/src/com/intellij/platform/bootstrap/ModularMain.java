// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bootstrap;

import com.intellij.ide.plugins.PluginDescriptorLoadingStrategy;
import com.intellij.idea.Main;
import com.intellij.platform.runtime.repository.ProductModules;
import com.intellij.platform.runtime.repository.RuntimeModuleRepository;
import org.jetbrains.annotations.NotNull;

/**
 * The entry point for the modular loading scheme. {@link #main} method is called via reflection from {@link com.intellij.platform.runtime.loader.Loader}.  
 */
@SuppressWarnings("unused")
public final class ModularMain {
  @SuppressWarnings("ConfusingMainMethod")
  public static void main(@NotNull RuntimeModuleRepository moduleRepository, @NotNull ProductModules productModules, String @NotNull [] args) {
    //when this new way to load the platform will become default, strategy instance may be passed explicitly instead
    PluginDescriptorLoadingStrategy.setStrategy(new ModuleBasedPluginDescriptorLoadingStrategy(productModules));
    Main.main(args);
  }
}

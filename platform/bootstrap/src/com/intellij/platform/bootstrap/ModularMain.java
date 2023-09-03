// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bootstrap;

import com.intellij.ide.plugins.ProductLoadingStrategy;
import com.intellij.idea.Main;
import com.intellij.platform.runtime.repository.RuntimeModuleRepository;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * The entry point for the modular loading scheme. 
 * {@link #main} method is called via reflection from {@link com.intellij.platform.runtime.loader.IntellijLoader}.  
 */
@SuppressWarnings("unused")
public final class ModularMain {
  @SuppressWarnings("ConfusingMainMethod")
  public static void main(@NotNull RuntimeModuleRepository moduleRepository, String @NotNull [] args,
                          @NotNull ArrayList<Object> startupTimings, long startTimeUnixNano) {
    //when this new way to load the platform will become default, strategy instance may be passed explicitly instead
    ProductLoadingStrategy.setStrategy(new ModuleBasedProductLoadingStrategy(moduleRepository));
    Main.mainImpl(args, startupTimings, startTimeUnixNano);
  }
}

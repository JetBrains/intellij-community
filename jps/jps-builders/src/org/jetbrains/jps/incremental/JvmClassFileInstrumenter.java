// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * Implement this class in your implementation of {@link ModuleLevelBuilder} to indicate that it instruments *.class files.
 * The build engine will remember which instrumenters were applied and recompile affected modules when the set of enabled instrumenters 
 * or their versions changes.
 */
@ApiStatus.Experimental
public interface JvmClassFileInstrumenter {
  /**
   * Returns a unique ID of the instrumenter.
   */
  @NotNull String getId();

  /**
   * Returns {@code true} if instrumentation is enabled for the given module.
   * If the instrumenter was disabled when the module was built the previous time, and becomes enabled in the next build (or vice versa), 
   * the module will be rebuilt.
   * <p>
   * Note, that currently even if this method returns {@code false}, {@link ModuleLevelBuilder#build} will still be called, so its
   * implementation should also check the condition.
   * </p>
   */
  boolean isEnabled(@NotNull ProjectDescriptor projectDescriptor, @NotNull JpsModule module);

  /**
   * Returns the current version of the instrumenter. Version should be incremented if the logic of the instrumenter changes. 
   * If a module was processed by a different version of the instrumenter, it'll be rebuilt to apply the new logic.
   */
  int getVersion();
}

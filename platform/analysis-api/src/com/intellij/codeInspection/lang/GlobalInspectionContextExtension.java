// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.lang;

import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.Tools;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.List;


/**
 * An extension to perform activities before and after running inspections
 * (see {@link GlobalInspectionContext}).
 */
public interface GlobalInspectionContextExtension<T> {
  @NotNull
  Key<T> getID();

  /**
   * Executed before tools are initialized (i.e., LocalInspectionTool.initialize() is called), and tools implementing
   * {@link com.intellij.codeInspection.ex.PairedUnfairLocalInspectionTool} are instantiated.
   * Could be used to modify the list of used tools, to enable/disable unfair tools in the selected profile if necessary, etc.
   *
   * @param usedTools list of tools from the selected inspection profile before they are classified and initialized
   * @param context global inspection context
   */
  default void performPreInitToolsActivities(@NotNull List<Tools> usedTools,
                                             @NotNull GlobalInspectionContext context) {}

  void performPreRunActivities(@NotNull List<Tools> globalTools,
                               @NotNull List<Tools> localTools,
                               @NotNull GlobalInspectionContext context);
  void performPostRunActivities(@NotNull List<InspectionToolWrapper<?, ?>> inspections, @NotNull GlobalInspectionContext context);

  void cleanup();
}

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Allows to provide frame variables which are not contained in visible variables list obtained from the current position
 */
public interface FrameExtraVariablesProvider {
  ExtensionPointName<FrameExtraVariablesProvider> EP_NAME = ExtensionPointName.create("com.intellij.debugger.frameExtraVarsProvider");

  boolean isAvailable(@NotNull SourcePosition sourcePosition, @NotNull EvaluationContext evalContext);

  @NotNull
  Set<TextWithImports> collectVariables(@NotNull SourcePosition sourcePosition,
                                        @NotNull EvaluationContext evalContext,
                                        @NotNull Set<String> alreadyCollected);
}

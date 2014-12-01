/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.openapi.extensions.ExtensionPointName;

import java.util.Set;

/**
 * Nikolay.Tropin
 * 2014-11-27
 */
public interface FrameExtraVariablesProvider {
  ExtensionPointName<FrameExtraVariablesProvider> EP_NAME = ExtensionPointName.create("com.intellij.debugger.frameExtraVarsProvider");

  boolean isAvailable(SourcePosition sourcePosition, EvaluationContextImpl evalContext);

  Set<TextWithImports> collectVariables(SourcePosition sourcePosition, EvaluationContextImpl evalContext, Set<String> alreadyCollected);
}

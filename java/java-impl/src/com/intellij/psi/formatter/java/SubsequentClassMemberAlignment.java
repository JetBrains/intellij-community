/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.formatter.java;

import com.intellij.formatting.alignment.AlignmentStrategy;
import com.intellij.lang.ASTNode;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public class SubsequentClassMemberAlignment extends ChildAlignmentStrategyProvider {
  private final ChildAlignmentStrategyProvider myFieldsAligner;
  private final ChildAlignmentStrategyProvider myOneLineMethodsAligner;

  public SubsequentClassMemberAlignment(CommonCodeStyleSettings settings) {
    myFieldsAligner = new SubsequentFieldAligner(settings);
    myOneLineMethodsAligner = getSimpleMethodsAligner(settings);
  }

  private static ChildAlignmentStrategyProvider getSimpleMethodsAligner(CommonCodeStyleSettings settings) {
    if (settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE && settings.ALIGN_SUBSEQUENT_SIMPLE_METHODS) {
      return new SubsequentOneLineMethodsAligner();
    }
    return ChildAlignmentStrategyProvider.NULL_STRATEGY_PROVIDER;
  }

  @Override
  public AlignmentStrategy getNextChildStrategy(@NotNull ASTNode child) {
    AlignmentStrategy fieldInColumnsAlignment = myFieldsAligner.getNextChildStrategy(child);
    AlignmentStrategy oneLineMethodsAlignment = myOneLineMethodsAligner.getNextChildStrategy(child);
    if (fieldInColumnsAlignment != AlignmentStrategy.getNullStrategy()) {
      return fieldInColumnsAlignment;
    }
    return oneLineMethodsAlignment;
  }
  
}

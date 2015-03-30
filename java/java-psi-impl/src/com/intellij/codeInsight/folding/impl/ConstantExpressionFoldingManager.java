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
package com.intellij.codeInsight.folding.impl;

import com.intellij.lang.folding.NamedFoldingDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.openapi.util.text.StringUtil.*;
import static com.intellij.psi.impl.JavaConstantExpressionEvaluator.computeConstantExpression;

public class ConstantExpressionFoldingManager {
  public static final String CONSTANT_EXPRESSION_GROUP_NAME = "Constant expression";

  private static final Logger LOG = Logger.getInstance("#" + ConstantExpressionFoldingManager.class.getName());

  public static @NotNull Collection<NamedFoldingDescriptor> fold(@NotNull final PsiPolyadicExpression polyadicExpression) {
    try {
      Object replacement = computeConstantExpression(polyadicExpression, false);
      if (replacement != null) {
        final TextRange range = polyadicExpression.getTextRange();
        final FoldingGroup group = FoldingGroup.newGroup(CONSTANT_EXPRESSION_GROUP_NAME);
        if (replacement instanceof String) replacement = wrapWithDoubleQuote(replacement.toString());

        return Collections.singletonList(new NamedFoldingDescriptor(polyadicExpression,
                                                                    range.getStartOffset(), range.getEndOffset(), group,
                                                                    replacement.toString()));
      }
    }
    catch (final RuntimeException e) {
      LOG.warn(e);
    }
    return ContainerUtil.emptyList();
  }
}
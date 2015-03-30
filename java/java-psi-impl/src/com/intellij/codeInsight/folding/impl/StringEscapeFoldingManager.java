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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

public class StringEscapeFoldingManager {
  public static final String STRING_ESCAPE_GROUP_NAME = "String escape";
  public static final Pattern BACKSLASH_PATTERN = Pattern.compile(StringUtil.escapeBackSlashes("\\"), Pattern.LITERAL);

  private static final Logger LOG = Logger.getInstance("#" + StringEscapeFoldingManager.class.getName());
  private static final String REPLACEMENT = "⧵";  // REVERSE SOLIDUS OPERATOR

  public static @NotNull Collection<NamedFoldingDescriptor> fold(@NotNull final PsiLiteralExpression literalExpression) {
    try {
      if (isStringLiteral(literalExpression)) {
        final List<NamedFoldingDescriptor> results = ContainerUtil.newArrayList();

        final TextRange range = literalExpression.getTextRange();
        final FoldingGroup group = FoldingGroup.newGroup(STRING_ESCAPE_GROUP_NAME);

        int pos = 0;
        for (final Matcher matcher = BACKSLASH_PATTERN.matcher(literalExpression.getText()); matcher.find(pos); ) {
          results.add(new NamedFoldingDescriptor(literalExpression,
                                                 range.getStartOffset() + matcher.start(), range.getStartOffset() + matcher.end(), group,
                                                 REPLACEMENT));
          pos = matcher.end();
        }

        return results;
      }
    }
    catch (final RuntimeException e) {
      LOG.warn(e);
    }
    return ContainerUtil.emptyList();
  }

  @SuppressWarnings("ConstantConditions")
  protected static boolean isStringLiteral(@Nullable final PsiExpression expression) {
    return (expression instanceof PsiLiteralExpression)
           && expression.getType().equalsToText(JAVA_LANG_STRING);
  }
}
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.codeInsight.folding.impl.StringEscapeFoldingManager.isStringLiteral;
import static com.intellij.openapi.util.text.StringUtil.unquoteString;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static com.intellij.psi.impl.JavaConstantExpressionEvaluator.computeConstantExpression;

public class StringFormatFoldingManager {
  public static final Pattern STRING_FORMAT_PATTERN = ReflectionUtil.getField(Formatter.class, null, Pattern.class, "fsPattern");
  public static final String STRING_FORMAT_GROUP_NAME = "String.format";

  private static final String FOLDED_STRING_START = "‘", FOLDED_STRING_END = "’";
  private static final String THIN_SPACE = " ";
  private static final String STRING_FORMAT_NAME = "format";
  private static final Logger LOG = Logger.getInstance("#" + StringFormatFoldingManager.class.getName());

  public static @NotNull Collection<NamedFoldingDescriptor> fold(@NotNull final PsiMethodCallExpression callExpression) {
    return isStringFormatMethod(callExpression) ? getFolds(callExpression)
                                                : ContainerUtil.<NamedFoldingDescriptor>emptyList();
  }

  private static boolean isStringFormatMethod(@NotNull final PsiMethodCallExpression callExpression) {
    final PsiMethod method = callExpression.resolveMethod();
    if (method != null) {
      PsiElement context = method.getContext();
      if (context instanceof ClsClassImpl) {
        String qualifiedName = ((ClsClassImpl)context).getQualifiedName();
        return JAVA_LANG_STRING.equals(qualifiedName)
               && STRING_FORMAT_NAME.equals(method.getName());
      }
    }
    return false;
  }

  private static @NotNull Collection<NamedFoldingDescriptor> getFolds(@NotNull final PsiMethodCallExpression callExpression) {
    try {
      final TextRange range = callExpression.getTextRange();
      final Iterator<PsiExpression> parameterIterator = ContainerUtil.iterate(callExpression.getArgumentList().getExpressions());

      PsiExpression formattingParameter = parameterIterator.next();
      if (!isString(formattingParameter)) {
        formattingParameter = parameterIterator.next();
        if (!isString(formattingParameter)) throw new IllegalStateException("Unknown signature!");
      }

      Iterator<String> stringsIterator = getStrings(formattingParameter).iterator();
      return getNamedFoldingDescriptors(parameterIterator, callExpression, range, stringsIterator);
    }
    catch (final RuntimeException e) {
      LOG.warn(e);
    }
    return ContainerUtil.emptyList();
  }

  private static @NotNull List<NamedFoldingDescriptor> getNamedFoldingDescriptors(@NotNull final Iterator<PsiExpression> parameterIterator,
                                                                                  @NotNull final PsiMethodCallExpression callExpression,
                                                                                  @NotNull final TextRange range,
                                                                                  @NotNull final Iterator<String> stringsIterator) {
    final List<NamedFoldingDescriptor> results = ContainerUtil.newArrayList();

    final FoldingGroup group = FoldingGroup.newGroup(STRING_FORMAT_GROUP_NAME);

    if (parameterIterator.hasNext()) {
      PsiExpression parameter = parameterIterator.next();
      results.add(new NamedFoldingDescriptor(callExpression,
                                             range.getStartOffset(), getEndOffset(parameter), group,
                                             FOLDED_STRING_START + stringsIterator.next() + getTextSuffix(parameter)));

      while (parameterIterator.hasNext()) {
        final PsiExpression nextParameter = parameterIterator.next();
        results.add(new NamedFoldingDescriptor(callExpression,
                                               parameter.getTextRange().getEndOffset(), getEndOffset(nextParameter), group,
                                               getTextPrefix(parameter) + stringsIterator.next() + getTextSuffix(nextParameter)));
        parameter = nextParameter;
      }

      results.add(new NamedFoldingDescriptor(callExpression,
                                             parameter.getTextRange().getEndOffset(), range.getEndOffset(), group,
                                             stringsIterator.next() + FOLDED_STRING_END));
    }
    else {
      results.add(new NamedFoldingDescriptor(callExpression,
                                             range.getStartOffset(), range.getEndOffset(), group,
                                             FOLDED_STRING_START + stringsIterator.next() + FOLDED_STRING_END));
    }

    if (stringsIterator.hasNext()) throw new IllegalStateException("More args specified: " + ContainerUtil.collect(stringsIterator));
    return results;
  }

  private static int getEndOffset(@NotNull final PsiExpression parameter) {
    TextRange textRange = parameter.getTextRange();
    return (getConstant(parameter) != null) ? textRange.getEndOffset()
                                            : textRange.getStartOffset();
  }

  protected static @NotNull String getTextPrefix(@NotNull final PsiExpression parameter) {
    Object value = getConstant(parameter);
    return (value != null) ? ""
                           : THIN_SPACE;
  }

  protected static @NotNull String getTextSuffix(@NotNull final PsiExpression parameter) {
    Object value = getConstant(parameter);
    return (value != null) ? value.toString()
                           : THIN_SPACE;
  }

  @SuppressWarnings("ConstantConditions")
  private static @NotNull List<String> getStrings(@NotNull final PsiExpression formattingParameter) {
    final List<String> results = ContainerUtil.newArrayList();

    int startMatching = 0, substringStart = 0;
    final StringBuilder input = new StringBuilder(getConstant(formattingParameter).toString());
    for (final Matcher matcher = STRING_FORMAT_PATTERN.matcher(input); matcher.find(startMatching); ) {
      final String pattern = matcher.group();
      if ("%%".equals(pattern)) {
        startMatching = replace(input, matcher, pattern, "%");
      }
      else if ("%n".equals(pattern)) {
        startMatching = replace(input, matcher, pattern, "↵");
      }
      else {
        results.add(input.substring(substringStart, matcher.start()));
        startMatching = substringStart = matcher.end();
      }
    }
    if (input.length() >= substringStart) {
      results.add(input.substring(substringStart, input.length()));
    }
    return results;
  }

  private static int replace(@NotNull final StringBuilder input,
                             @NotNull final Matcher matcher,
                             @NotNull final String pattern,
                             @NotNull final String replacement) {
    input.replace(matcher.start(), matcher.end(), replacement);
    return matcher.end() - (pattern.length() - replacement.length());
  }

  private static @Nullable Object getConstant(@NotNull final PsiExpression expression) {
    return isStringLiteral(expression) ? unquoteString(expression.getText())
                                       : computeConstantExpression(expression, false);
  }

  protected static boolean isString(@Nullable final PsiExpression expression) {
    return isStringLiteral(expression)
           || (computeConstantExpression(expression, false) instanceof String);
  }
}
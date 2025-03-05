// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.url.parameters;

import com.intellij.microservices.url.UrlPath;
import com.intellij.microservices.url.references.UrlPksParser;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PartiallyKnownString;
import com.intellij.util.SmartList;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public final class PathVariableDeclarationUtils {
  public static PsiReference @NotNull [] createPathVariableReferencesForPks(
    @NotNull PsiElement host,
    @NotNull PartiallyKnownString fullPks,
    @NotNull UrlPksParser parser,
    @NotNull PathVariableUsagesProvider pathVariableUsagesProvider) {
    String expressionValue = fullPks.getValueIfKnown();
    if (expressionValue == null) return PsiReference.EMPTY_ARRAY;

    List<PsiReference> result = new SmartList<>();
    for (PartiallyKnownString segmentPks : parser.splitUrlPath(fullPks)) {
      for (TextRange textRange : getVariablesTextRangesInPks(segmentPks, parser.pksPathSegment(segmentPks))) {
        TextRange rangeInHost = segmentPks.mapRangeToHostRange(host, textRange);
        if (rangeInHost == null) {
          continue; // means variable exists but could not be found in the literal (for instance is declared in the constant)
        }
        result.add(createPathVariableReference(host, rangeInHost, pathVariableUsagesProvider));
      }
    }

    return result.toArray(PsiReference.EMPTY_ARRAY);
  }

  private static List<TextRange> getVariablesTextRangesInPks(PartiallyKnownString pks, UrlPath.PathSegment maybeComplexVariable) {
    if (maybeComplexVariable instanceof UrlPath.PathSegment.Variable) {
      String variableName = ((UrlPath.PathSegment.Variable)maybeComplexVariable).getVariableName();
      if (variableName == null) return Collections.emptyList();

      int start = pks.findIndexOfInKnown(variableName);
      if (start == -1) return Collections.emptyList(); // just in case
      return Collections.singletonList(TextRange.from(start, variableName.length()));
    }

    if (!(maybeComplexVariable instanceof UrlPath.PathSegment.Composite)) return Collections.emptyList();
    SmartList<TextRange> result = new SmartList<>();
    int position = 0;
    for (UrlPath.PathSegment.Variable variable :
      StreamEx.of(((UrlPath.PathSegment.Composite)maybeComplexVariable).getSegments()).select(UrlPath.PathSegment.Variable.class)) {
      String variableName = variable.getVariableName();
      if (variableName == null) continue;
      int start = pks.findIndexOfInKnown(variableName, position);
      if (start == -1) continue; // just in case

      TextRange textRange = TextRange.from(start, variableName.length());
      result.add(textRange);
      position = textRange.getEndOffset();
    }

    return result;
  }

  private static @NotNull PsiReference createPathVariableReference(@NotNull PsiElement host,
                                                                   @NotNull TextRange variableRange,
                                                                   @NotNull PathVariableUsagesProvider pathVariableUsagesProvider) {
    return new PathVariableDeclaringReference(host, variableRange, pathVariableUsagesProvider);
  }
}

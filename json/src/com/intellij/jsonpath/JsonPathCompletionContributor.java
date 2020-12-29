// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.jsonpath.psi.JsonPathStringLiteral;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.intellij.jsonpath.JsonPathConstants.STANDARD_FUNCTIONS;
import static com.intellij.jsonpath.psi.JsonPathTokenSets.JSONPATH_DOT_NAVIGATION_SET;
import static com.intellij.jsonpath.psi.JsonPathTokenSets.JSONPATH_EQUALITY_OPERATOR_SET;
import static com.intellij.patterns.PlatformPatterns.psiElement;

public final class JsonPathCompletionContributor extends CompletionContributor {

  // 2. todo completion for null, true and false inside object literals

  public JsonPathCompletionContributor() {
    extend(CompletionType.BASIC,
           psiElement()
             .afterLeaf(psiElement().withElementType(JSONPATH_EQUALITY_OPERATOR_SET))
             .andNot(psiElement().withParent(JsonPathStringLiteral.class)),
           KeywordsCompletionProvider.INSTANCE);

    extend(CompletionType.BASIC,
           psiElement().afterLeaf(psiElement().withElementType(JSONPATH_DOT_NAVIGATION_SET)),
           new FunctionNamesCompletionProvider());
  }

  private static class FunctionNamesCompletionProvider extends CompletionProvider<CompletionParameters> {
    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      result.addElement(LookupElementBuilder.create("*").bold());

      for (Map.Entry<String, String> function : STANDARD_FUNCTIONS.entrySet()) {
        result.addElement(LookupElementBuilder.create(function.getKey() + "()")
                            .withPresentableText(function.getKey())
                            .withIcon(AllIcons.Nodes.Method)
                            .withTailText("()")
                            .withTypeText(function.getValue()));
      }
    }
  }

  private static class KeywordsCompletionProvider extends CompletionProvider<CompletionParameters> {
    private static final KeywordsCompletionProvider INSTANCE = new KeywordsCompletionProvider();
    private static final String[] KEYWORDS = new String[]{"null", "true", "false"};

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      for (String keyword : KEYWORDS) {
        result.addElement(LookupElementBuilder.create(keyword).bold());
      }
    }
  }
}

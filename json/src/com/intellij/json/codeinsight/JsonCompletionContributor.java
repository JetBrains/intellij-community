// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.codeinsight;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author Mikhail Golubev
 */
public class JsonCompletionContributor extends CompletionContributor {
  private static final PsiElementPattern.Capture<PsiElement> AFTER_COLON_IN_PROPERTY = psiElement()
    .afterLeaf(":").withSuperParent(2, JsonProperty.class)
    .andNot(psiElement().withParent(JsonStringLiteral.class));
  
  private static final PsiElementPattern.Capture<PsiElement> AFTER_COMMA_OR_BRACKET_IN_ARRAY = psiElement()
    .afterLeaf("[", ",").withSuperParent(2, JsonArray.class)
    .andNot(psiElement().withParent(JsonStringLiteral.class));
  
  public JsonCompletionContributor() {
    extend(CompletionType.BASIC, AFTER_COLON_IN_PROPERTY, MyKeywordsCompletionProvider.INSTANCE);
    extend(CompletionType.BASIC, AFTER_COMMA_OR_BRACKET_IN_ARRAY, MyKeywordsCompletionProvider.INSTANCE);
  }

  private static final class MyKeywordsCompletionProvider extends CompletionProvider<CompletionParameters> {
    private static final MyKeywordsCompletionProvider INSTANCE = new MyKeywordsCompletionProvider();
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

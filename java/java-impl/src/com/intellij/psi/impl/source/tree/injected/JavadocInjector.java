// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.javadoc.PsiSnippetDocTagImpl;
import com.intellij.psi.impl.source.javadoc.SnippetDocTagManipulator;
import com.intellij.psi.javadoc.PsiSnippetAttribute;
import com.intellij.psi.javadoc.PsiSnippetAttributeList;
import com.intellij.psi.javadoc.PsiSnippetDocTag;
import com.intellij.psi.javadoc.PsiSnippetDocTagValue;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JavadocInjector implements MultiHostInjector {

  private static final String LANG_ATTR_KEY = "lang";
  private static final String SNIPPET_INJECTION_JAVA_HEADER = "class ___JavadocSnippetPlaceholder {\n" +
                                                              "  void ___JavadocSnippetPlaceholderMethod() throws Throwable {\n";

  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar,
                                   @NotNull PsiElement context) {
    if (!(context instanceof PsiSnippetDocTagImpl)) return;

    final PsiSnippetDocTagImpl snippet = (PsiSnippetDocTagImpl)context;

    final Language language = getLanguage(snippet);

    final String prefix = language == JavaLanguage.INSTANCE ? SNIPPET_INJECTION_JAVA_HEADER : null;
    final String suffix = language == JavaLanguage.INSTANCE ? " }}" : null;

    registrar.startInjecting(language)
      .addPlace(prefix, suffix, snippet, innerRangeStrippingQuotes(snippet))
      .doneInjecting();
  }

  private static @NotNull Language getLanguage(@NotNull PsiSnippetDocTagImpl snippet) {
    PsiSnippetDocTagValue valueElement = snippet.getValueElement();
    if (valueElement == null) return JavaLanguage.INSTANCE;
    final PsiSnippetAttributeList attributeList = valueElement.getAttributeList();

    for (PsiSnippetAttribute attribute : attributeList.getAttributes()) {
      if (!LANG_ATTR_KEY.equals(attribute.getName())) continue;

      final PsiElement langValue = attribute.getValue();
      if (langValue == null) break;

      final String langValueText = stripPossibleLeadingAndTrailingQuotes(langValue);

      final Language language = findRegisteredLanguage(langValueText);
      if (language == null) break;

      return language;
    }
    return JavaLanguage.INSTANCE;
  }

  private static @Nullable Language findRegisteredLanguage(@NotNull String langValueText) {
    final Language language = Language.findLanguageByID(langValueText);
    if (language != null) return language;

    return ContainerUtil.find(Language.getRegisteredLanguages(),
                              e -> e.getID().equalsIgnoreCase(langValueText));
  }

  private static @NotNull String stripPossibleLeadingAndTrailingQuotes(@NotNull PsiElement langValue) {
    String langValueText = langValue.getText();
    if (langValueText.charAt(0) == '"') {
      langValueText = langValueText.substring(1);
    }
    if (langValueText.charAt(langValueText.length() - 1) == '"') {
      langValueText = langValueText.substring(0, langValueText.length() - 1);
    }
    return langValueText;
  }

  private static @NotNull TextRange innerRangeStrippingQuotes(@NotNull PsiSnippetDocTagImpl context) {
    return new SnippetDocTagManipulator().getRangeInElement(context);
  }

  @Override
  public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return List.of(PsiSnippetDocTag.class);
  }
}

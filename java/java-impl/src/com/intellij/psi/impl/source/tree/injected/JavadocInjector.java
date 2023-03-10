// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.java.JShellLanguage;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.javadoc.PsiSnippetDocTagImpl;
import com.intellij.psi.impl.source.javadoc.SnippetDocTagManipulator;
import com.intellij.psi.javadoc.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JavadocInjector implements MultiHostInjector {

  @Override
  public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar,
                                   @NotNull PsiElement context) {
    if (!(context instanceof PsiSnippetDocTagImpl snippet)) return;

    final Language language = getLanguage(snippet);

    final List<TextRange> ranges = snippet.getContentRanges();
    if (ranges.isEmpty()) return;

    registrar.startInjecting(language);
    for (TextRange range : ranges) {
      registrar.addPlace(null, null, snippet, range);
    }
    registrar.doneInjecting();

  }

  private static @NotNull Language getLanguage(@NotNull PsiSnippetDocTagImpl snippet) {
    PsiSnippetDocTagValue valueElement = snippet.getValueElement();
    if (valueElement == null) return JShellLanguage.INSTANCE;
    final PsiSnippetAttributeList attributeList = valueElement.getAttributeList();

    PsiSnippetAttribute attribute = attributeList.getAttribute(PsiSnippetAttribute.LANG_ATTRIBUTE);
    if (attribute == null) {
      return JShellLanguage.INSTANCE;
    }
    final PsiSnippetAttributeValue langValue = attribute.getValue();

    if (langValue != null) {
      String langValueText = langValue.getValue();
      if ("java".equalsIgnoreCase(langValueText)) {
        return JShellLanguage.INSTANCE;
      }

      final Language language = findRegisteredLanguage(langValueText);
      if (language != null) {
        return language;
      }
    }
    return PlainTextLanguage.INSTANCE;
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

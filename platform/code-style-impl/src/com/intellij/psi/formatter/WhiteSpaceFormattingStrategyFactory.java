// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Contains utility methods for working with {@link WhiteSpaceFormattingStrategy}.
 */
public final class WhiteSpaceFormattingStrategyFactory {
  public static final WhiteSpaceFormattingStrategy DEFAULT_STRATEGY =
    new StaticSymbolWhiteSpaceDefinitionStrategy(' ', '\t', '\n');

  private static final AtomicReference<WeakReference<Collection<WhiteSpaceFormattingStrategy>>> myCachedStrategies
    = new AtomicReference<>();

  private WhiteSpaceFormattingStrategyFactory() {
  }

  /**
   * @return    default language-agnostic white space strategy
   */
  public static WhiteSpaceFormattingStrategy getStrategy() {
    return DEFAULT_STRATEGY;
  }

  /**
   * Tries to return white space strategy to use for the given language.
   *
   * @param language    target language
   * @return            white space strategy to use for the given language
   */
  public static @NotNull WhiteSpaceFormattingStrategy getStrategy(@NotNull Language language) {
    WhiteSpaceFormattingStrategy strategy = LanguageWhiteSpaceFormattingStrategy.INSTANCE.forLanguage(language);
    if (strategy != null) {
      if (strategy.replaceDefaultStrategy()) {
        return strategy;
      }
      else {
        return new CompositeWhiteSpaceFormattingStrategy(List.of(DEFAULT_STRATEGY, strategy));
      }
    }
    else {
      return getStrategy();
    }
  }

  /**
   * @return    collection of all registered white space strategies
   */
  public static @NotNull Collection<WhiteSpaceFormattingStrategy> getAllStrategies() {
    final WeakReference<Collection<WhiteSpaceFormattingStrategy>> reference = myCachedStrategies.get();
    final Collection<WhiteSpaceFormattingStrategy> strategies = SoftReference.dereference(reference);
    if (strategies != null) {
      return strategies;
    }
    final Collection<Language> languages = Language.getRegisteredLanguages();

    Set<WhiteSpaceFormattingStrategy> result = new HashSet<>();
    result.add(DEFAULT_STRATEGY);
    final LanguageWhiteSpaceFormattingStrategy languageStrategy = LanguageWhiteSpaceFormattingStrategy.INSTANCE;
    for (Language language : languages) {
      final WhiteSpaceFormattingStrategy strategy = languageStrategy.forLanguage(language);
      if (strategy != null) {
        result.add(strategy);
      }
    }
    myCachedStrategies.set(new WeakReference<>(result));
    return result;
  }

  /**
   * Returns white space strategy to use for the document managed by the given editor.
   *
   * @param editor      editor that manages target document
   * @return            white space strategy for the document managed by the given editor
   */
  public static WhiteSpaceFormattingStrategy getStrategy(@NotNull Editor editor) {
    Project project = editor.getProject();
    if (project != null) {
      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (psiFile != null) {
        return getStrategy(psiFile.getLanguage());
      }
    }
    return getStrategy();
  }
}

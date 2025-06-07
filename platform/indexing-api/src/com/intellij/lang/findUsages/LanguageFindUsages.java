// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.lang.findUsages;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class LanguageFindUsages extends LanguageExtension<FindUsagesProvider> {
  public static final LanguageFindUsages INSTANCE = new LanguageFindUsages() {
    @Override
    public @NotNull List<FindUsagesProvider> allForLanguage(@NotNull Language language) {
      List<FindUsagesProvider> result = super.allForLanguage(language);
      if (result.isEmpty() ) {
        return Collections.singletonList(getDefaultImplementation());
      }
      return result;
    }
  };

  private LanguageFindUsages() {
    super("com.intellij.lang.findUsagesProvider", new EmptyFindUsagesProvider());
  }


  /**
   * {@link FindUsagesProvider#canFindUsagesFor(PsiElement)}
   * @return true iff could be found usages by some provider for this element
   */
  public static boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
    return getFromProviders(psiElement, Boolean.FALSE, p -> p.canFindUsagesFor(psiElement));
  }

  /**
   * {@link FindUsagesProvider#getWordsScanner()}
   * @return a word-scanner specified by some provider or null
   */
  public static @Nullable WordsScanner getWordsScanner(@NotNull Language language) {
    for (FindUsagesProvider provider : INSTANCE.allForLanguage(language)) {
      WordsScanner scanner = provider.getWordsScanner();
      if (scanner != null) {
        return scanner;
      }
    }
    return null;
  }

  /**
   * {@link FindUsagesProvider#getDescriptiveName(PsiElement)}
   * @return specified by some provider non-empty user-visible descriptive name or empty string
   */
  public static @NotNull String getDescriptiveName(@NotNull PsiElement psiElement) {
    return getFromProviders(psiElement, "", p -> p.getDescriptiveName(psiElement));
  }

  /**
   * {@link FindUsagesProvider#getType(PsiElement)}
   * @return specified by some provider non-empty user-visible type name or empty string
   */
  public static @NotNull String getType(@NotNull PsiElement psiElement) {
    return getFromProviders(psiElement, "", p -> p.getType(psiElement));
  }

  /**
   * {@link FindUsagesProvider#getNodeText(PsiElement, boolean)}
   * @return specified by some provider the text representing the specified PSI element in the Find Usages tree or empty string
   */
  public static @NotNull String getNodeText(@NotNull PsiElement psiElement, boolean useFullName) {
    return getFromProviders(psiElement, "", p -> p.getNodeText(psiElement, useFullName));
  }

  /**
   * {@link FindUsagesProvider#getHelpId(PsiElement)}
   * @return specified by some provider ID of the help topic
   */
  public static @Nullable String getHelpId(@NotNull PsiElement psiElement) {
    return getFromProviders(psiElement, null, p -> p.getHelpId(psiElement));
  }

  private static <T> T getFromProviders(@NotNull PsiElement psiElement,
                                        T defaultValue, @NotNull Function<? super FindUsagesProvider, ? extends T> getter) {
    Language language = psiElement.getLanguage();
    List<FindUsagesProvider> providers = INSTANCE.allForLanguage(language);
    assert !providers.isEmpty() : "Element: " + psiElement + ", language: " + language;

    for (FindUsagesProvider provider : providers) {
      T res = getter.apply(provider);
      if (res != null && !res.equals(defaultValue)) {
        return res;
      }
    }
    return defaultValue;
  }

}
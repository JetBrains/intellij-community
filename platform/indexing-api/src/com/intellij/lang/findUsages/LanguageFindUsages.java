/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.lang.findUsages;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class LanguageFindUsages extends LanguageExtension<FindUsagesProvider> {
  public static final LanguageFindUsages INSTANCE = new LanguageFindUsages() {
    @NotNull
    @Override
    public List<FindUsagesProvider> allForLanguage(@NotNull Language language) {
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
   * @return true iff could be found usages by some provider for this element
   */
  public static boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
    return forPsiElement(psiElement) != null;
  }

  @Nullable
  public static WordsScanner getWordsScanner(@NotNull Language language) {
    for (FindUsagesProvider provider : INSTANCE.allForLanguage(language)) {
      WordsScanner scanner = provider.getWordsScanner();
      if (scanner != null) {
        return scanner;
      }
    }
    return null;
  }

  @NotNull
  public static String getDescriptiveName(@NotNull PsiElement psiElement) {
    FindUsagesProvider provider = forPsiElement(psiElement);
    return provider == null ? "" : provider.getDescriptiveName(psiElement);
  }

  /**
   * @return specified by some provider non-empty user-visible type name or empty string
   */
  @NotNull
  public static String getType(@NotNull PsiElement psiElement) {
    FindUsagesProvider provider = forPsiElement(psiElement);
    return provider == null ? "" : provider.getType(psiElement);
  }

  @NotNull
  public static String getNodeText(@NotNull PsiElement psiElement, boolean useFullName) {
    FindUsagesProvider provider = forPsiElement(psiElement);
    return provider == null ? "" : provider.getNodeText(psiElement, useFullName);
  }

  @Nullable
  public static String getHelpId(@NotNull PsiElement psiElement) {
    FindUsagesProvider provider = forPsiElement(psiElement);
    return provider == null ? null : provider.getHelpId(psiElement);
  }

  private static FindUsagesProvider forPsiElement(@NotNull PsiElement psiElement) {
    Language language = psiElement.getLanguage();
    List<FindUsagesProvider> providers = INSTANCE.allForLanguage(language);
    assert !providers.isEmpty() : "Element: " + psiElement + ", language: " + language;

    for (FindUsagesProvider provider : providers) {
      if (provider.canFindUsagesFor(psiElement)) {
        return provider;
      }
    }
    return null;
  }

}
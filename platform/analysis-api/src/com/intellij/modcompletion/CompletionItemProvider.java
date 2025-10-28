// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcompletion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.modcommand.ActionContext;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A language-specific provider for {@link CompletionItem} completion options 
 */
@NotNullByDefault
public interface CompletionItemProvider {
  LanguageExtension<CompletionItemProvider> EP_NAME = new LanguageExtension<>("com.intellij.modcompletion.completionItemProvider");

  /**
   * Provide completion items for given context
   * 
   * @param context context to use
   * @param sink a consumer to pass completion items to
   */
  void provideItems(CompletionContext context, Consumer<CompletionItem> sink);

  /**
   * @param language language to get providers for
   * @return language-specific completion providers
   */
  static List<CompletionItemProvider> forLanguage(Language language) {
    return EP_NAME.forKey(language);
  }

  /**
   * Completion context
   * 
   * @param context an action context to use
   * @param prefix current completion prefix
   * @param invocationCount invocation count (0 = auto-popup)
   * @param type completion type
   */
  record CompletionContext(ActionContext context, String prefix, int invocationCount, CompletionType type) {
    /**
     * @return a context PSI element
     */
    public PsiElement element() {
      return Objects.requireNonNull(context.element());
    }

    /**
     * @return true if the smart completion is invoked (Ctrl+Shift+Space)
     */
    public boolean isSmart() {
      return type == CompletionType.SMART;
    }

    /**
     * @return true if the basic completion is invoked (Ctrl+Space)
     */
    public boolean isBasic() {
      return type == CompletionType.BASIC;
    }
  }
}

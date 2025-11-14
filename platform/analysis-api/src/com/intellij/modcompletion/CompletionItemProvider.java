// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcompletion;

import com.intellij.codeInsight.completion.BaseCompletionParameters;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.List;
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
   * @return true if the mod command completion is enabled in registry
   */
  @ApiStatus.Internal
  static boolean modCommandCompletionEnabled() {
    return Registry.is("ide.completion.modcommand", false);
  }

  /**
   * Completion context
   *
   * @param originalFile original file
   * @param offset current offset in the file
   * @param element current element in the file
   * @param matcher prefix matcher
   * @param invocationCount invocation count (0 = auto-popup)
   * @param type completion type
   */
  record CompletionContext(
    PsiFile originalFile,
    int offset,
    PsiElement element,
    PrefixMatcher matcher,
    int invocationCount,
    CompletionType type
  )
    implements BaseCompletionParameters {
    /**
     * @return matcher prefix
     */
    public String prefix() {
      return matcher.getPrefix();
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

    @Override
    public PsiFile getOriginalFile() {
      return originalFile;
    }

    @Override
    public int getOffset() {
      return offset;
    }

    @Override
    public PsiElement getPosition() {
      return element;
    }
  }
}

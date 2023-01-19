// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.folding;

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Base class and extension point for custom folding providers.
 */
public abstract class CustomFoldingProvider {
  public static final ExtensionPointName<CustomFoldingProvider> EP_NAME = ExtensionPointName.create("com.intellij.customFoldingProvider");
  private static final Logger LOG = Logger.getInstance(CustomFoldingProvider.class);

  @NotNull
  public static List<CustomFoldingProvider> getAllProviders() {
    return EP_NAME.getExtensionList();
  }

  public abstract boolean isCustomRegionStart(String elementText);
  public abstract boolean isCustomRegionEnd(String elementText);
  public abstract String getPlaceholderText(String elementText);

  /**
   * @return A description string shown in "Surround With" action.
   */
  @Nls(capitalization = Nls.Capitalization.Sentence)
  public abstract String getDescription();

  /**
   * If the method returns `false`, the inheritance class need:
   *  1. override the method `isSupportedBy` and limit folding by a language-specific instantiation of `CustomFoldingBuilder`
   *  2. the language-specific instantiation of `CustomFoldingBuilder` need to filter out language-specific
   *     AST nodes in the overridden `CustomFoldingBuilder#isCustomFoldingCandidate(ASTNode)`
   *
   * @return true, if you like to wrap `getStartString/getEndString` return values
   *         in language specific comment on folding generation
   */
  public boolean wrapStartEndMarkerTextInLanguageSpecificComment() {
    return true;
  }

  /**
   * @return true, if custom folding provider is supported
   */
  public boolean isSupportedBy(FoldingBuilder foldingBuilder) {
    if (!wrapStartEndMarkerTextInLanguageSpecificComment()) {
      // this method need to be overridden, if `wrapStartEndMarkerTextInLanguageSpecificComment` returns `false`.
      // The overridden methods returns `true` only for a language-specific child of `CustomFoldingBuilder` class with
      // overridden `CustomFoldingBuilder#isCustomFoldingCandidate(ASTNode)`.
      LOG.error("non-comment based custom folding node need to be filtered in overridden `CustomFoldingBuilder#isCustomFoldingCandidate(ASTNode)`");
    }
    return foldingBuilder instanceof CustomFoldingBuilder;
  }

  public boolean isSupported(@NotNull Language language) {
    return true;
  }

  /**
   * Called from new folding generation procedure.
   * Please, use tailing `?` as description placeholder if any.
   *
   * @return starting marker text without comment suffix/prefix if `wrapStartEndMarkerTextInLanguageSpecificComment` returns true.
   *         Else, full starting element text for non-comment bound element.
   */
  @NonNls
  public abstract String getStartString();

  /**
   * Called from new folding generation procedure.
   *
   * @return ending marker text without comment suffix/prefix if `wrapStartEndMarkerTextInLanguageSpecificComment` returns true.
   *         Else, full ending element text for non-comment bound element.
   */
  @NonNls
  public abstract String getEndString();

  public boolean isCollapsedByDefault(String text) {
    return CodeFoldingSettings.getInstance().COLLAPSE_CUSTOM_FOLDING_REGIONS;
  }
}

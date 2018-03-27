/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.psi.tree;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import com.intellij.util.diff.FlyweightCapableTreeStructure;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Irina.Chernushina on 12/5/2017.
 *
 * Provide this custom comparator to detect whether to reparse lazy-reparseable children of the node for the language:
 * - For instance, if parsing of some IReparseableElementType depends on its parents contents, custom comparator is needed to check this.
 * - Having custom comparator as extension point helps for correct comparing also for embedded/injected fragments.
 */
public interface CustomLanguageASTComparator {
  LanguageExtension<CustomLanguageASTComparator> EXTENSION_POINT_NAME = new LanguageExtension<>("com.intellij.tree.CustomLanguageASTComparator");

  static List<CustomLanguageASTComparator> getMatchingComparators(@NotNull PsiFile file) {
    return EXTENSION_POINT_NAME.allForLanguage(file.getLanguage());
  }

  /**
   * @return {@code ThreeState#NO} for the children to be reparsed, {@code ThreeState#UNSURE} to continue comparing
   */
  @NotNull
  ThreeState compareAST(@NotNull ASTNode oldNode,
                        @NotNull LighterASTNode newNode,
                        @NotNull FlyweightCapableTreeStructure<LighterASTNode> structure);
}

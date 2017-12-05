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
 */
public interface CustomLanguageASTComparator {
  LanguageExtension<CustomLanguageASTComparator> EXTENSION_POINT_NAME = new LanguageExtension<>("com.intellij.tree.CustomLanguageASTComparator");

  static List<CustomLanguageASTComparator> getMatchingComparators(@NotNull PsiFile file) {// todo for language does not work
    return EXTENSION_POINT_NAME.allForLanguage(file.getLanguage());
  }

  @NotNull
  ThreeState compareAST(@NotNull ASTNode oldNode,
                        @NotNull LighterASTNode newNode,
                        @NotNull FlyweightCapableTreeStructure<LighterASTNode> structure);
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.folding;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author yole
 * @author Konstantin Bulenkov
 */
public class LanguageFolding extends LanguageExtension<FoldingBuilder> {
  public static final LanguageFolding INSTANCE = new LanguageFolding();

  private LanguageFolding() {
    super("com.intellij.lang.foldingBuilder");
  }

  /**
   * This method is left to preserve binary compatibility.
   */
  @Override
  public FoldingBuilder forLanguage(@NotNull Language l) {
    return super.forLanguage(l);
  }

  @Override
  protected FoldingBuilder findForLanguage(@NotNull Language l) {
    List<FoldingBuilder> extensions = allForLanguage(l);
    if (extensions.isEmpty()) {
      return null;
    }
    else if (extensions.size() == 1) {
      return extensions.get(0);
    }
    else {
      return new CompositeFoldingBuilder(extensions);
    }
  }

  /**
   * Only queries base language results if there are no extensions for originally requested language.
   */
  @NotNull
  @Override
  public List<FoldingBuilder> allForLanguage(@NotNull Language language) {
    for (Language l = language; l != null; l = l.getBaseLanguage()) {
      List<FoldingBuilder> extensions = forKey(l);
      if (!extensions.isEmpty()) {
        return extensions;
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  public static FoldingDescriptor[] buildFoldingDescriptors(@Nullable FoldingBuilder builder, @NotNull PsiElement root, @NotNull Document document, boolean quick) {
    if (!DumbService.isDumbAware(builder) && DumbService.getInstance(root.getProject()).isDumb()) {
      return FoldingDescriptor.EMPTY;
    }

    if (builder instanceof FoldingBuilderEx) {
      return ((FoldingBuilderEx)builder).buildFoldRegions(root, document, quick);
    }
    final ASTNode astNode = root.getNode();
    if (astNode == null || builder == null) {
      return FoldingDescriptor.EMPTY;
    }

    return builder.buildFoldRegions(astNode, document);
  }
}

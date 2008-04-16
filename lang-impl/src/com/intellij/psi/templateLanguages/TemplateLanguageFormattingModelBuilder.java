/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.templateLanguages;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.DocumentBasedFormattingModel;
import com.intellij.psi.formatter.common.AbstractBlock;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class TemplateLanguageFormattingModelBuilder implements FormattingModelBuilder{
  @NotNull
  public FormattingModel createModel(final PsiElement element, final CodeStyleSettings settings) {
    if (element instanceof PsiFile) {
      final TemplateLanguageFileViewProvider provider = (TemplateLanguageFileViewProvider)((PsiFile)element).getViewProvider();
      final Language language = provider.getTemplateDataLanguage();
      FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forLanguage(language);
      if (builder != null) {
        return builder.createModel(provider.getPsi(language), settings);
      }
    }

    final PsiFile file = element.getContainingFile();
    return new DocumentBasedFormattingModel(new AbstractBlock(element.getNode(), Wrap.createWrap(WrapType.NONE, false), Alignment.createAlignment()) {
      protected List<Block> buildChildren() {
        return Collections.emptyList();
      }

      public Spacing getSpacing(final Block child1, final Block child2) {
        return Spacing.getReadOnlySpacing();
      }

      public boolean isLeaf() {
        return true;
      }
    }, element.getProject(), settings, file.getFileType(), file);
  }

  public TextRange getRangeAffectingIndent(final PsiFile file, final int offset, final ASTNode elementAtOffset) {
    return null;
  }
}

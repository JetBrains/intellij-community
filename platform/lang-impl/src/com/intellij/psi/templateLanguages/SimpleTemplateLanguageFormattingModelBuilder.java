// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.templateLanguages;

import com.intellij.formatting.*;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageFormatting;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.formatter.DocumentBasedFormattingModel;
import com.intellij.psi.formatter.common.AbstractBlock;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class SimpleTemplateLanguageFormattingModelBuilder implements FormattingModelBuilder{
  @Override
  public @NotNull FormattingModel createModel(@NotNull FormattingContext formattingContext) {
    PsiElement element = formattingContext.getPsiElement();
    if (element instanceof PsiFile) {
      final FileViewProvider viewProvider = ((PsiFile)element).getViewProvider();
      if (viewProvider instanceof TemplateLanguageFileViewProvider) {
        final Language templateDataLanguage = ((TemplateLanguageFileViewProvider)viewProvider).getTemplateDataLanguage();
        FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forLanguage(templateDataLanguage);
        if (builder != null && templateDataLanguage != element.getLanguage()) {
          return builder.createModel(formattingContext.withPsiElement(viewProvider.getPsi(templateDataLanguage)));
        }
      }
    }

    final PsiFile file = element.getContainingFile();
    return new DocumentBasedFormattingModel(new AbstractBlock(element.getNode(), Wrap.createWrap(WrapType.NONE, false), Alignment.createAlignment()) {
      @Override
      protected List<Block> buildChildren() {
        return Collections.emptyList();
      }

      @Override
      public Spacing getSpacing(final Block child1, @NotNull final Block child2) {
        return Spacing.getReadOnlySpacing();
      }

      @Override
      public boolean isLeaf() {
        return true;
      }
    }, element.getProject(), formattingContext.getCodeStyleSettings(), file.getFileType(), file);
  }

}

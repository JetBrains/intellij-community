/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.formatting.templateLanguages;

import com.intellij.formatting.*;
import static com.intellij.formatting.templateLanguages.BlockUtil.buildChildWrappers;
import static com.intellij.formatting.templateLanguages.BlockUtil.filterBlocksByRange;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.DocumentBasedFormattingModel;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Alexey Chmutov
 *         Date: Jun 26, 2009
 *         Time: 4:07:09 PM
 */
public abstract class TemplateLanguageFormattingModelBuilder implements DelegatingFormattingModelBuilder, TemplateLanguageBlockFactory {

  @NotNull
  public FormattingModel createModel(PsiElement element, CodeStyleSettings settings) {
    final PsiFile file = element.getContainingFile();
    Block rootBlock = getRootBlock(file, file.getViewProvider(), settings);
    return new DocumentBasedFormattingModel(rootBlock, element.getProject(), settings, file.getFileType(), file);
  }

  private Block getRootBlock(PsiElement element, FileViewProvider viewProvider, CodeStyleSettings settings) {
    ASTNode node = element.getNode();
    if (node == null) {
      return createDummyBlock(node);
    }
    final Language dataLanguage = ((TemplateLanguageFileViewProvider)viewProvider).getTemplateDataLanguage();
    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forLanguage(dataLanguage);
    if (builder instanceof DelegatingFormattingModelBuilder && ((DelegatingFormattingModelBuilder)builder).dontFormatMyModel()) {
      return createDummyBlock(node);
    }
    if (builder == null) {
      return createTemplateLanguageBlock(node, Collections.<DataLanguageBlockWrapper>emptyList(), settings);
    }
    final FormattingModel model = builder.createModel(viewProvider.getPsi(dataLanguage), settings);
    List<DataLanguageBlockWrapper> childWrappers = buildChildWrappers(model.getRootBlock());
    if (childWrappers.size() == 1) {
      childWrappers = buildChildWrappers(childWrappers.get(0).getOriginal());
    }
    return createTemplateLanguageBlock(node, filterBlocksByRange(childWrappers, node.getTextRange()), settings);
  }

  protected AbstractBlock createDummyBlock(final ASTNode node) {
    return new AbstractBlock(node, Wrap.createWrap(WrapType.NONE, false), Alignment.createAlignment()) {
      protected List<Block> buildChildren() {
        return Collections.emptyList();
      }

      public Spacing getSpacing(final Block child1, final Block child2) {
        return Spacing.getReadOnlySpacing();
      }

      public boolean isLeaf() {
        return true;
      }
    };
  }

  public TextRange getRangeAffectingIndent(PsiFile file, int offset, ASTNode elementAtOffset) {
    return null;
  }

  public boolean dontFormatMyModel() {
    return true;
  }
}

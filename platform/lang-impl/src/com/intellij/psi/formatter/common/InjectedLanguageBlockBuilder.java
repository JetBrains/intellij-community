// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.formatter.common;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageFormatting;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class InjectedLanguageBlockBuilder {
  private static final Logger LOG = Logger.getInstance(InjectedLanguageBlockBuilder.class);

  @NotNull
  public Block createInjectedBlock(@NotNull ASTNode node,
                                   @NotNull Block originalBlock,
                                   Indent indent,
                                   int offset,
                                   TextRange range,
                                   @NotNull Language language) {
    return new InjectedLanguageBlockWrapper(originalBlock, offset, range, indent, language);
  }

  public abstract CodeStyleSettings getSettings();

  protected boolean supportsMultipleFragments() {
    return false;
  }

  public abstract boolean canProcessFragment(String text, ASTNode injectionHost);

  public abstract Block createBlockBeforeInjection(ASTNode node, Wrap wrap, Alignment alignment, Indent indent, TextRange range);

  public abstract Block createBlockAfterInjection(ASTNode node, Wrap wrap, Alignment alignment, Indent indent, TextRange range);

  public boolean addInjectedBlocks(List<? super Block> result, final ASTNode injectionHost, Wrap wrap, Alignment alignment, Indent indent) {
    Ref<Integer> lastInjectionEndOffset = new Ref<>(0);

    final PsiLanguageInjectionHost.InjectedPsiVisitor injectedPsiVisitor = (injectedPsi, places) -> {
      if (places.isEmpty() || (places.size() != 1 && !supportsMultipleFragments())) {
        return;
      }
      PsiLanguageInjectionHost.Shred firstShred = places.get(0);
      PsiLanguageInjectionHost.Shred lastShred = places.get(places.size() - 1);
      PsiLanguageInjectionHost shredHost = firstShred.getHost();
      if (shredHost == null) {
        return;
      }

      for (PsiLanguageInjectionHost.Shred place : places) {
        if (place.getHost() != shredHost) return;
      }

      TextRange injectionRange = new TextRange(firstShred.getRangeInsideHost().getStartOffset(),
                                               lastShred.getRangeInsideHost().getEndOffset());
      ASTNode node = shredHost.getNode();
      if (node == null || !injectionHost.getTextRange().contains(injectionRange.shiftRight(node.getStartOffset()))) {
        return;
      }
      if (node != injectionHost) {
        int shift = 0;
        boolean canProcess = false;
        for (ASTNode n = injectionHost.getTreeParent(), prev = injectionHost; n != null; prev = n, n = n.getTreeParent()) {
          shift += n.getStartOffset() - prev.getStartOffset();
          if (n == node) {
            injectionRange = injectionRange.shiftRight(shift);
            canProcess = true;
            break;
          }
        }
        if (!canProcess) {
          return;
        }
      }

      String childText;
      if (injectionHost.getTextLength() == injectionRange.getEndOffset() && injectionRange.getStartOffset() == 0 ||
          canProcessFragment((childText = injectionHost.getText()).substring(0, injectionRange.getStartOffset()), injectionHost) &&
          canProcessFragment(childText.substring(injectionRange.getEndOffset()), injectionHost)) {

        // inject language block

        final Language childLanguage = injectedPsi.getLanguage();
        final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(childLanguage, injectionHost.getPsi());

        if (builder != null) {
          final int startOffset = injectionRange.getStartOffset();
          final int endOffset = injectionRange.getEndOffset();
          TextRange range = injectionHost.getTextRange();
          final int prefixLength = firstShred.getPrefix().length();
          final int suffixLength = lastShred.getSuffix().length();

          int childOffset = range.getStartOffset();
          if (lastInjectionEndOffset.get() < startOffset) {
            result.add(createBlock(injectionHost, wrap, alignment, indent, new TextRange(lastInjectionEndOffset.get(), startOffset)));
          }

          addInjectedLanguageBlocks(result, injectedPsi, indent, childOffset + startOffset,
                                    new TextRange(prefixLength, injectedPsi.getTextLength() - suffixLength), places);

          lastInjectionEndOffset.set(endOffset);
        }
      }
    };
    final PsiElement injectionHostPsi = injectionHost.getPsi();
    PsiFile containingFile = injectionHostPsi.getContainingFile();
    InjectedLanguageManager.getInstance(containingFile.getProject())
      .enumerateEx(injectionHostPsi, containingFile, true, injectedPsiVisitor);

    if (lastInjectionEndOffset.get() > 0) {
      if (lastInjectionEndOffset.get() < injectionHost.getTextLength()) {
        result.add(createBlock(injectionHost, wrap, alignment, indent,
                               new TextRange(lastInjectionEndOffset.get(), injectionHost.getTextLength())));
      }
      return true;
    }
    return false;
  }

  private Block createBlock(ASTNode injectionHost, Wrap wrap, Alignment alignment, Indent indent, TextRange range) {
    if (range.getStartOffset() == 0) {
      final ASTNode leaf = injectionHost.findLeafElementAt(range.getEndOffset() - 1);
      return createBlockBeforeInjection(
        leaf, wrap, alignment, indent, range.shiftRight(injectionHost.getStartOffset()));
    }
    final ASTNode leaf = injectionHost.findLeafElementAt(range.getStartOffset());
    return createBlockAfterInjection(
      leaf, wrap, alignment, indent, range.shiftRight(injectionHost.getStartOffset()));
  }

  protected void addInjectedLanguageBlocks(List<? super Block> result,
                                           PsiFile injectedFile,
                                           Indent indent,
                                           int offset,
                                           TextRange injectedEditableRange,
                                           List<PsiLanguageInjectionHost.Shred> shreds) {
    addInjectedLanguageBlockWrapper(result, injectedFile.getNode(), indent, offset, injectedEditableRange);
  }

  public void addInjectedLanguageBlockWrapper(final List<? super Block> result, final ASTNode injectedNode,
                                              final Indent indent, int offset, @Nullable TextRange range) {
    if (isEmptyRange(injectedNode, range)) {
      return;
    }

    final PsiElement childPsi = injectedNode.getPsi();
    final Language childLanguage = childPsi.getLanguage();
    final FormattingModelBuilder builder = LanguageFormatting.INSTANCE.forContext(childLanguage, childPsi);
    LOG.assertTrue(builder != null);
    final FormattingModel childModel = builder.createModel(FormattingContext.create(childPsi, getSettings()));
    Block original = childModel.getRootBlock();

    if (original.isLeaf() && !injectedNode.getText().trim().isEmpty() || !original.getSubBlocks().isEmpty()) {
      result.add(createInjectedBlock(injectedNode, original, indent, offset, range, childLanguage));
    }
  }

  protected static boolean isEmptyRange(@NotNull ASTNode injectedNode, @Nullable TextRange range) {
    return range != null && (range.getLength() == 0 || StringUtil.isEmptyOrSpaces(range.substring(injectedNode.getText())));
  }
}
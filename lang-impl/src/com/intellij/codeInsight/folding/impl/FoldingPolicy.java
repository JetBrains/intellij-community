package com.intellij.codeInsight.folding.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.LeafElement;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.TreeMap;

class FoldingPolicy {
  private FoldingPolicy() {}

  /**
   * Returns map from element to range to fold, elements are sorted in start offset order
   */
  public static TreeMap<PsiElement, FoldingDescriptor> getElementsToFold(PsiElement file, Document document) {
    TreeMap<PsiElement, FoldingDescriptor> map = new TreeMap<PsiElement, FoldingDescriptor>(new Comparator<PsiElement>() {
      public int compare(PsiElement element, PsiElement element1) {
        int startOffsetDiff = element.getTextRange().getStartOffset() - element1.getTextRange().getStartOffset();
        return startOffsetDiff == 0 ? element.getTextRange().getEndOffset() - element1.getTextRange().getEndOffset() : startOffsetDiff;
      }
    });
    final Language lang = file.getLanguage();
    final FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(lang);
    if (foldingBuilder != null) {
      //PsiDocumentManager.getInstance(file.getProject()).commitDocument(document);
      final ASTNode node = file.getNode();
      ChameleonTransforming.transformChildren(node, true);
      final FoldingDescriptor[] foldingDescriptors = foldingBuilder.buildFoldRegions(node, document);
      for (FoldingDescriptor descriptor : foldingDescriptors) {
        ASTNode descriptorNode = descriptor.getElement();
        if (descriptorNode instanceof LeafElement) descriptorNode = ChameleonTransforming.transform((LeafElement)descriptorNode);
        map.put(SourceTreeToPsiMap.treeElementToPsi(descriptorNode), descriptor);
      }
    }

    return map;
  }


  public static boolean isCollapseByDefault(PsiElement element) {
    final Language lang = element.getLanguage();
    final FoldingBuilder foldingBuilder = LanguageFolding.INSTANCE.forLanguage(lang);
    return foldingBuilder != null && foldingBuilder.isCollapsedByDefault(element.getNode());
  }

  @Nullable
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static String getSignature(PsiElement element) {
    for(ElementSignatureProvider provider: Extensions.getExtensions(ElementSignatureProvider.EP_NAME)) {
      String signature = provider.getSignature(element);
      if (signature != null) return signature;
    }
    return null;
  }

  @Nullable
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static PsiElement restoreBySignature(PsiFile file, String signature) {
    for(ElementSignatureProvider provider: Extensions.getExtensions(ElementSignatureProvider.EP_NAME)) {
      PsiElement result = provider.restoreBySignature(file, signature);
      if (result != null) return result;
    }
    return null;
  }
}

package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.LanguageDialect;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CollectHighlightsUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil");

  private CollectHighlightsUtil() {
  }

  @NotNull
  public static List<PsiElement> getElementsInRange(PsiElement root, final int startOffset, final int endOffset) {
    return getElementsInRange(root, startOffset, endOffset, false);
  }

  @NotNull
  public static List<PsiElement> getElementsInRange(final PsiElement root,
                                                    final int startOffset,
                                                    final int endOffset,
                                                    boolean includeAllParents) {
    PsiElement commonParent = findCommonParent(root, startOffset, endOffset);
    if (commonParent == null) return Collections.emptyList();
    final List<PsiElement> list = new ArrayList<PsiElement>();

    for(HighlightRangeExtension extension: Extensions.getExtensions(HighlightRangeExtension.EP_NAME)) {
      List<PsiElement> extendList = extension.getElementsToHighlight(root, commonParent, startOffset, endOffset);
      if (extendList != null) {
        list.addAll(extendList);
      }
    }

    PsiElement parent = commonParent;
    while (parent != null && parent != root) {
      list.add(parent);
      parent = includeAllParents ? parent.getParent() : null;
    }

    list.add(root);

    return Collections.unmodifiableList(list);
  }

  @Nullable
  public static PsiElement findCommonParent(final PsiElement root, final int startOffset, final int endOffset) {
    if (startOffset == endOffset) return null;
    final PsiElement left = findElementAtInRoot(root, startOffset);
    PsiElement right = findElementAtInRoot(root, endOffset - 1);
    if (left == null || right == null) return null;

    PsiElement commonParent = PsiTreeUtil.findCommonParent(left, right);
    LOG.assertTrue(commonParent != null);
    LOG.assertTrue(commonParent.getTextRange() != null);

    while (commonParent.getParent() != null && commonParent.getTextRange().equals(commonParent.getParent().getTextRange())) {
      commonParent = commonParent.getParent();
    }
    return commonParent;
  }

  @Nullable
  public static PsiElement findElementAtInRoot(final PsiElement root, final int offset) {
    if (root instanceof PsiFile) {
      final PsiFile file = (PsiFile)root;
      final LanguageDialect dialect = file.getLanguageDialect();
      if (dialect != null) {
        final PsiElement element = file.getViewProvider().findElementAt(offset, dialect);
        if (element != null) return element;
      }
      return file.getViewProvider().findElementAt(offset, root.getLanguage());
    }
    return root.findElementAt(offset);
  }
}

package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.LanguageDialect;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CollectHighlightsUtil {
  private static ExtensionPointName<Condition<PsiElement>> EP_NAME = ExtensionPointName.create("com.intellij.elementsToHighlightFilter");

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
    final List<PsiElement> list = new ArrayList<PsiElement>(getElementsToHighlight(commonParent, startOffset, endOffset));

    PsiElement parent = commonParent;
    while (parent != null && parent != root) {
      list.add(parent);
      parent = includeAllParents ? parent.getParent() : null;
    }

    list.add(root);

    return Collections.unmodifiableList(list);
  }

  private static List<PsiElement> getElementsToHighlight(final PsiElement commonParent, final int startOffset, final int endOffset) {
    final List<PsiElement> result = new ArrayList<PsiElement>();
    final int currentOffset = commonParent.getTextRange().getStartOffset();
    final Condition<PsiElement>[] filters = Extensions.getExtensions(EP_NAME);

    final PsiElementVisitor visitor = new PsiRecursiveElementVisitor() {
      int offset = currentOffset;

      @Override public void visitElement(PsiElement element) {
        PsiElement child = element.getFirstChild();
        if (child != null) {
          // composite element
          while (child != null) {
            if (offset > endOffset) break;
            int start = offset;

            boolean skip = false;
            for (Condition<PsiElement> filter : filters) {
              if (!filter.value(child)) skip = true;
            }

            if (!skip) {
              child.accept(this);
              if (startOffset <= start && offset <= endOffset) result.add(child);
            }

            child = child.getNextSibling();
          }
        }
        else {
          // leaf element
          offset += element.getTextLength();
        }
      }
    };
    commonParent.accept(visitor);
    return result;
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

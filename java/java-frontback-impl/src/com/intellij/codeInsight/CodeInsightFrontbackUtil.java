// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Set;

public class CodeInsightFrontbackUtil {
  public static @Nullable PsiExpression findExpressionInRange(PsiFile file, int startOffset, int endOffset) {
    if (!file.getViewProvider().getLanguages().contains(JavaLanguage.INSTANCE)) return null;
    PsiExpression expression = findElementInRange(file, startOffset, endOffset, PsiExpression.class);
    if (expression == null && findStatementsInRange(file, startOffset, endOffset).length == 0) {
      PsiElement element2 = file.getViewProvider().findElementAt(endOffset - 1, JavaLanguage.INSTANCE);
      if (element2 instanceof PsiJavaToken token) {
        final IElementType tokenType = token.getTokenType();
        if (tokenType.equals(JavaTokenType.SEMICOLON) || element2.getParent() instanceof PsiErrorElement) {
          expression = findElementInRange(file, startOffset, element2.getTextRange().getStartOffset(), PsiExpression.class);
        }
      }
    }
    if (expression == null && findStatementsInRange(file, startOffset, endOffset).length == 0) {
      PsiElement element = PsiTreeUtil.skipWhitespacesBackward(file.findElementAt(endOffset));
      if (element != null) {
        element = PsiTreeUtil.skipWhitespacesAndCommentsBackward(element.getLastChild());
        if (element != null) {
          final int newEndOffset = element.getTextRange().getEndOffset();
          if (newEndOffset < endOffset) {
            expression = findExpressionInRange(file, startOffset, newEndOffset);
          }
        }
      }
    }
    if (expression instanceof PsiReferenceExpression && expression.getParent() instanceof PsiMethodCallExpression) return null;
    return expression;
  }

  public static <T extends PsiElement> T findElementInRange(PsiFile file, int startOffset, int endOffset, Class<T> klass) {
    return CodeInsightUtilCore.findElementInRange(file, startOffset, endOffset, klass, JavaLanguage.INSTANCE);
  }

  public static PsiElement @NotNull [] findStatementsInRange(@NotNull PsiFile file, int startOffset, int endOffset) {
    Language language = findJavaOrLikeLanguage(file);
    if (language == null) return PsiElement.EMPTY_ARRAY;
    FileViewProvider viewProvider = file.getViewProvider();
    PsiElement element1 = viewProvider.findElementAt(startOffset, language);
    PsiElement element2 = viewProvider.findElementAt(endOffset - 1, language);
    if (element1 instanceof PsiWhiteSpace) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.findElementAt(startOffset);
    }
    if (element2 instanceof PsiWhiteSpace) {
      endOffset = element2.getTextRange().getStartOffset();
      element2 = file.findElementAt(endOffset - 1);
    }
    if (element1 == null || element2 == null) return PsiElement.EMPTY_ARRAY;

    PsiElement parent = PsiTreeUtil.findCommonParent(element1, element2);
    if (parent == null) return PsiElement.EMPTY_ARRAY;
    while (true) {
      if (parent instanceof PsiStatement) {
        if (!(element1 instanceof PsiComment)) {
          parent = parent.getParent();
        }
        break;
      }
      if (parent instanceof PsiCodeBlock) break;
      if (FileTypeUtils.isInServerPageFile(parent) && parent instanceof PsiFile) break;
      if (parent instanceof PsiCodeFragment) break;
      if (parent == null || parent instanceof PsiFile) return PsiElement.EMPTY_ARRAY;
      parent = parent.getParent();
    }

    if (!parent.equals(element1)) {
      while (!parent.equals(element1.getParent())) {
        element1 = element1.getParent();
      }
    }
    if (startOffset != element1.getTextRange().getStartOffset()) return PsiElement.EMPTY_ARRAY;

    if (!parent.equals(element2)) {
      while (!parent.equals(element2.getParent())) {
        element2 = element2.getParent();
      }
    }
    if (endOffset != element2.getTextRange().getEndOffset() && !isAtTrailingComment(element1, element2, endOffset)) {
      return PsiElement.EMPTY_ARRAY;
    }

    if (parent instanceof PsiCodeBlock &&
        element1 == ((PsiCodeBlock)parent).getLBrace() && element2 == ((PsiCodeBlock)parent).getRBrace()) {
      if (parent.getParent() instanceof PsiBlockStatement) {
        return new PsiElement[]{parent.getParent()};
      }
      PsiElement[] children = parent.getChildren();
      return getStatementsInRange(children, ((PsiCodeBlock)parent).getFirstBodyElement(), ((PsiCodeBlock)parent).getLastBodyElement());
    }

    PsiElement[] children = parent.getChildren();
    return getStatementsInRange(children, element1, element2);
  }

  private static boolean isAtTrailingComment(PsiElement element1, PsiElement element2, int offset) {
    if (element1 == element2 && element1 instanceof PsiExpressionStatement) {
      for (PsiElement child = element1.getLastChild(); child != null; child = child.getPrevSibling()) {
        if (PsiUtil.isJavaToken(child, JavaTokenType.SEMICOLON) && child.getTextRange().getEndOffset() == offset) {
          return false; // findExpressionInRange() counts this as an expression - don't interfere with it
        }
      }
    }
    PsiElement trailing = element2;
    while (trailing.getTextRange().contains(offset) && trailing.getLastChild() != null) {
      trailing = trailing.getLastChild();
    }
    while (trailing instanceof PsiComment || trailing instanceof PsiWhiteSpace) {
      PsiElement previous = trailing.getPrevSibling();
      if (trailing.getTextRange().contains(offset)) {
        return true;
      }
      trailing = previous;
    }
    return false;
  }

  private static @Nullable Language findJavaOrLikeLanguage(final @NotNull PsiFile file) {
    final Set<Language> languages = file.getViewProvider().getLanguages();
    if (languages.contains(JavaLanguage.INSTANCE)) return JavaLanguage.INSTANCE;
    for (final Language language : languages) {
      if (language.isKindOf(JavaLanguage.INSTANCE)) return language;
    }
    return null;
  }

  private static PsiElement @NotNull [] getStatementsInRange(PsiElement[] children, PsiElement element1, PsiElement element2) {
    ArrayList<PsiElement> array = new ArrayList<>();
    boolean flag = false;
    for (PsiElement child : children) {
      if (child.equals(element1)) {
        flag = true;
      }
      if (flag && !(child instanceof PsiWhiteSpace)) {
        array.add(child);
      }
      if (child.equals(element2)) {
        break;
      }
    }

    for (PsiElement element : array) {
      if (!(element instanceof PsiStatement || element instanceof PsiWhiteSpace || element instanceof PsiComment)) {
        return PsiElement.EMPTY_ARRAY;
      }
    }

    return PsiUtilCore.toPsiElementArray(array);
  }
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 *
 */

public class MethodOrClassSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return (e instanceof PsiClass && !(e instanceof PsiTypeParameter) || e instanceof PsiMethod) &&
           e.getLanguage() == JavaLanguage.INSTANCE;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    List<TextRange> result = new ArrayList<>();

    PsiElement firstChild = e.getFirstChild();
    PsiElement[] children = e.getChildren();
    int i = 1;

    if (firstChild instanceof PsiDocComment) {
      while (children[i] instanceof PsiWhiteSpace) {
        i++;
      }

      TextRange range = new TextRange(children[i].getTextRange().getStartOffset(), e.getTextRange().getEndOffset());
      result.add(range);
      result.addAll(expandToWholeLinesWithBlanks(editorText, range));

      range = TextRange.create(firstChild.getTextRange());
      result.addAll(expandToWholeLinesWithBlanks(editorText, range));

      firstChild = children[i++];
    }
    if (firstChild instanceof PsiComment) {
      while (children[i] instanceof PsiComment || children[i] instanceof PsiWhiteSpace) {
        i++;
      }
      PsiElement last = children[i - 1] instanceof PsiWhiteSpace ? children[i - 2] : children[i - 1];
      TextRange range = new TextRange(firstChild.getTextRange().getStartOffset(), last.getTextRange().getEndOffset());
      if (range.contains(cursorOffset)) {
        result.addAll(expandToWholeLinesWithBlanks(editorText, range));
      }

      range = new TextRange(children[i].getTextRange().getStartOffset(), e.getTextRange().getEndOffset());
      result.add(range);
      result.addAll(expandToWholeLinesWithBlanks(editorText, range));
    }

    result.add(e.getTextRange());
    result.addAll(expandToWholeLinesWithBlanks(editorText, e.getTextRange()));

    if (e instanceof PsiClass) {
      result.addAll(selectWithTypeParameters((PsiClass)e));
      result.addAll(selectBetweenBracesLines(children, editorText));
    }
    if (e instanceof PsiAnonymousClass) {
      result.addAll(selectWholeBlock((PsiAnonymousClass)e));
    }

    return result;
  }

  private static Collection<TextRange> selectWithTypeParameters(@NotNull PsiClass psiClass) {
    final PsiIdentifier identifier = psiClass.getNameIdentifier();
    final PsiTypeParameterList list = psiClass.getTypeParameterList();
    if (identifier != null && list != null) {
      return Collections.singletonList(new TextRange(identifier.getTextRange().getStartOffset(), list.getTextRange().getEndOffset()));
    }
    return Collections.emptyList();
  }

  private static Collection<TextRange> selectBetweenBracesLines(@NotNull PsiElement[] children,
                                                                @NotNull CharSequence editorText) {
    int start = CodeBlockOrInitializerSelectioner.findOpeningBrace(children);
    // in non-Java PsiClasses, there can be no opening brace
    if (start != 0) {
      int end = CodeBlockOrInitializerSelectioner.findClosingBrace(children, start);

      return expandToWholeLinesWithBlanks(editorText, new TextRange(start, end));
    }
    return Collections.emptyList();
  }

  private static Collection<TextRange> selectWholeBlock(PsiClass c) {
    PsiJavaToken[] tokens = PsiTreeUtil.getChildrenOfType(c, PsiJavaToken.class);
    if (tokens != null && tokens.length == 2 &&
        tokens[0].getTokenType() == JavaTokenType.LBRACE &&
        tokens[1].getTokenType() == JavaTokenType.RBRACE) {
      return Collections.singleton(new TextRange(tokens[0].getTextRange().getStartOffset(), tokens[1].getTextRange().getEndOffset()));
    }
    return Collections.emptyList();
  }
}

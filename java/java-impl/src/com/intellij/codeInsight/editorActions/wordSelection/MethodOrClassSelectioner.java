/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 *
 */

public class MethodOrClassSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(PsiElement e) {
    return (e instanceof PsiClass && !(e instanceof PsiTypeParameter) || e instanceof PsiMethod) &&
           e.getLanguage() == JavaLanguage.INSTANCE;
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = ContainerUtil.newArrayList();

    PsiElement firstChild = e.getFirstChild();
    PsiElement[] children = e.getChildren();

    if (firstChild instanceof PsiDocComment) {
      int i = 1;

      while (children[i] instanceof PsiWhiteSpace) {
        i++;
      }

      TextRange range = new TextRange(children[i].getTextRange().getStartOffset(), e.getTextRange().getEndOffset());
      result.add(range);
      result.addAll(expandToWholeLinesWithBlanks(editorText, range));

      range = TextRange.create(firstChild.getTextRange());
      result.addAll(expandToWholeLinesWithBlanks(editorText, range));
    }
    else if (firstChild instanceof PsiComment) {
      int i = 1;

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
}

/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.ASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.javadoc.CommentFormatter;
import com.intellij.psi.javadoc.PsiDocComment;
import org.jetbrains.annotations.NotNull;

public class FormatCommentsProcessor implements PreFormatProcessor {
  @NotNull
  @Override
  public TextRange process(@NotNull final ASTNode element, @NotNull final TextRange range) {
    PsiElement e = SourceTreeToPsiMap.treeElementToPsi(element);
    assert e != null && e.isValid();
    final PsiFile file = e.getContainingFile();
    final Project project = e.getProject();
    if (!CodeStyle.getCustomSettings(file, JavaCodeStyleSettings.class).ENABLE_JAVADOC_FORMATTING ||
        element.getPsi().getContainingFile().getLanguage() != JavaLanguage.INSTANCE
        || InjectedLanguageManager.getInstance(project).isInjectedFragment(element.getPsi().getContainingFile()))
    {
      return range;
    }
    return formatCommentsInner(project, element, range);
  }

  /**
   * Formats PsiDocComments of current ASTNode element and all his children PsiDocComments
   */
  @NotNull
  private static TextRange formatCommentsInner(@NotNull Project project, @NotNull ASTNode element, @NotNull final TextRange markedRange) {
    TextRange resultTextRange = markedRange;
    final PsiElement elementPsi = element.getPsi();
    assert elementPsi.isValid();
    final PsiFile file = elementPsi.getContainingFile();
    boolean shouldFormat = markedRange.contains(element.getTextRange());

    if (shouldFormat) {

      final ASTNode rangeAnchor;
      // There are two possible cases:
      //   1. Given element correspond to comment's owner (e.g. field or method);
      //   2. Given element corresponds to comment itself;
      // However, doc comment formatter replaces old comment with the new one, hence, old element becomes invalid. That's why we need
      // to calculate text length delta not for the given comment element (it's invalid because removed from the AST tree) but for
      // its parent.
      if (elementPsi instanceof PsiDocComment) {
        rangeAnchor = element.getTreeParent();
      }
      else {
        rangeAnchor = element;
      }
      TextRange before = rangeAnchor.getTextRange();
      new CommentFormatter(file).processComment(element);
      int deltaRange = rangeAnchor.getTextRange().getLength() - before.getLength();
      resultTextRange = new TextRange(markedRange.getStartOffset(), markedRange.getEndOffset() + deltaRange);
    }

    
    // If element is Psi{Method, Field, DocComment} and was formatted there is no reason to continue - we formatted all possible javadocs.
    // If element is out of range its children are also out of range. So in both cases formatting is finished. It's just for optimization.
    if ((shouldFormat && (elementPsi instanceof  PsiMethod || elementPsi instanceof PsiField || elementPsi instanceof PsiDocComment))
        || markedRange.getEndOffset() < element.getStartOffset())
    {
      return resultTextRange;
    }

    ASTNode current = element.getFirstChildNode();
    while (current != null) {
      // When element is PsiClass its PsiDocComment is formatted up to this moment, so we didn't need to format it again.
      if (!(shouldFormat && current.getPsi() instanceof PsiDocComment && elementPsi instanceof PsiClass)) {
        resultTextRange = formatCommentsInner(project, current, resultTextRange);
      }
      current = current.getTreeNext();
    }

    return resultTextRange;
  }

}

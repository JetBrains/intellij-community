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

import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.javadoc.CommentFormatter;
import com.intellij.psi.javadoc.PsiDocComment;
import org.jetbrains.annotations.NotNull;

public class FormatCommentsProcessor implements PreFormatProcessor {
  @NotNull
  @Override
  public TextRange process(@NotNull final ASTNode element, @NotNull final TextRange range) {
    final Project project = SourceTreeToPsiMap.treeElementToPsi(element).getProject();
    if (!CodeStyleSettingsManager.getSettings(project).ENABLE_JAVADOC_FORMATTING ||
        element.getPsi().getContainingFile().getLanguage() != StdLanguages.JAVA) {
      return range;
    }

    return formatCommentsInner(project, element, range);
  }

  private static TextRange formatCommentsInner(Project project, ASTNode element, final TextRange range) {
    TextRange result = range;


    // check for RepositoryTreeElement is optimization
    if (shouldProcess(element)) {
      final TextRange elementRange = element.getTextRange();

      if (range.contains(elementRange)) {
        new CommentFormatter(project).process(element);
        final TextRange newRange = element.getTextRange();
        result = new TextRange(range.getStartOffset(), range.getEndOffset() + newRange.getLength() - elementRange.getLength());
      }

      // optimization, does not seek PsiDocComment inside fields / methods or out of range
      if (element.getPsi() instanceof PsiField ||
          element.getPsi() instanceof PsiMethod ||
          element instanceof PsiDocComment ||
          range.getEndOffset() < elementRange.getStartOffset()
         ) {
        return result;
      }
    }

    ASTNode current = element.getFirstChildNode();
    while (current != null) {
      // we expand the chameleons here for effectiveness
      current.getFirstChildNode();
      result = formatCommentsInner(project, current, result);
      current = current.getTreeNext();
    }
    return result;
  }

  private static boolean shouldProcess(final ASTNode element) {
    if (element instanceof PsiDocComment) {
      return true;
    }
    else {
      return true;//element.getElementType() instanceof JavaStubElementType &&
          //(element.getPsi()) instanceof PsiDocCommentOwner;
    }
  }

}

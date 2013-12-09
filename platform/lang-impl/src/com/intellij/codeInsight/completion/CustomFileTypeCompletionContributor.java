/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.impl.CustomSyntaxTableFileType;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.PlatformPatterns.psiFile;
import static com.intellij.patterns.StandardPatterns.instanceOf;

/**
 * @author yole
 */
public class CustomFileTypeCompletionContributor extends CompletionContributor {
  public CustomFileTypeCompletionContributor() {
    extend(CompletionType.BASIC, psiElement().inFile(psiFile().withFileType(instanceOf(CustomSyntaxTableFileType.class))),
           new CompletionProvider<CompletionParameters>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               if (inCommentOrLiteral(parameters)) {
                 return;
               }

               CustomSyntaxTableFileType fileType = (CustomSyntaxTableFileType)parameters.getOriginalFile().getFileType();
               SyntaxTable syntaxTable = fileType.getSyntaxTable();
               String prefix = findPrefix(parameters.getPosition(), parameters.getOffset());
               CompletionResultSet resultSetWithPrefix = result.withPrefixMatcher(prefix);

               addVariants(resultSetWithPrefix, syntaxTable.getKeywords1());
               addVariants(resultSetWithPrefix, syntaxTable.getKeywords2());
               addVariants(resultSetWithPrefix, syntaxTable.getKeywords3());
               addVariants(resultSetWithPrefix, syntaxTable.getKeywords4());
             }
           });
  }

  private static boolean inCommentOrLiteral(CompletionParameters parameters) {
    HighlighterIterator iterator = ((EditorEx)parameters.getEditor()).getHighlighter().createIterator(parameters.getOffset());
    IElementType elementType = iterator.getTokenType();
    if (elementType == CustomHighlighterTokenType.WHITESPACE) {
      iterator.retreat();
      elementType = iterator.getTokenType();
    }
    return elementType == CustomHighlighterTokenType.LINE_COMMENT ||
           elementType == CustomHighlighterTokenType.MULTI_LINE_COMMENT ||
           elementType == CustomHighlighterTokenType.STRING ||
           elementType == CustomHighlighterTokenType.SINGLE_QUOTED_STRING;
  }

  private static void addVariants(CompletionResultSet resultSet, Set<String> keywords) {
    for (String keyword : keywords) {
      resultSet.addElement(LookupElementBuilder.create(keyword));
    }
  }

  private static String findPrefix(PsiElement insertedElement, int offset) {
    String text = insertedElement.getText();
    int offsetInElement = offset - insertedElement.getTextOffset();
    int start = offsetInElement - 1;
    while(start >=0 ) {
      if(!Character.isJavaIdentifierStart(text.charAt(start))) break;
      --start;
    }
    return text.substring(start+1, offsetInElement).trim();
  }

}

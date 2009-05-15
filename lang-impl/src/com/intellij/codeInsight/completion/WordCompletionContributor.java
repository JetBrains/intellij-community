/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageWordCompletion;
import com.intellij.psi.PlainTextTokenTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.getters.AllWordsGetter;
import com.intellij.psi.tree.IElementType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import static com.intellij.patterns.StandardPatterns.*;

import java.util.Set;
import java.util.HashSet;

/**
 * @author peter
 */
public class WordCompletionContributor extends CompletionContributor{

  @Override
  public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {
    if (parameters.getCompletionType() != CompletionType.BASIC) return;

    final PsiFile file = parameters.getOriginalFile();
    final int startOffset = parameters.getOffset();
    final PsiElement element = ApplicationManager.getApplication().runReadAction(new Computable<PsiElement>() {
      public PsiElement compute() {
        return file.findElementAt(startOffset - 1);
      }
    });
    final PsiElement insertedElement = parameters.getPosition();

    final CompletionData data = CompletionUtil.getCompletionDataByElement(file);
    if (!(data instanceof SyntaxTableCompletionData)) {
      Set<CompletionVariant> toAdd = new HashSet<CompletionVariant>();
      data.addKeywordVariants(toAdd, insertedElement, file);
      for (CompletionVariant completionVariant : toAdd) {
        if (completionVariant.hasKeywordCompletions()) {
          return;
        }
      }
    }

    final PsiReference reference = ApplicationManager.getApplication().runReadAction(new Computable<PsiReference>() {
      public PsiReference compute() {
        return file.findReferenceAt(startOffset);
      }
    });
    if (reference == null) {
      ASTNode textContainer = element != null ? element.getNode() : null;
      while (textContainer != null) {
        final IElementType elementType = textContainer.getElementType();
        if (LanguageWordCompletion.INSTANCE.isEnabledIn(elementType) || elementType == PlainTextTokenTypes.PLAIN_TEXT) {
          final CompletionResultSet javaResultSet = result.withPrefixMatcher(CompletionUtil.findJavaIdentifierPrefix(insertedElement, startOffset));
          final CompletionResultSet plainResultSet = result.withPrefixMatcher(CompletionUtil.findIdentifierPrefix(insertedElement,
                                                                                                                  startOffset,
                                                                                                                  character().javaIdentifierPart().andNot(character().equalTo('$')),
                                                                                                                  character().javaIdentifierStart()));
          for (final String word : AllWordsGetter.getAllWords(insertedElement, startOffset)) {
            final LookupItem<String> item = new LookupItem<String>(word, word).setTailType(TailType.SPACE);
            javaResultSet.addElement(item);
            plainResultSet.addElement(item);
          }
        }
        textContainer = textContainer.getTreeParent();
      }
    }
  }
}

/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.TailType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageWordCompletion;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Computable;
import static com.intellij.patterns.StandardPatterns.character;
import com.intellij.psi.PlainTextTokenTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.getters.AllWordsGetter;
import com.intellij.psi.tree.IElementType;

import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public class WordCompletionContributor extends CompletionContributor implements DumbAware {

  @Override
  public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {
    if (parameters.getCompletionType() == CompletionType.BASIC && shouldPerformWordCompletion(parameters)) {
      addWordCompletionWariants(result, parameters);
    }
  }

  public static void addWordCompletionWariants(CompletionResultSet result, CompletionParameters parameters) {
    int startOffset = parameters.getOffset();
    PsiElement insertedElement = parameters.getPosition();
    final CompletionResultSet javaResultSet = result.withPrefixMatcher(CompletionUtil.findJavaIdentifierPrefix(insertedElement, startOffset));
    final CompletionResultSet plainResultSet = result.withPrefixMatcher(CompletionUtil.findIdentifierPrefix(insertedElement,
                                                                                                            startOffset,
                                                                                                            character().javaIdentifierPart().andNot(character().equalTo('$')),
                                                                                                            character().javaIdentifierStart()));
    for (final String word : AllWordsGetter.getAllWords(insertedElement, startOffset)) {
      final LookupElement item = TailTypeDecorator.createDecorator(LookupElementBuilder.create(word), TailType.SPACE);
      javaResultSet.addElement(item);
      plainResultSet.addElement(item);
    }
  }

  private static boolean shouldPerformWordCompletion(CompletionParameters parameters) {
    final PsiElement insertedElement = parameters.getPosition();
    final boolean dumb = DumbService.getInstance(insertedElement.getProject()).isDumb();
    if (dumb) {
      return true;
    }

    final PsiFile file = insertedElement.getContainingFile();
    final CompletionData data = CompletionUtil.getCompletionDataByElement(file);
    if (!(data instanceof SyntaxTableCompletionData)) {
      Set<CompletionVariant> toAdd = new HashSet<CompletionVariant>();
      data.addKeywordVariants(toAdd, insertedElement, file);
      for (CompletionVariant completionVariant : toAdd) {
        if (completionVariant.hasKeywordCompletions()) {
          return false;
        }
      }
    }

    final int startOffset = parameters.getOffset();

    final PsiReference reference = ApplicationManager.getApplication().runReadAction(new Computable<PsiReference>() {
      public PsiReference compute() {
        return file.findReferenceAt(startOffset);
      }
    });
    if (reference != null) {
      return false;
    }

    final PsiElement element = ApplicationManager.getApplication().runReadAction(new Computable<PsiElement>() {
      public PsiElement compute() {
        return file.findElementAt(startOffset - 1);
      }
    });

    ASTNode textContainer = element != null ? element.getNode() : null;
    while (textContainer != null) {
      final IElementType elementType = textContainer.getElementType();
      if (LanguageWordCompletion.INSTANCE.isEnabledIn(elementType) || elementType == PlainTextTokenTypes.PLAIN_TEXT) {
        return true;
      }
      textContainer = textContainer.getTreeParent();
    }
    return false;
  }
}

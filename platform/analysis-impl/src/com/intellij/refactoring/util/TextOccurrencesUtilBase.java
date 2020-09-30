// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.util;

import com.intellij.find.findUsages.FindUsagesHelper;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageInfoFactory;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class TextOccurrencesUtilBase {

  private TextOccurrencesUtilBase() {
  }

  public static void addTextOccurrences(@NotNull PsiElement element,
                                        @NotNull String stringToSearch,
                                        @NotNull GlobalSearchScope searchScope,
                                        @NotNull Collection<? super UsageInfo> results,
                                        @NotNull UsageInfoFactory factory) {
    FindUsagesHelper.processTextOccurrences(element, stringToSearch, searchScope, factory, t -> {
      results.add(t);
      return true;
    });
  }

  public static boolean processUsagesInStringsAndComments(
    @NotNull Processor<? super UsageInfo> processor,
    @NotNull PsiElement element,
    @NotNull SearchScope searchScope,
    @NotNull String stringToSearch,
    @NotNull UsageInfoFactory factory
  ) {
    return processUsagesInStringsAndComments(element, searchScope, stringToSearch, false, (commentOrLiteral, textRange) -> {
      UsageInfo usageInfo = factory.createUsageInfo(commentOrLiteral, textRange.getStartOffset(), textRange.getEndOffset());
      if (usageInfo != null && !processor.process(usageInfo)) return false;
      return true;
    });
  }

  public static boolean processUsagesInStringsAndComments(@NotNull PsiElement element,
                                                   @NotNull SearchScope searchScope,
                                                   @NotNull String stringToSearch,
                                                   boolean ignoreReferences,
                                                   @NotNull PairProcessor<? super PsiElement, ? super TextRange> processor) {
    PsiSearchHelper helper = PsiSearchHelper.getInstance(element.getProject());
    SearchScope scope = helper.getUseScope(element);
    scope = scope.intersectWith(searchScope);
    Processor<PsiElement> commentOrLiteralProcessor = literal -> processTextIn(literal, stringToSearch, ignoreReferences, processor);
    return processStringLiteralsContainingIdentifier(stringToSearch, scope, helper, commentOrLiteralProcessor) &&
           helper.processCommentsContainingIdentifier(stringToSearch, scope, commentOrLiteralProcessor);
  }

  private static boolean processStringLiteralsContainingIdentifier(@NotNull String identifier,
                                                                   @NotNull SearchScope searchScope,
                                                                   PsiSearchHelper helper,
                                                                   final Processor<? super PsiElement> processor) {
    TextOccurenceProcessor occurenceProcessor = (element, offsetInElement) -> {
      if (isStringLiteralElement(element)) {
        return processor.process(element);
      }
      return true;
    };

    return helper.processElementsWithWord(occurenceProcessor, searchScope, identifier, UsageSearchContext.IN_STRINGS, true);
  }

  public static boolean isStringLiteralElement(@NotNull PsiElement element) {
    final ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(element.getLanguage());
    if (definition == null) {
      return false;
    }
    final ASTNode node = element.getNode();
    return node != null && definition.getStringLiteralElements().contains(node.getElementType());
  }

  private static boolean processTextIn(PsiElement scope,
                                       String stringToSearch,
                                       boolean ignoreReferences,
                                       PairProcessor<? super PsiElement, ? super TextRange> processor) {
    String text = scope.getText();
    for (int offset = 0; offset < text.length(); offset++) {
      offset = text.indexOf(stringToSearch, offset);
      if (offset < 0) break;
      final PsiReference referenceAt = scope.findReferenceAt(offset);
      if (!ignoreReferences && referenceAt != null
          && (referenceAt.resolve() != null || referenceAt instanceof PsiPolyVariantReference
                                               && ((PsiPolyVariantReference)referenceAt).multiResolve(true).length > 0)) {
        continue;
      }

      if (offset > 0) {
        char c = text.charAt(offset - 1);
        if (Character.isJavaIdentifierPart(c) && c != '$') {
          if (offset < 2 || text.charAt(offset - 2) != '\\') continue;  //escape sequence
        }
      }

      if (offset + stringToSearch.length() < text.length()) {
        char c = text.charAt(offset + stringToSearch.length());
        if (Character.isJavaIdentifierPart(c) && c != '$') {
          continue;
        }
      }

      TextRange textRange = new TextRange(offset, offset + stringToSearch.length());
      if (!processor.process(scope, textRange)) {
        return false;
      }

      offset += stringToSearch.length();
    }
    return true;
  }

  public static void addUsagesInStringsAndComments(@NotNull PsiElement element,
                                            @NotNull SearchScope searchScope,
                                            @NotNull String stringToSearch,
                                            @NotNull Collection<? super UsageInfo> results,
                                            @NotNull UsageInfoFactory factory) {
    Object lock = new Object();
    processUsagesInStringsAndComments(element, searchScope, stringToSearch, false, (commentOrLiteral, textRange) -> {
      UsageInfo usageInfo = factory.createUsageInfo(commentOrLiteral, textRange.getStartOffset(), textRange.getEndOffset());
      if (usageInfo != null) {
        synchronized (lock) {
          results.add(usageInfo);
        }
      }
      return true;
    });
  }
}

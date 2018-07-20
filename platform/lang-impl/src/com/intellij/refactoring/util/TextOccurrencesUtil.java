// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.util;

import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.FindUsagesUtil;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.search.PsiSearchHelperImpl;
import com.intellij.psi.search.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageInfoFactory;
import com.intellij.util.PairProcessor;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class TextOccurrencesUtil {
  private TextOccurrencesUtil() {
  }

  public static void addTextOccurences(@NotNull PsiElement element,
                                       @NotNull String stringToSearch,
                                       @NotNull GlobalSearchScope searchScope,
                                       @NotNull final Collection<UsageInfo> results,
                                       @NotNull final UsageInfoFactory factory) {
    PsiSearchHelperImpl.processTextOccurrences(element, stringToSearch, searchScope, t -> {
      results.add(t);
      return true;
    }, factory);
  }

  private static boolean processStringLiteralsContainingIdentifier(@NotNull String identifier, @NotNull SearchScope searchScope, PsiSearchHelper helper, final Processor<PsiElement> processor) {
    TextOccurenceProcessor occurenceProcessor = (element, offsetInElement) -> {
      final ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(element.getLanguage());
      final ASTNode node = element.getNode();
      if (definition != null && node != null && definition.getStringLiteralElements().contains(node.getElementType())) {
        return processor.process(element);
      }
      return true;
    };

    return helper.processElementsWithWord(occurenceProcessor,
                                   searchScope,
                                   identifier,
                                   UsageSearchContext.IN_STRINGS,
                                   true);
  }

  public static boolean processUsagesInStringsAndComments(@NotNull final PsiElement element,
                                                          @NotNull final String stringToSearch,
                                                          final boolean ignoreReferences,
                                                          @NotNull final PairProcessor<PsiElement, TextRange> processor) {
    PsiSearchHelper helper = PsiSearchHelper.getInstance(element.getProject());
    SearchScope scope = helper.getUseScope(element);
    scope = scope.intersectWith(GlobalSearchScope.projectScope(element.getProject()));
    Processor<PsiElement> commentOrLiteralProcessor = literal -> processTextIn(literal, stringToSearch, ignoreReferences, processor);
    return processStringLiteralsContainingIdentifier(stringToSearch, scope, helper, commentOrLiteralProcessor) &&
           helper.processCommentsContainingIdentifier(stringToSearch, scope, commentOrLiteralProcessor);
  }

  public static void addUsagesInStringsAndComments(@NotNull PsiElement element,
                                                   @NotNull String stringToSearch,
                                                   @NotNull final Collection<UsageInfo> results,
                                                   @NotNull final UsageInfoFactory factory) {
    final Object lock = new Object();
    processUsagesInStringsAndComments(element, stringToSearch, false, (commentOrLiteral, textRange) -> {
      UsageInfo usageInfo = factory.createUsageInfo(commentOrLiteral, textRange.getStartOffset(), textRange.getEndOffset());
      if (usageInfo != null) {
        synchronized (lock) {
          results.add(usageInfo);
        }
      }
      return true;
    });
  }

  private static boolean processTextIn(PsiElement scope, String stringToSearch, final boolean ignoreReferences, PairProcessor<PsiElement, TextRange> processor) {
    String text = scope.getText();
    for (int offset = 0; offset < text.length(); offset++) {
      offset = text.indexOf(stringToSearch, offset);
      if (offset < 0) break;
      final PsiReference referenceAt = scope.findReferenceAt(offset);
      if (!ignoreReferences && referenceAt != null
          && (referenceAt.resolve() != null || referenceAt instanceof PsiPolyVariantReference
                                               && ((PsiPolyVariantReference)referenceAt).multiResolve(true).length > 0)) continue;

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

  public static boolean isSearchTextOccurencesEnabled(@NotNull PsiElement element) {
    final FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(element.getProject())).getFindUsagesManager();
    final FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(element, true);
    return FindUsagesUtil.isSearchForTextOccurrencesAvailable(element, false, handler);
  }

  public static void findNonCodeUsages(PsiElement element, String stringToSearch, boolean searchInStringsAndComments,
                                       boolean searchInNonJavaFiles, String newQName, Collection<UsageInfo> results) {
    if (searchInStringsAndComments || searchInNonJavaFiles) {
      UsageInfoFactory factory = createUsageInfoFactory(element, newQName);

      if (searchInStringsAndComments) {
        addUsagesInStringsAndComments(element, stringToSearch, results, factory);
      }

      if (searchInNonJavaFiles) {
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(element.getProject());
        addTextOccurences(element, stringToSearch, projectScope, results, factory);
      }
    }
  }

  private static UsageInfoFactory createUsageInfoFactory(final PsiElement element,
                                                        final String newQName) {
    return (usage, startOffset, endOffset) -> {
      int start = usage.getTextRange().getStartOffset();
      return NonCodeUsageInfo.create(usage.getContainingFile(), start + startOffset, start + endOffset, element,
                                     newQName);
    };
  }
}

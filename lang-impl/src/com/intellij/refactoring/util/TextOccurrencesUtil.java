package com.intellij.refactoring.util;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TextOccurrencesUtil {
  private TextOccurrencesUtil() {
  }

  public static void addTextOccurences(PsiElement element,
                                       @NotNull String stringToSearch,
                                       GlobalSearchScope searchScope,
                                       final List<UsageInfo> results,
                                       final UsageInfoFactory factory) {
    processTextOccurences(element, stringToSearch, searchScope, new Processor<UsageInfo>() {
      public boolean process(UsageInfo t) {
        results.add(t);
        return true;
      }
    }, factory);
  }

  public static void processTextOccurences(PsiElement element,
                                           @NotNull String stringToSearch,
                                           GlobalSearchScope searchScope,
                                           final Processor<UsageInfo> processor,
                                           final UsageInfoFactory factory) {
    PsiSearchHelper helper = element.getManager().getSearchHelper();

    helper.processUsagesInNonJavaFiles(element, stringToSearch, new PsiNonJavaFileReferenceProcessor() {
      public boolean process(PsiFile psiFile, int startOffset, int endOffset) {
        UsageInfo usageInfo = factory.createUsageInfo(psiFile, startOffset, endOffset);
        return usageInfo == null || processor.process(usageInfo);
      }
    }, searchScope);
  }

  public static PsiElement[] findStringLiteralsContainingIdentifier(@NotNull String identifier, @NotNull SearchScope searchScope, PsiSearchHelper helper) {
    final ArrayList<PsiElement> results = new ArrayList<PsiElement>();
    TextOccurenceProcessor processor = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        final ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(element.getLanguage());
        final ASTNode node = element.getNode();
        if (node != null && definition.getStringLiteralElements().contains(node.getElementType())) {
          synchronized (results) {
            results.add(element);
          }
        }
        return true;
      }
    };

    helper.processElementsWithWord(processor,
                                   searchScope,
                                   identifier,
                                   UsageSearchContext.IN_STRINGS,
                                   true);
    return results.toArray(new PsiElement[results.size()]);
  }

  public static void addUsagesInStringsAndComments(final PsiElement element, final String stringToSearch, final List<UsageInfo> results,
                                                   final UsageInfoFactory factory,
                                                   final boolean ignoreReferences) {
    PsiManager manager = element.getManager();
    PsiSearchHelper helper = manager.getSearchHelper();
    SearchScope scope = element.getUseScope();
    scope = scope.intersectWith(GlobalSearchScope.projectScope(manager.getProject()));
    PsiElement[] literals = findStringLiteralsContainingIdentifier(stringToSearch, scope, helper);
    for (PsiElement literal : literals) {
      processStringOrComment(literal, stringToSearch, results, factory, ignoreReferences);
    }

    PsiElement[] comments = helper.findCommentsContainingIdentifier(stringToSearch, scope);
    for (PsiElement comment : comments) {
      processStringOrComment(comment, stringToSearch, results, factory, ignoreReferences);
    }
  }

  public static void addUsagesInStringsAndComments(PsiElement element,
                                                   @NotNull String stringToSearch,
                                                   List<UsageInfo> results,
                                                   UsageInfoFactory factory) {
    addUsagesInStringsAndComments(element, stringToSearch, results, factory, false);
  }

  private static void processStringOrComment(PsiElement element, String stringToSearch, List<UsageInfo> results, UsageInfoFactory factory,
                                             final boolean ignoreReferences) {
    String elementText = element.getText();
    for (int index = 0; index < elementText.length(); index++) {
      index = elementText.indexOf(stringToSearch, index);
      if (index < 0) break;
      final PsiReference referenceAt = element.findReferenceAt(index);
      if (!ignoreReferences && referenceAt != null && referenceAt.resolve() != null) continue;

      if (index > 0) {
        char c = elementText.charAt(index - 1);
        if (Character.isJavaIdentifierPart(c) && c != '$') {
          if (index < 2 || elementText.charAt(index - 2) != '\\') continue;  //escape sequence
        }
      }

      if (index + stringToSearch.length() < elementText.length()) {
        char c = elementText.charAt(index + stringToSearch.length());
        if (Character.isJavaIdentifierPart(c) && c != '$') {
          continue;
        }
      }

      UsageInfo usageInfo = factory.createUsageInfo(element, index, index + stringToSearch.length());
      if (usageInfo != null) {
        results.add(usageInfo);
      }

      index += stringToSearch.length();
    }
  }

  public static boolean isSearchTextOccurencesEnabled(PsiElement element) {
    return ElementDescriptionUtil.getElementDescription(element, NonCodeSearchDescriptionLocation.NON_JAVA) != null;
  }

  public static interface UsageInfoFactory {
    UsageInfo createUsageInfo(@NotNull PsiElement usage, int startOffset, int endOffset);
  }
}

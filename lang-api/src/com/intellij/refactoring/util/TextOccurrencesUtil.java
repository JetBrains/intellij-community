package com.intellij.refactoring.util;

import com.intellij.usageView.UsageInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.PsiNonJavaFileReferenceProcessor;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

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

  public static interface UsageInfoFactory {
    UsageInfo createUsageInfo(@NotNull PsiElement usage, int startOffset, int endOffset);
  }
}

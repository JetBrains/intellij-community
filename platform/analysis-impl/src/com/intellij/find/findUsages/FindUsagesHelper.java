// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.stubs.BinaryFileStubBuilders;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageInfoFactory;
import com.intellij.util.Processor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class FindUsagesHelper {
  private static final Logger LOG = Logger.getInstance(FindUsagesHelper.class);

  public static boolean processUsagesInText(final @NotNull PsiElement element,
                                            @NotNull Collection<String> stringToSearch,
                                            boolean equivalentReferencesOnly,
                                            @NotNull GlobalSearchScope searchScope,
                                            @NotNull Processor<? super UsageInfo> processor) {
    TextRange elementTextRange = ReadAction.computeBlocking(
      () -> {
        if (!element.isValid()) {
          return null;
        }
        VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
        if (virtualFile == null || BinaryFileStubBuilders.INSTANCE.forFileType(virtualFile.getFileType()) != null) {
          return null;
        }
        return element.getTextRange();
      });
    UsageInfoFactory factory = (usage, startOffset, endOffset) -> {
      if (!element.isValid()) return equivalentReferencesOnly ? null : new UsageInfo(usage, startOffset, endOffset, true);
      if (elementTextRange != null
          && usage.getContainingFile() == element.getContainingFile()
          && elementTextRange.contains(startOffset)
          && elementTextRange.contains(endOffset)) {
        return null;
      }

      PsiReference someReference = usage.findReferenceAt(startOffset);
      if (someReference != null) {
        PsiElement refElement = someReference.getElement();
        for (PsiReference ref : PsiReferenceService.getService()
          .getReferences(refElement, new PsiReferenceService.Hints(element, null))) {
          if (element.getManager().areElementsEquivalent(ref.resolve(), element)) {
            TextRange range = ref.getRangeInElement()
              .shiftRight(refElement.getTextRange().getStartOffset() - usage.getTextRange().getStartOffset());
            return new UsageInfo(usage, range.getStartOffset(), range.getEndOffset(), true);
          }
        }
      }

      return equivalentReferencesOnly ? null : new UsageInfo(usage, startOffset, endOffset, true);
    };
    for (String s : stringToSearch) {
      if (!processTextOccurrences(element, s, searchScope, factory, processor)) return false;
    }
    return true;
  }

  /**
   * @param processor must be thread-safe
   */
  public static boolean processTextOccurrences(@NotNull PsiElement element,
                                               @NotNull String stringToSearch,
                                               @NotNull GlobalSearchScope searchScope,
                                               @NotNull UsageInfoFactory factory,
                                               @NotNull Processor<? super UsageInfo> processor) {
    Project project = element.getProject();
    var psiSearchHelper = PsiSearchHelper.getInstance(project);
    try {
      return psiSearchHelper.processUsagesInNonJavaFiles(element, stringToSearch, (psiFile, startOffset, endOffset) -> {
        try {
          UsageInfo usageInfo = ReadAction.nonBlocking(() -> {
              if (!psiFile.isValid()) return null;

              return factory.createUsageInfo(psiFile, startOffset, endOffset);
            })
            .expireWith(project)
            .executeSynchronously();

          return usageInfo == null || processor.process(usageInfo);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.error(e);
          return true;
        }
      }, searchScope);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return true;
    }
  }

  @ApiStatus.Internal
  public static boolean isSearchForTextOccurrencesAvailable(@NotNull FindUsagesHandlerBase handler,
                                                            @NotNull PsiElement psiElement,
                                                            boolean isSingleFile) {
    return handler.isSearchForTextOccurrencesAvailable(psiElement, isSingleFile);
  }
}

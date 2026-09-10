// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
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

  public static boolean processUsagesInText(@NotNull PsiElement psiElement,
                                            @NotNull Collection<String> stringToSearch,
                                            boolean equivalentReferencesOnly,
                                            @NotNull GlobalSearchScope searchScope,
                                            @NotNull Processor<? super UsageInfo> processor) {
    TextRange elementTextRange = ReadAction.computeBlocking(
      () -> {
        if (!psiElement.isValid()) {
          return null;
        }
        VirtualFile virtualFile = PsiUtilCore.getVirtualFile(psiElement);
        if (virtualFile == null || BinaryFileStubBuilders.INSTANCE.forFileType(virtualFile.getFileType()) != null) {
          return null;
        }
        return psiElement.getTextRange();
      });
    UsageInfoFactory factory = (usage, startOffset, endOffset) -> {
      if (!psiElement.isValid()) {
        return equivalentReferencesOnly ? null : new UsageInfo(usage, startOffset, endOffset, true);
      }
      if (elementTextRange != null
          && usage.getContainingFile() == psiElement.getContainingFile()
          && elementTextRange.contains(startOffset)
          && elementTextRange.contains(endOffset)) {
        return null;
      }

      PsiReference someReference = usage.findReferenceAt(startOffset);
      if (someReference != null) {
        PsiElement refElement = someReference.getElement();
        for (PsiReference ref : PsiReferenceService.getService()
          .getReferences(refElement, new PsiReferenceService.Hints(psiElement, null))) {
          if (psiElement.getManager().areElementsEquivalent(ref.resolve(), psiElement)) {
            TextRange range = ref.getRangeInElement()
              .shiftRight(refElement.getTextRange().getStartOffset() - usage.getTextRange().getStartOffset());
            return new UsageInfo(usage, range.getStartOffset(), range.getEndOffset(), true);
          }
        }
      }

      return equivalentReferencesOnly ? null : new UsageInfo(usage, startOffset, endOffset, true);
    };
    for (String s : stringToSearch) {
      if (!processTextOccurrences(psiElement, s, searchScope, factory, processor)) return false;
    }
    return true;
  }

  /**
   * @param processor must be thread-safe
   */
  public static boolean processTextOccurrences(@NotNull PsiElement psiElement,
                                               @NotNull String stringToSearch,
                                               @NotNull GlobalSearchScope searchScope,
                                               @NotNull UsageInfoFactory factory,
                                               @NotNull Processor<? super UsageInfo> processor) {
    Project project = ReadAction.computeBlocking(() -> psiElement.getProject());
    var psiSearchHelper = PsiSearchHelper.getInstance(project);
    try {
      return psiSearchHelper.processUsagesInNonJavaFiles(psiElement, stringToSearch, (psiFile, startOffset, endOffset) -> {
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

  /**
   * Returns {@code true} when Find Usages must skip {@code reference}.
   * A {@link TextOccurrenceReference} is skipped when {@link FindUsagesOptions#isSearchForTextOccurrences} is {@code false}.
   */
  @ApiStatus.Internal
  public static boolean isHiddenTextOccurrence(@NotNull PsiReference reference, @NotNull FindUsagesOptions options) {
    return !options.isSearchForTextOccurrences && reference instanceof TextOccurrenceReference;
  }

  /**
   * Returns {@code true} when Find Usages must skip {@code usageInfo}.
   * The usage is skipped when {@link FindUsagesOptions#isSearchForTextOccurrences} is {@code false}
   * and each reference at the position of the usage is a {@link TextOccurrenceReference}.
   * <p>
   * A usage keeps the class of the reference that made it only when the handler builds the usage from the reference.
   * A handler can also build the usage from the element and the range, and then the class is absent.
   * For that reason the check reads the references of the element again.
   */
  @ApiStatus.Internal
  public static boolean isHiddenTextOccurrence(@NotNull UsageInfo usageInfo, @NotNull FindUsagesOptions options) {
    if (options.isSearchForTextOccurrences) return false;
    Class<? extends PsiReference> referenceClass = usageInfo.getReferenceClass();
    if (referenceClass != null && !TextOccurrenceReference.class.isAssignableFrom(referenceClass)) return false;
    return ReadAction.computeBlocking(() -> hasTextOccurrenceReferencesOnly(usageInfo));
  }

  private static boolean hasTextOccurrenceReferencesOnly(@NotNull UsageInfo usageInfo) {
    PsiElement element = usageInfo.getElement();
    if (element == null) return false;
    ProperTextRange usageRange = usageInfo.getRangeInElement();
    if (usageRange == null) return false;
    boolean found = false;
    for (PsiReference reference : element.getReferences()) {
      if (!reference.getRangeInElement().intersectsStrict(usageRange)) continue;
      if (!(reference instanceof TextOccurrenceReference)) return false;
      found = true;
    }
    return found;
  }
}

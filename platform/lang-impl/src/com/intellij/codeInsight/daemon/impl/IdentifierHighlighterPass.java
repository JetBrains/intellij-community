// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Deprecated(since = "for backward compatibility only", forRemoval = true)
public final class IdentifierHighlighterPass {
  private static final Logger LOG = Logger.getInstance(IdentifierHighlighterPass.class);

  IdentifierHighlighterPass() {
  }

  /**
   * Returns read and write usages of psi element inside a single element
   *
   * @param target target psi element
   * @param psiElement psi element to search in
   */
  @Deprecated(since = "for backward compatibility only", forRemoval = true)
  public static void getHighlightUsages(@NotNull PsiElement target,
                                        @NotNull PsiElement psiElement,
                                        boolean withDeclarations,
                                        @NotNull Collection<? super TextRange> readRanges,
                                        @NotNull Collection<? super TextRange> writeRanges) {
    getUsages(target, psiElement, withDeclarations, true, readRanges, writeRanges);
  }

  /**
   * Returns usages of psi element inside a single element
   * @param target target psi element
   * @param psiElement psi element to search in
   */
  @Deprecated(since = "for backward compatibility only", forRemoval = true)
  public static @NotNull Collection<TextRange> getUsages(@NotNull PsiElement target, PsiElement psiElement, boolean withDeclarations) {
    List<TextRange> ranges = new ArrayList<>();
    getUsages(target, psiElement, withDeclarations, false, ranges, ranges);
    return ranges;
  }

  private static void getUsages(@NotNull PsiElement target,
                                @NotNull PsiElement scopeElement,
                                boolean withDeclarations,
                                boolean detectAccess,
                                @NotNull Collection<? super TextRange> readRanges,
                                @NotNull Collection<? super TextRange> writeRanges) {
    ReadWriteAccessDetector detector = detectAccess ? ReadWriteAccessDetector.findDetector(target) : null;
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(target.getProject())).getFindUsagesManager();
    FindUsagesHandler findUsagesHandler = findUsagesManager.getFindUsagesHandler(target, true);
    LocalSearchScope scope = new LocalSearchScope(scopeElement);
    Collection<PsiReference> refs = findUsagesHandler == null
                                    ? ReferencesSearch.search(target, scope).findAll()
                                    : findUsagesHandler.findReferencesToHighlight(target, scope);
    for (PsiReference psiReference : refs) {
      if (psiReference == null) {
        LOG.error("Null reference returned, findUsagesHandler=" + findUsagesHandler + "; target=" + target + " of " + target.getClass());
        continue;
      }
      Collection<? super TextRange> destination;
      if (detector == null || detector.getReferenceAccess(target, psiReference) == ReadWriteAccessDetector.Access.Read) {
        destination = readRanges;
      }
      else {
        destination = writeRanges;
      }
      HighlightUsagesHandler.collectHighlightRanges(psiReference, destination);
    }

    if (withDeclarations) {
      TextRange declRange = HighlightUsagesHandler.getNameIdentifierRange(scopeElement.getContainingFile(), target);
      if (declRange != null) {
        if (detector != null && detector.isDeclarationWriteAccess(target)) {
          writeRanges.add(declRange);
        }
        else {
          readRanges.add(declRange);
        }
      }
    }
  }

  @Deprecated
  @ApiStatus.Internal
  public static void clearMyHighlights(@NotNull Document document, @NotNull Project project) {
    IdentifierHighlighterUpdater.Companion.clearMyHighlights(document, project);
  }
}

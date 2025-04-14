// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Author: msk
 */
public final class ThrowSearchUtil {
  private static final Logger LOG = Logger.getInstance(ThrowSearchUtil.class);

  private ThrowSearchUtil() {
  }

  public static class Root {
    private final @NotNull PsiElement myElement;
    private final @NotNull PsiType myType;
    private final boolean isExact;

    public Root(@NotNull PsiElement root, @NotNull PsiType type, final boolean exact) {
      myElement = root;
      myType = type;
      isExact = exact;
    }

    @Override
    public String toString() {
      return PsiFormatUtil.formatType(myType, PsiFormatUtilBase.SHOW_FQ_CLASS_NAMES, PsiSubstitutor.EMPTY);
    }
  }

  /**
   * @return true, if we should continue processing
   */
  private static boolean processExn(@NotNull PsiParameter aCatch, @NotNull Processor<? super UsageInfo> processor, @NotNull Root root) {
    final PsiType type = aCatch.getType();
    if (type.isAssignableFrom(root.myType)) {
      processor.process(new UsageInfo(aCatch));
      return false;
    }
    if (!root.isExact && root.myType.isAssignableFrom(type)) {
      processor.process(new UsageInfo(aCatch));
      return true;
    }
    return true;
  }

  private static boolean scanCatches(@NotNull PsiElement elem,
                                     @NotNull Processor<? super UsageInfo> processor,
                                     @NotNull Root root,
                                     @NotNull FindUsagesOptions options,
                                     @NotNull Set<? super PsiMethod> processed) {
    while (elem != null) {
      final PsiElement parent = elem.getParent();
      if (elem instanceof PsiMethod) {
        final PsiMethod deepestSuperMethod = ((PsiMethod)elem).findDeepestSuperMethod();
        final PsiMethod method = deepestSuperMethod != null ? deepestSuperMethod : (PsiMethod)elem;
        if (!processed.contains(method)) {
          processed.add(method);
          final PsiReference[] refs = MethodReferencesSearch.search(method, options.searchScope, true).toArray(PsiReference.EMPTY_ARRAY);
          for (int i = 0; i != refs.length; ++i) {
            if (!scanCatches(refs[i].getElement(), processor, root, options, processed)) return false;
          }
        }
        return true;
      }
      if (elem instanceof PsiTryStatement aTry) {
        final PsiParameter[] catches = aTry.getCatchBlockParameters();
        for (int i = 0; i != catches.length; ++i) {
          if (!processExn(catches[i], processor, root)) {
            return false;
          }
        }
      }
      else if (parent instanceof PsiTryStatement tryStmt) {
        if (elem != tryStmt.getTryBlock()) {
          elem = parent.getParent();
          continue;
        }
      }
      elem = parent;
    }
    return true;
  }

  public static boolean addThrowUsages(@NotNull Processor<? super UsageInfo> processor, @NotNull Root root, @NotNull FindUsagesOptions options) {
    Set<PsiMethod> processed = new HashSet<>();
    return scanCatches(root.myElement, processor, root, options, processed);
  }

  /**
   * @return is type of exn exactly known
   */

  private static boolean isExactExnType(final PsiExpression exn) {
    return exn instanceof PsiNewExpression;
  }

  public static Root @Nullable [] getSearchRoots(final PsiElement element) {
    if (element instanceof PsiThrowStatement aThrow) {
      final PsiExpression exn = aThrow.getException();
      PsiType exType = exn == null ? null : exn.getType();
      if (exType == null) return null;

      return new Root[]{new Root(aThrow.getParent(), exType, isExactExnType(exn))};
    }
    if (element instanceof PsiKeyword kwd && JavaKeywords.THROWS.equals(kwd.getText())) {
      final PsiElement parent = kwd.getParent();
      if (parent != null && parent.getParent() instanceof PsiMethod method) {
        final PsiReferenceList throwsList = method.getThrowsList();
        final PsiClassType[] exns = throwsList.getReferencedTypes();
        final Root[] roots = new Root[exns.length];
        for (int i = 0; i != roots.length; ++i) {
          final PsiClassType exn = exns[i];
          roots[i] = new Root(method, exn, false); // TODO: test for final
        }
        return roots;
      }
    }
    return null;
  }

  public static boolean isSearchable(final PsiElement element) {
    return getSearchRoots(element) != null;
  }

  public static @NlsSafe String getSearchableTypeName(final PsiElement e) {
    if (e instanceof PsiThrowStatement aThrow) {
      final PsiType type = aThrow.getException().getType();
      return PsiFormatUtil.formatType(type, PsiFormatUtilBase.SHOW_FQ_CLASS_NAMES, PsiSubstitutor.EMPTY);
    }
    if (e instanceof PsiKeyword && JavaKeywords.THROWS.equals(e.getText())) {
      return e.getParent().getText();
    }
    LOG.error("invalid searchable element");
    return e.getText();
  }
}

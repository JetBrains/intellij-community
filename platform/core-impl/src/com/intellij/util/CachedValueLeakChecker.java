// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ref.DebugReflectionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

final class CachedValueLeakChecker {
  private static final Logger LOG = Logger.getInstance(CachedValueLeakChecker.class);
  private static final boolean DO_CHECKS = ApplicationManager.getApplication().isUnitTestMode();
  private static final Set<String> ourCheckedKeys = ContainerUtil.newConcurrentSet();

  static void checkProviderDoesNotLeakPSI(@NotNull CachedValueProvider<?> provider, @NotNull Key<?> key, @NotNull UserDataHolder userDataHolder) {
    if (!DO_CHECKS || ApplicationManagerEx.isInStressTest()) {
      return;
    }
    if (!ourCheckedKeys.add(key.toString())) {
      return; // store strings because keys are created afresh in each (test) project
    }

    findReferencedPsi(provider, key, userDataHolder);
  }

  private static synchronized void findReferencedPsi(@NotNull Object root, @NotNull Key<?> key, @NotNull UserDataHolder toIgnore) {
    Predicate<Object> shouldExamineValue = value -> {
      if (value == toIgnore) return false;
      if (value instanceof ASTNode) {
        value = ((ASTNode)value).getPsi();
        if (value == toIgnore) return false;
      }
      if (value instanceof Project || value instanceof Module || value instanceof Application) return false;
      if (value instanceof PsiElement &&
          toIgnore instanceof PsiElement &&
          ((PsiElement)toIgnore).getContainingFile() != null &&
          isAncestor((PsiElement)value, (PsiElement)toIgnore)) {
        // allow to capture PSI parents, assuming that they stay valid at least as long as the element itself
        return false;
      }
      return true;
    };
    Map<Object, @NonNls String> roots = Collections.singletonMap(root, "CachedValueProvider " + key);
    DebugReflectionUtil.walkObjects(5, roots, PsiElement.class, shouldExamineValue, (__, backLink) -> {
      LOG.error("Provider '" + root + "' is retaining PSI, causing memory leaks and possible invalid element access.\n" + backLink);
      return false;
    });
  }

  private static boolean isAncestor(@NotNull PsiElement ancestor, @NotNull PsiElement element) {
    // To avoid AST loading, on stub-based trees let's use PsiTreeUtil.isContextAncestor(). It's possible because
    // StubBasedPsiElementBase.getContext() uses stub tree node's parents for PsiElement.getContext(). It skips elements which are
    // not bound to stub tree. "PsiElement.getStub() != null" means that AST is not loaded, so there are no valid AST-based PSI elements
    // in current file.
    // For other cases it's safer to use PsiTreeUtil.isAncestor(), because PsiElement.getContext() can skip some parents.
    if (ancestor instanceof StubBasedPsiElementBase && ((StubBasedPsiElementBase<?>)ancestor).getStub() != null ||
        element instanceof StubBasedPsiElementBase && ((StubBasedPsiElementBase<?>)element).getStub() != null) {
      return ancestor.getContainingFile() == element.getContainingFile() && PsiTreeUtil.isContextAncestor(ancestor, element, true);
    }

    return PsiTreeUtil.isAncestor(ancestor, element, true);
  }
}

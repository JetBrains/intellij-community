// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
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
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * @author peter
 */
final class CachedValueLeakChecker {
  private static final Logger LOG = Logger.getInstance(CachedValueLeakChecker.class);
  private static final boolean DO_CHECKS = ApplicationManager.getApplication().isUnitTestMode();
  private static final Set<String> ourCheckedKeys = ContainerUtil.newConcurrentSet();

  static void checkProvider(@NotNull CachedValueProvider<?> provider, @NotNull Key<?> key, @NotNull UserDataHolder userDataHolder) {
    if (!DO_CHECKS || ApplicationInfoImpl.isInStressTest()) return;
    if (!ourCheckedKeys.add(key.toString())) return; // store strings because keys are created afresh in each (test) project

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
          PsiTreeUtil.isAncestor((PsiElement)value, (PsiElement)toIgnore, true)) {
        // allow to capture PSI parents, assuming that they stay valid at least as long as the element itself
        return false;
      }
      return true;
    };
    Map<Object, String> roots = Collections.singletonMap(root, "CachedValueProvider "+key);
    DebugReflectionUtil.walkObjects(5, roots, PsiElement.class, shouldExamineValue, (value, backLink) -> {
      if (value instanceof PsiElement) {
        LOG.error(
          "Incorrect CachedValue use. Provider references PSI, causing memory leaks and possible invalid element access, provider=" +
          root + "\n" + backLink);
        return false;
      }
      return true;
    });
  }
}

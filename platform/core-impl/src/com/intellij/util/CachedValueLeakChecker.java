/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
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

/**
 * @author peter
 */
class CachedValueLeakChecker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.CachedValueChecker");
  private static final boolean DO_CHECKS = ApplicationManager.getApplication().isUnitTestMode();
  private static final Set<String> ourCheckedKeys = ContainerUtil.newConcurrentSet();

  static void checkProvider(@NotNull final CachedValueProvider provider,
                            @NotNull final Key key,
                            @NotNull final UserDataHolder userDataHolder) {
    if (!DO_CHECKS || ApplicationInfoImpl.isInStressTest()) return;
    if (!ourCheckedKeys.add(key.toString())) return; // store strings because keys are created afresh in each (test) project

    if (!SystemInfo.IS_AT_LEAST_JAVA9) {
      findReferencedPsi(provider, key, userDataHolder, 5);
    }
  }

  private static synchronized void findReferencedPsi(@NotNull final Object root,
                                                     @NotNull Key key,
                                                     @NotNull final UserDataHolder toIgnore,
                                                     int depth) {
    Condition<Object> shouldExamineValue = value -> {
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
    DebugReflectionUtil.walkObjects(depth, roots, PsiElement.class, shouldExamineValue, (value, backLink) -> {
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

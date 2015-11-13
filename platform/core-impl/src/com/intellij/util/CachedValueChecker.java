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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.DebugReflectionUtil.BackLink;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Set;

/**
 * @author peter
 */
class CachedValueChecker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.CachedValueChecker");
  private static final boolean DO_CHECKS = ApplicationManager.getApplication().isUnitTestMode();
  private static final Set<String> ourCheckedKeys = ContainerUtil.newConcurrentSet();

  static void checkProvider(@NotNull final CachedValueProvider provider,
                            @NotNull final Key key,
                            @NotNull final UserDataHolder userDataHolder) {
    if (!DO_CHECKS) return;
    if (!ourCheckedKeys.add(key.toString())) return; // store strings because keys are created afresh in each (test) project

    Set<Object> visited = ContainerUtil.newIdentityTroveSet();
    BackLink path = findReferencedPsi(provider, userDataHolder, 6, visited, null);
    if (path != null) {
      LOG.error("Incorrect CachedValue use. Provider references PSI, causing memory leaks and possible invalid element access, provider=" +
                provider + "\n" + path);
    }
  }

  @Nullable
  private static synchronized BackLink findReferencedPsi(@NotNull Object o,
                                                         @Nullable final UserDataHolder toIgnore,
                                                         final int depth,
                                                         @NotNull final Set<Object> visited,
                                                         @Nullable final BackLink backLink) {
    if (depth == 0 || o == toIgnore || !visited.add(o)) return null;
    if (o instanceof Project || o instanceof Module || o instanceof Application) return null;
    if (o instanceof PsiElement) {
      if (toIgnore instanceof PsiElement &&
          ((PsiElement)toIgnore).getContainingFile() != null &&
          PsiTreeUtil.isAncestor((PsiElement)o, (PsiElement)toIgnore, true)) {
        // allow to capture PSI parents, assuming that they stay valid at least as long as the element itself
        return null;
      }
      return backLink;
    }

    final Ref<BackLink> result = Ref.create();
    DebugReflectionUtil.processStronglyReferencedValues(o, new PairProcessor<Object, Field>() {
      @Override
      public boolean process(Object next, Field field) {
        result.set(findReferencedPsi(next, toIgnore, depth - 1, visited, new BackLink(next, field, backLink)));
        return result.isNull();
      }
    });
    return result.get();
  }
}

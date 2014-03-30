/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.quickfix;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiReference;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

public abstract class UnresolvedReferenceQuickFixProvider<T extends PsiReference> {
  public static <T extends PsiReference> void registerReferenceFixes(@NotNull T ref, @NotNull QuickFixActionRegistrar registrar) {
    final boolean dumb = DumbService.getInstance(ref.getElement().getProject()).isDumb();
    UnresolvedReferenceQuickFixProvider[] fixProviders = Extensions.getExtensions(EXTENSION_NAME);
    Class<? extends PsiReference> referenceClass = ref.getClass();
    for (UnresolvedReferenceQuickFixProvider each : fixProviders) {
      if (dumb && !DumbService.isDumbAware(each)) {
        continue;
      }
      if (ReflectionUtil.isAssignable(each.getReferenceClass(), referenceClass)) {
        each.registerFixes(ref, registrar);
      }
    }
  }

  private static final ExtensionPointName<UnresolvedReferenceQuickFixProvider> EXTENSION_NAME = ExtensionPointName.create("com.intellij.codeInsight.unresolvedReferenceQuickFixProvider");

  public abstract void registerFixes(@NotNull T ref, @NotNull QuickFixActionRegistrar registrar);

  @NotNull
  public abstract Class<T> getReferenceClass();
}

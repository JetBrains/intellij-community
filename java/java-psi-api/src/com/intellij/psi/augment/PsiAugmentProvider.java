/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi.augment;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


public abstract class PsiAugmentProvider {
  public static final ExtensionPointName<PsiAugmentProvider> EP_NAME = ExtensionPointName.create("com.intellij.lang.psiAugmentProvider");

  @NotNull
  public abstract <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element, @NotNull Class<Psi> type);

  @NotNull
  public static <Psi extends PsiElement> List<Psi> collectAugments(@NotNull final PsiElement element, @NotNull final Class<Psi> type) {
    final List<Psi> augments = new ArrayList<Psi>();
    for (PsiAugmentProvider provider : Extensions.getExtensions(EP_NAME)) {
      final List<Psi> list = provider.getAugments(element, type);
      augments.addAll(list);
    }

    return augments;
  }
}

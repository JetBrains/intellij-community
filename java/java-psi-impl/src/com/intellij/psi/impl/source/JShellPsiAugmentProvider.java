/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.impl.source;

import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 * Date: 28-Jun-17
 */
public class JShellPsiAugmentProvider extends PsiAugmentProvider{
  private static final Set<String> JSHELL_FIELD_MODIFIERS = Collections.unmodifiableSet(ContainerUtil.newHashSet(PsiModifier.PUBLIC, PsiModifier.STATIC));
  @NotNull
  @Override
  protected Set<String> transformModifiers(@NotNull PsiModifierList modifierList, @NotNull Set<String> modifiers) {
    // enforce permanent field modifiers for all variables declared at top-level
    return isInsideJShellField(modifierList)? JSHELL_FIELD_MODIFIERS : modifiers;
  }

  private static boolean isInsideJShellField(PsiElement element) {
    final PsiElement parent = element.getParent();
    return parent instanceof PsiField && parent.getParent() instanceof PsiJShellRootClass;
  }
}
